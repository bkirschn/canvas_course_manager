package edu.umich.ctools.sectionsUtilityTool;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Properties;
import java.util.ResourceBundle;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.json.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class FriendServlet extends HttpServlet {

	private static Log M_log = LogFactory.getLog(FriendServlet.class);
	private static final long serialVersionUID = 7284813350014385613L;

	private static final String INVITE_EMAIL = "inviteEmail";
	private static final String INSTRUCTOR_EMAIL = "instructorEmail";
	private static final String INSTRUCTOR_NAME = "instructorName";
	private static final String PARAMETER_ID = "id";
	private static final String PARAMETER_INSTRUCTOR_EMAIL = "inst_email";
	private static final String PARAMETER_INSTRUCTOR_FIRST_NAME = "inst_first_name";
	private static final String PARAMETER_INSTRUCTOR_LAST_NAME = "inst_last_name";
	ResourceBundle props = ResourceBundle.getBundle("sectiontool");

	public void init() throws ServletException {
		M_log.debug(" Servlet init(): Called");
	}

	//This servlet only processes POST calls so the others have not been implemented
	protected void doPost(HttpServletRequest request,HttpServletResponse response){
		M_log.debug("doPOST: Called");
		try {
			friendRestApiCall(request, response);
		}catch(Exception e) {
			M_log.error("POST request has some exceptions",e);
		}
	}

	/*
	 * This method is handling all the different Api request like PUT, POST etc to Friend Accounts. 
	 */
	private void friendRestApiCall(HttpServletRequest request,
			HttpServletResponse response) throws IOException {
		request.setCharacterEncoding("UTF-8");
		M_log.debug("friendRestApiCall(): called");
		Friend myFriend = new Friend();
		PrintWriter out = response.getWriter();
		response.setContentType("application/json");
		Properties appExtSecureProperties = Friend.appExtSecurePropertiesFile; 
		if(appExtSecureProperties==null) {
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			out = response.getWriter();
			out.print(props.getString("property.file.load.error"));
			out.flush();
			M_log.error("Failed to load system properties(sectionsToolProps.properties) for SectionsTool");
			return;
		}
		friendApiConnectionLogic(request,response, myFriend);
	}	

	/*
	 * This function has logic that execute client(i.e., browser) request and get results from the Friend Accounts  
	 */
	private void friendApiConnectionLogic(HttpServletRequest request, HttpServletResponse response, Friend myFriend)
			throws IOException {
		String pathInfo = request.getPathInfo();
		PrintWriter out = response.getWriter();
		JsonObject json;
		switch (pathInfo){
		case "/friendCreate":
			String friendInviteEmail = request.getParameter(PARAMETER_ID);
			String friendInstructorEmail = request.getParameter(PARAMETER_INSTRUCTOR_EMAIL);
			String friendInstructorFirstName = request.getParameter(PARAMETER_INSTRUCTOR_FIRST_NAME);
			String friendInstructorLastName = request.getParameter(PARAMETER_INSTRUCTOR_LAST_NAME);
			if(friendInviteEmail == null || 
					friendInstructorEmail == null ||
					friendInstructorFirstName == null || 
					friendInstructorLastName == null){
				response.setStatus(400);
				json = Json.createObjectBuilder()
						.add("message", "request error: required parameters for request not specified")
						.build();
			}
			else{
				json = doFriendLogic(myFriend, 
						friendInviteEmail, 
						friendInstructorEmail, 
						friendInstructorFirstName, 
						friendInstructorLastName);
			}
			break;
		default:
			response.setStatus(400);
			json = Json.createObjectBuilder()
					.add("message", "request error")
					.build();
			break;
		}
		out.print(json.toString());
		out.flush();
	}
	
	private JsonObject buildResponseObject(String message, String detailedMessage){
		return Json.createObjectBuilder()
				.add("message", message)
				.add("detailedMessage", detailedMessage)
				.add("FriendURL", Friend.friendUrl)
				.build();
	}

	private JsonObject doFriendLogic(Friend myFriend, 
			String inviteEmail, 
			String instructorEmail, 
			String instructorFirstName, 
			String instructorLastName){
		JsonObject json;
		String instructorName = instructorFirstName + " " + instructorLastName;
		
		HashMap<String, String> friendValues = new HashMap<String,String>();
		
		friendValues.put("inviteEmail", inviteEmail);
		friendValues.put("instructorEmail", instructorEmail);
		friendValues.put("instructorName", instructorName);
		
		//Step 1: determine if friend exists
		CheckAccountExistsResponse friendExists = myFriend.checkAccountExist(inviteEmail);
		json = doCheckFriendAccount(friendExists, friendValues, myFriend);
		myFriend.destroy();
		return json;
	}		
	
	private JsonObject doCheckFriendAccount(CheckAccountExistsResponse friendExists, HashMap<String, String> friendValues, Friend myFriend){
		JsonObject json;
		String detailedMessage;

		switch(friendExists){
		case INVALID_EMAIL:
			//If friend email is invalid, send message for invalid email in json response.
			M_log.warn(" friendCreate: invalid email address " + friendValues.get(INVITE_EMAIL));
			detailedMessage = "Account " + friendValues.get(INVITE_EMAIL) + " is not a valid email address";
			json = buildResponseObject("false", detailedMessage);
			break;
		case FRIEND_ACCOUNT_DOES_NOT_EXIST:
			//If friend has valid email but does not have a friend account, attempt to create one.
			M_log.info(" friendCreate: " + friendValues.get(INVITE_EMAIL) + " does not have an account. One will attempt to be created.");
			CreateAccountResponse friendCreate = myFriend.doSendInvite(friendValues.get(INVITE_EMAIL), friendValues.get(INSTRUCTOR_EMAIL), friendValues.get(INSTRUCTOR_NAME));
			//Step 2: attempt to create friend account
			json = doCreateFriendAccount(friendCreate, friendValues);
			break;	
		case FRIEND_ACCOUNT_ALREADY_EXISTS:
			//If friend already has friend account, send so back in json response
			detailedMessage = "Account " + friendValues.get(INVITE_EMAIL) + " already exist";
			json = buildResponseObject("false", detailedMessage);
			break;
		default:
			//Default is that nothing be done and send back json response.
			M_log.warn(" friendCreate: invalid email address " + friendValues.get(INVITE_EMAIL));
			detailedMessage = "Friend Account NOT created for " + friendValues.get(INVITE_EMAIL);
			json = buildResponseObject("false", detailedMessage);
			break;
		}
		return json;
	}

	private JsonObject doCreateFriendAccount(CreateAccountResponse friendCreate, HashMap<String, String> friendValues){
		JsonObject json;
		String detailedMessage;

		switch(friendCreate){
		case INVITATION_SUCCESSFULLY_SENT:
			//If friend account is successful, send json response back saying so
			M_log.info(" friendCreate: successfully created account for  " + friendValues.get(INVITE_EMAIL));
			detailedMessage = "Friend Account created for " + friendValues.get(INVITE_EMAIL);
			json = buildResponseObject("true", detailedMessage);

			//Step 3: send notification to instructor indicating that a friend account has been created.
			Friend.notifyCurrentUser(friendValues.get(INSTRUCTOR_NAME), friendValues.get(INSTRUCTOR_EMAIL), friendValues.get(INVITE_EMAIL));
			break;
		case INVALID_EMAIL:
			//If attempt to create friend account fails, then send so in response
			M_log.warn(" friendCreate: invalid email address " + friendValues.get(INVITE_EMAIL));
			detailedMessage = "Friend Account NOT created for " + friendValues.get(INVITE_EMAIL);
			json = buildResponseObject("false", detailedMessage);
			break;
		case RUNTIME_PROBLEM:
			//Default is that nothing be done and send back json response.
			M_log.warn(" friendCreate: runtime error when attempting to create account for " + friendValues.get(INVITE_EMAIL));
			detailedMessage = "Friend Account NOT created for " + friendValues.get(INVITE_EMAIL) + " due to runtime error";
			json = buildResponseObject("false", detailedMessage);
			break;
		default:
			//Default is that nothing be done and send back json response.
			M_log.warn(" friendCreate: runtime error when attempting to create account for " + friendValues.get(INVITE_EMAIL));
			detailedMessage = "Friend Account NOT created for " + friendValues.get(INVITE_EMAIL) + " due to runtime error";
			json = buildResponseObject("false", detailedMessage);
			break;
		}		
		return json;
	}

	public void destroy(){
		M_log.debug(" Servlet destroy(): Called");
	}

}