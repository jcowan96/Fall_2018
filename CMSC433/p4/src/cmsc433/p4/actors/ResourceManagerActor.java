package cmsc433.p4.actors;
import java.util.*;

import cmsc433.p4.enums.*;
import cmsc433.p4.messages.*;
import cmsc433.p4.util.*;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;

public class ResourceManagerActor extends UntypedActor {
	
	private ActorRef logger;					// Actor to send logging messages to

	//Resources local to this manager, keyed by name
	private HashMap<String, Resource> localResources = new HashMap<>();

	//All other managers this knows about in the system
	private ArrayList<ActorRef> systemManagers = new ArrayList<>();

	//All local users to this manager
	private ArrayList<ActorRef> usersLocal = new ArrayList<>();

	//Map of remote resource names and the manager that owns them
	private HashMap<String, ActorRef> remoteResources = new HashMap<>();

	//Queue for access requests being blocked
	private Queue<AccessRequestMsg> blockingAccessRequests = new LinkedList<>();

	//Maps resource name to the requests to disable it
	private HashMap<String, List<ManagementRequestMsg>> toDisable = new HashMap<>();

	//Maps name of local resources to users in the system and their access level to that resource
	private HashMap<String, List<UserLevel>> userAccessLevels = new HashMap<>();

	//Map of resource name to pair of message object and integer counter, used to look for remote resources when location is unknown
	private HashMap<String, List<HashMap<Object, Integer>>> unknownRemoteMap = new HashMap<>();
	
	/**
	 * Props structure-generator for this class.
	 * @return  Props structure
	 */
	static Props props (ActorRef logger) {
		return Props.create(ResourceManagerActor.class, logger);
	}
	
	/**
	 * Factory method for creating resource managers
	 * @param logger			Actor to send logging messages to
	 * @param system			Actor system in which manager will execute
	 * @return					Reference to new manager
	 */
	public static ActorRef makeResourceManager (ActorRef logger, ActorSystem system) {
		ActorRef newManager = system.actorOf(props(logger));
		return newManager;
	}
	
	/**
	 * Sends a message to the Logger Actor
	 * @param msg The message to be sent to the logger
	 */
	public void log (LogMsg msg) {
		logger.tell(msg, getSelf());
	}
	
	/**
	 * Constructor
	 * 
	 * @param logger			Actor to send logging messages to
	 */
	private ResourceManagerActor(ActorRef logger) {
		super();
		this.logger = logger;
	}

	//These are private helper methods to handle different types of incoming messages
	//
	//================================================================================

	//Adds list of resources to localResources, enables all the new resources, and logs it
	private void AddInitialLocalResourcesHandler(AddInitialLocalResourcesRequestMsg msg, ActorRef from) {
		ArrayList<Resource> resources = msg.getLocalResources();

		int i = 0;
		while (i < resources.size()) {
			Resource res = resources.get(i);
			String resourceName = res.getName();

			//Initialize data structures for this resource
			localResources.put(res.getName(), res); //Map the name and resource for O(1) resource location
			toDisable.put(resourceName, new LinkedList<>()); //Initialize future pending disable messages for this resource
			userAccessLevels.put(resourceName, new LinkedList<>()); //Initialize future pending access levels for users for this resource

			res.enable(); //Resources are disabled by default, so we must enable them
			log(LogMsg.makeLocalResourceCreatedLogMsg(getSelf(), resourceName)); //Log creation of new local resource
			log(LogMsg.makeResourceStatusChangedLogMsg(getSelf(), res.getName(), res.getStatus())); //Log change of resource status
			i++;
		}

		//Send the reply message
		from.tell(new AddInitialLocalResourcesResponseMsg(msg), getSelf());
	}

	//Adds list of local users from message to localUsers, and replies to the incoming message
	private void AddLocalUsersHandler(AddLocalUsersRequestMsg msg, ActorRef from) {
		ArrayList<ActorRef> localUsers = msg.getLocalUsers();

		//Add received set of local users to the usersLocal list
		int i = 0;
		while (i < localUsers.size()) {
			usersLocal.add(localUsers.get(i));
			i++;
		}

		//Send the reply message
		from.tell(new AddLocalUsersResponseMsg(msg), getSelf());
	}

	//Adds incoming list of managers to known managers in system, and replies to message sender
	private void AddRemoteManagersHandler(AddRemoteManagersRequestMsg msg, ActorRef from) {
		ArrayList<ActorRef> managers = msg.getManagerList();

		//Add the managers into the list of system managers, as long as it is not this specific manager
		int i = 0;
		while (i < managers.size()) {
			if (!managers.get(i).equals(getSelf())) systemManagers.add(managers.get(i));
			i++;
		}

		//Send the reply message
		from.tell(new AddRemoteManagersResponseMsg(msg), getSelf());
	}

	//Handles incoming access requests
	private void AccessRequestHandler(AccessRequestBlocking block, ActorRef from) {
		AccessRequestMsg msg = block.getAccessRequestMsg();
		AccessRequest request = msg.getAccessRequest();
		String resourceName = request.getResourceName();
		if (!block.isBlocking()) //If not a blocking message, log that request has been received
			log(LogMsg.makeAccessRequestReceivedLogMsg(msg.getReplyTo(), getSelf(), request));

		//If the resource is contained locally, process it in place
		if (localResources.containsKey(resourceName)) {
			AccessRequestType requestType = request.getType();
			ResourceStatus resourceStatus = localResources.get(resourceName).getStatus();
			//Check if the resource is going to be disabled, and send the denial message if it is
			if ((toDisable.get(resourceName) != null && !toDisable.get(resourceName).isEmpty()) || resourceStatus == ResourceStatus.DISABLED) {
				from.tell(new AccessRequestDeniedMsg(request, AccessRequestDenialReason.RESOURCE_DISABLED), getSelf());
				log(LogMsg.makeAccessRequestDeniedLogMsg(from, getSelf(), request, AccessRequestDenialReason.RESOURCE_DISABLED));
			}
			else { //Resource not being disabled, check if access can be granted
				List<UserLevel> temp = userAccessLevels.get(resourceName);
				boolean access = true;

				//If there is other user access information for the resource, figure out what other kinds of access users have,
				//and use that info to determine whether or not to grant access to the resource
				if (!temp.isEmpty()) {
					//If another user has exclusive write access, or another user had concurrent read access and the
					//request is for exclusive write, deny the request. Re-entrant locking is supported, so if the user with
					//access is the same one making the request, that will not automatically deny the request
					for (int i = 0; i < temp.size(); i++) {
						AccessType accessType = temp.get(i).getAccessType();
						if (!from.equals(temp.get(i).getUser())) {
							if (accessType == AccessType.CONCURRENT_READ) {
								if (requestType == AccessRequestType.EXCLUSIVE_WRITE_BLOCKING || requestType == AccessRequestType.EXCLUSIVE_WRITE_NONBLOCKING)
									access = false;
							}
							else if (accessType == AccessType.EXCLUSIVE_WRITE) {
								access = false;
							}
						}
						//If user is same as one in userAccessLevels do nothing, allow for re-entrant locking
						//Do nothing
					}

					//If the user can access the resource, reply with an access granted message
					if (access) {
						switch(requestType) {
							case CONCURRENT_READ_BLOCKING:
							case CONCURRENT_READ_NONBLOCKING:
								userAccessLevels.get(resourceName).add(new UserLevel(from, AccessType.CONCURRENT_READ));
								break;
							case EXCLUSIVE_WRITE_BLOCKING:
							case EXCLUSIVE_WRITE_NONBLOCKING:
								userAccessLevels.get(resourceName).add(new UserLevel(from, AccessType.EXCLUSIVE_WRITE));
							default:
								//Do nothing
								break;
						}
						//Reply to sender with access request granted message
						from.tell(new AccessRequestGrantedMsg(request), getSelf());
						log(LogMsg.makeAccessRequestGrantedLogMsg(from, getSelf(), request));
					}
					//Else the user can't access the resource, reply with an access denied message
					else {
						//If request is blocking add it to blocking queue, otherwise send busy message to requester
						switch(requestType) {
							case CONCURRENT_READ_BLOCKING:
							case EXCLUSIVE_WRITE_BLOCKING:
								blockingAccessRequests.add(msg);
								break;
							case CONCURRENT_READ_NONBLOCKING:
							case EXCLUSIVE_WRITE_NONBLOCKING:
								from.tell(new AccessRequestDeniedMsg(request, AccessRequestDenialReason.RESOURCE_BUSY), getSelf());
								log(LogMsg.makeAccessRequestDeniedLogMsg(from, getSelf(), request, AccessRequestDenialReason.RESOURCE_BUSY));
								break;
							default:
								//Do nothing
								break;
						}
					}
				}
				//If no other users have access to the resource, grant the request automatically
				else {
					switch (requestType){
						case CONCURRENT_READ_BLOCKING:
						case CONCURRENT_READ_NONBLOCKING:
							userAccessLevels.get(resourceName).add(new UserLevel(from, AccessType.CONCURRENT_READ));
							from.tell(new AccessRequestGrantedMsg(request), getSelf());
							log(LogMsg.makeAccessRequestGrantedLogMsg(from, getSelf(), request));
							break;
						case EXCLUSIVE_WRITE_BLOCKING:
						case EXCLUSIVE_WRITE_NONBLOCKING:
							userAccessLevels.get(resourceName).add(new UserLevel(from, AccessType.EXCLUSIVE_WRITE));
							from.tell(new AccessRequestGrantedMsg(request), getSelf());
							log(LogMsg.makeAccessRequestGrantedLogMsg(from, getSelf(), request));
							break;
						default:
							//Do nothing
							break;
					}
				}
			}
		}
		else { //If resource does not exist locally, either redirect the request if we know where to go, or find it if we dont
			if (remoteResources.containsKey(resourceName)) //If location is known, forward the message
				forwardRequestMessage(msg, remoteResources.get(resourceName), from);
			else {
				//Otherwise, search the whole system for the resource location
				searchUnknownRemote(msg);
			}

		}
	}

	//Process incoming access release messages, acknowledging that a resource has been released somewhere in the system
	private void AccessReleaseHandler(AccessReleaseMsg msg, ActorRef from) {
		AccessRelease accessRelease = msg.getAccessRelease();
		String resourceName = accessRelease.getResourceName();
		AccessType accessType = accessRelease.getType();
		log(LogMsg.makeAccessReleaseReceivedLogMsg(from, getSelf(), accessRelease)); //Log access release msg received

		//If the resource is local to this manager, process it here
		if (localResources.containsKey(resourceName)) {
			List<UserLevel> temp = userAccessLevels.get(resourceName);

			//Iterate through list of users with access levels on the resource
			for (int i = 0; i < temp.size(); i++) {
				UserLevel accessLevel = temp.get(i);
				if (accessLevel.getUser().equals(from) && accessLevel.getAccessType().equals(accessType)) { //If the user is in the accessLevel list, it can release
					//Remove the user from the access list and log
					temp.remove(accessLevel);
					log(LogMsg.makeAccessReleasedLogMsg(from, getSelf(), accessRelease));
					break;
				}
				//If user does not have any
				else if (i == temp.size()) {
					log(LogMsg.makeAccessReleaseIgnoredLogMsg(from, getSelf(), accessRelease));
				}
			}

			//If there are no users whomst have tried to access the resource, iterate through the toDisable requests
			//and send messages granting their requests
			if (temp.isEmpty()) {
				if ((toDisable.get(resourceName) != null && !toDisable.get(resourceName).isEmpty()) && localResources.get(resourceName).getStatus() == ResourceStatus.ENABLED) {
					localResources.get(resourceName).disable(); //Disable the resource
					log(LogMsg.makeResourceStatusChangedLogMsg(getSelf(), resourceName, localResources.get(resourceName).getStatus()));

					//Iterate through list of pending disable messages for the resource in question, and reply to them
					//with the news that the resource has been disabled
					List<ManagementRequestMsg> messages = toDisable.get(resourceName);
					for (int i = 0; i < messages.size(); i++) {
						ManagementRequest request = messages.get(i).getRequest();
						ActorRef target = messages.get(i).getReplyTo();

						target.tell(new ManagementRequestGrantedMsg(request), getSelf());
						log(LogMsg.makeManagementRequestGrantedLogMsg(target, getSelf(), request));
					}
				}
			}
		}
		else { //If the resource is not local, attempt to find where it exists, and forward the message
			if (remoteResources.containsKey(resourceName))  //If the location is known, forward the message
				forwardRequestMessage(msg, remoteResources.get(resourceName), from);
			else //Otherwise, search the entire system for the resource location
				searchUnknownRemote(msg);
		}
		//After releasing access to resources, check in the blockingAccessRequests for the ability to fulfill any of them
		//now that an element has had its access released
		if (!blockingAccessRequests.isEmpty()) { //If the blockingAccess queue has elements, iterate through them
			Iterator<AccessRequestMsg> iter = blockingAccessRequests.iterator();
			while (iter.hasNext()) {
				AccessRequestMsg message = iter.next();
				AccessRequest request = message.getAccessRequest();
				AccessRequestType requestType = request.getType();

				//Iterate through user access levels to check access level of other users of this resource
				boolean access = true;
				List<UserLevel> lst = userAccessLevels.get(resourceName);
				for (int i = 0; i < lst.size(); i++) {
					AccessType type = lst.get(i).getAccessType();
					ActorRef user = lst.get(i).getUser();

					//If this user is not the sender, check its access level
					if (!msg.getSender().equals(user)) {
						if (type == AccessType.CONCURRENT_READ) { //Another user has concurrent read access, can grant if our request is also to read
							if (requestType == AccessRequestType.EXCLUSIVE_WRITE_BLOCKING || requestType == AccessRequestType.EXCLUSIVE_WRITE_NONBLOCKING)
								access = false;
						} else if (type == AccessType.EXCLUSIVE_WRITE) //Another user has exclusive write access, cant grant request
							access = false;
					}
				}
				//Checked all other users of the resource at this point, made it to end of list
				//If user can now access resource, send access granted message and remove element from blocking queue
				if (access) {
					AccessRequestBlocking block = new AccessRequestBlocking(message, true);
					AccessRequestHandler(block, message.getReplyTo());
					log(LogMsg.makeAccessRequestGrantedLogMsg(message.getReplyTo(), getSelf(), request));
					iter.remove();
				}
			}
			//Finished checking blocking queue at this point
		}
	}

	//Handles incoming ManagementRequestMsg
	private void ManagementRequestHandler(ManagementRequestMsg msg, ActorRef from) {
		ManagementRequest managementRequest = msg.getRequest();
		String resourceName = managementRequest.getResourceName();
		log(LogMsg.makeManagementRequestReceivedLogMsg(from, getSelf(), managementRequest));

		//If resource in question is managed locally, process it in place
		if (localResources.containsKey(resourceName)) {
			Resource res = localResources.get(resourceName);
			ResourceStatus status = res.getStatus();
			//If the request is to ENABLE, this is the easy case
			if (managementRequest.getType() == ManagementRequestType.ENABLE) {
				if (status == ResourceStatus.ENABLED) { //If the resource is already enabled, just log
					log(LogMsg.makeResourceStatusChangedLogMsg(getSelf(), resourceName, ResourceStatus.ENABLED));
				}
				else if (status == ResourceStatus.DISABLED) { //If disabled, enable it, remove from queue of messages to be disabled, and log
					res.enable();
					log(LogMsg.makeResourceStatusChangedLogMsg(getSelf(), resourceName, ResourceStatus.ENABLED));
					toDisable.remove(resourceName);
				}
				//Finally, reply with a request granted message to whoever made the request, and log this message was sent
				from.tell(new ManagementRequestGrantedMsg(managementRequest), getSelf());
				log(LogMsg.makeManagementRequestGrantedLogMsg(from, getSelf(), managementRequest));
			}
			//If the request is to DISABLE, this is the more complicated case
			else if (managementRequest.getType() == ManagementRequestType.DISABLE) {
				//If resource is already disabled, just reply with the request granted to the requester: no work to do
				if (status == ResourceStatus.DISABLED) {
					from.tell(new ManagementRequestGrantedMsg(managementRequest), getSelf());
					log(LogMsg.makeManagementRequestGrantedLogMsg(from, getSelf(), managementRequest));
				}
				//If the resource is already enabled, it's trickier
				else if (status == ResourceStatus.ENABLED) {
					List<UserLevel> temp = userAccessLevels.get(resourceName);
					//Check to see if this user holds an access level for this resource
					for (int i = 0; i < temp.size(); i++) {
						if (from.equals(temp.get(i).getUser())) { //If user already has access, deny request and return method
							from.tell(new ManagementRequestDeniedMsg(managementRequest, ManagementRequestDenialReason.ACCESS_HELD_BY_USER), getSelf());
							log(LogMsg.makeManagementRequestDeniedLogMsg(from, getSelf(), managementRequest, ManagementRequestDenialReason.ACCESS_HELD_BY_USER));
							return;
						}
					}
					//If user does not already have access, disable the resource
					if (temp.isEmpty()) { //If no previous disable requests made, disable the resource and respond with request granted
						localResources.get(resourceName).disable();
						log(LogMsg.makeResourceStatusChangedLogMsg(getSelf(), resourceName, ResourceStatus.DISABLED));
						from.tell(new ManagementRequestGrantedMsg(managementRequest), getSelf());
						log(LogMsg.makeManagementRequestGrantedLogMsg(from, getSelf(), managementRequest));
					}
					toDisable.get(resourceName).add(msg); //Add request to disable queue

					//Deny access to all messages queued for the resource
					Iterator<AccessRequestMsg> accessIter = blockingAccessRequests.iterator();
					while (accessIter.hasNext()) {
						AccessRequestMsg mess = accessIter.next();
						if (resourceName.equals(mess.getAccessRequest().getResourceName())) {
							accessIter.remove();
							mess.getReplyTo().tell(new AccessRequestDeniedMsg(mess.getAccessRequest(), AccessRequestDenialReason.RESOURCE_DISABLED), getSelf());
							log(LogMsg.makeAccessRequestDeniedLogMsg(mess.getReplyTo(), getSelf(), mess.getAccessRequest(), AccessRequestDenialReason.RESOURCE_DISABLED));
						}
					}
				}
			}
		}
		//If resource is not managed locally, either find it from a known remote source, or search the system for it
		else {
			if (remoteResources.containsKey(resourceName)) //If the resource is with a known manager, reroute the request there
				forwardRequestMessage(msg, remoteResources.get(resourceName), from);
			else //Otherwise, search the system for the resource location
				searchUnknownRemote(msg);
		}
	}

	//Checks to see if this resource manager has the resource in question, and sends a response with the result
	//to whatever resource manager sent the request
	private void WhoHasResourceRequestHandler(WhoHasResourceRequestMsg msg, ActorRef from) {
		from.tell(new WhoHasResourceResponseMsg(msg.getResourceName(), localResources.containsKey(msg.getResourceName()), getSelf()), getSelf());
	}

	//Parses result from WhoHasResourceResponse. If result is true that manager has the resource locally, so forward
	//all pending messages to that manager. If result is false that manager does not have the resource. If the counter
	//mapped to the resource hits 0 no managers in the system have the resource, so it does not exist. In that case
	//iterate through all pending messages for that resource and deny their requests
	private void WhoHasResourceResponseHandler(WhoHasResourceResponseMsg msg, ActorRef from) {
		String resourceName = msg.getResourceName();

		if (!msg.getResult()) { //Respondent does not have resource available locally
			if (unknownRemoteMap.get(resourceName) != null && unknownRemoteMap.get(resourceName).get(0) != null) {
				HashMap<Object, Integer> unknown = unknownRemoteMap.get(resourceName).get(0);
				Object mess = unknownRemoteMap.get(resourceName).get(0).keySet().toArray()[0]; //Should only be 1 key per HashMap, just a K/V pair

				unknown.put(mess, unknown.get(mess) - 1);
				if (unknown.get(mess) == 0) { //If count is 0 no manager on the system has the resource, it does not exist. Reply with denial messages
					List<HashMap<Object, Integer>> temp = unknownRemoteMap.get(resourceName);
					for (int i = 0; i < temp.size(); i++) { //Send response to every manager waiting on resource information
						if (mess instanceof AccessRequestMsg) {
							AccessRequestMsg accRequest = (AccessRequestMsg)mess;
							ActorRef user = accRequest.getReplyTo();
							user.tell(new AccessRequestDeniedMsg(accRequest.getAccessRequest(), AccessRequestDenialReason.RESOURCE_NOT_FOUND), getSelf());
							log(LogMsg.makeAccessRequestDeniedLogMsg(user, getSelf(), accRequest.getAccessRequest(), AccessRequestDenialReason.RESOURCE_NOT_FOUND));
						}
						else if (mess instanceof ManagementRequestMsg) {
							ManagementRequestMsg management = (ManagementRequestMsg)mess;
							ActorRef user = management.getReplyTo();
							user.tell(new ManagementRequestDeniedMsg(management.getRequest(), ManagementRequestDenialReason.RESOURCE_NOT_FOUND), getSelf());
							log(LogMsg.makeManagementRequestDeniedLogMsg(user, getSelf(), management.getRequest(), ManagementRequestDenialReason.RESOURCE_NOT_FOUND));
						}
						else if (mess instanceof AccessReleaseMsg) {
							AccessReleaseMsg release = (AccessReleaseMsg)mess;
							ActorRef user = release.getSender();
							log(LogMsg.makeAccessReleaseIgnoredLogMsg(user, getSelf(), release.getAccessRelease()));
						}
					}
					unknownRemoteMap.remove(resourceName); //Remove resource from unknown map, we know it does not exist
				}
			}
		}
		else { //Respondent has the resource locally, so go through stored messages and forward to correct manager
			List<HashMap<Object, Integer>> temp = unknownRemoteMap.get(resourceName);
			if (temp != null) {
				for (int i = 0; i < temp.size(); i++) {
					HashMap<Object, Integer> unk = temp.get(i);
					Object mess = unk.keySet().toArray()[0];
					forwardRequestMessage(mess, from, getSelf());
				}
				//Remove resource from unknown map and add it to known locations map for easier access
				unknownRemoteMap.remove(resourceName);
				remoteResources.put(resourceName, from);
				log(LogMsg.makeRemoteResourceDiscoveredLogMsg(getSelf(), from, resourceName));
			}
		}
	}


	//When a request comes in and the resource is not stored locally or in a known remote, this method is used to
	//both send out WhoHasResourceRequestMsg to all known managers in the system, as well as update the local
	//unknownRemoteResources list to process during WhoHasResourceResponseMsg
	private void searchUnknownRemote(Object requestMsg) {
		if (requestMsg instanceof AccessRequestMsg) {
			AccessRequestMsg accessRequest = (AccessRequestMsg)requestMsg;
			String resourceName = accessRequest.getAccessRequest().getResourceName();
			HashMap<Object, Integer> unknown = new HashMap<>();
			unknown.put(accessRequest, 0);

			//If this is a new resource not on the local manager, add an entry in userAccessLevels and toDisable
			if (!userAccessLevels.containsKey(resourceName))
				userAccessLevels.put(resourceName, new LinkedList<>());
			if (!toDisable.containsKey(resourceName))
				toDisable.put(resourceName, new LinkedList<>());

			if (unknownRemoteMap.containsKey(resourceName)) //If resource is already in map, add new request to existing list
				unknownRemoteMap.get(resourceName).add(unknown);
			else { //Otherwise, look at all system managers and send resource request messages to all of them for current resource
				int i = 0;
				while (i < systemManagers.size()) {
					//Increment count of managers queried
					unknown.put(accessRequest, unknown.get(accessRequest) + 1);
					ActorRef manager = systemManagers.get(i);
					manager.tell(new WhoHasResourceRequestMsg(resourceName, getSelf()), getSelf());
					i++;
				}

				//Finally, add resource with request message to unknownResourceMap
				List<HashMap<Object, Integer>> temp = new LinkedList<>();
				temp.add(unknown);
				unknownRemoteMap.put(resourceName, temp);
			}
		}
		else if (requestMsg instanceof AccessReleaseMsg) {
			AccessReleaseMsg releaseRequest = (AccessReleaseMsg)requestMsg;
			String resourceName = releaseRequest.getAccessRelease().getResourceName();
			HashMap<Object, Integer> unknown = new HashMap<>();
			unknown.put(releaseRequest, 0);

			//If this is a new resource not on the local manager, add an entry in userAccessLevels and toDisable
			if (!userAccessLevels.containsKey(resourceName))
				userAccessLevels.put(resourceName, new LinkedList<>());
			if (!toDisable.containsKey(resourceName))
				toDisable.put(resourceName, new LinkedList<>());

			if (unknownRemoteMap.containsKey(resourceName)) //If resource is already in map, add new request to existing list
				unknownRemoteMap.get(resourceName).add(unknown);
			else { //Otherwise, look at all system managers and send resource request messages to all of them for current resource
				int i = 0;
				while (i < systemManagers.size()) {
					//Increment count of managers queried
					unknown.put(releaseRequest, unknown.get(releaseRequest) + 1);
					ActorRef manager = systemManagers.get(i);
					manager.tell(new WhoHasResourceRequestMsg(resourceName, getSelf()), getSelf());
					i++;
				}

				//Finally, add resource with request message to unknownResourceMap
				List<HashMap<Object, Integer>> temp = new LinkedList<>();
				temp.add(unknown);
				unknownRemoteMap.put(resourceName, temp);
			}
		}
		else if (requestMsg instanceof ManagementRequestMsg) {
			ManagementRequestMsg managementRequest = (ManagementRequestMsg)requestMsg;
			String resourceName = managementRequest.getRequest().getResourceName();
			HashMap<Object, Integer> unknown = new HashMap<>();
			unknown.put(managementRequest, 0);

			//If this is a new resource not on the local manager, add an entry in userAccessLevels and toDisable
			if (!userAccessLevels.containsKey(resourceName))
				userAccessLevels.put(resourceName, new LinkedList<>());
			if (!toDisable.containsKey(resourceName))
				toDisable.put(resourceName, new LinkedList<>());

			if (unknownRemoteMap.containsKey(resourceName)) //If resource is already in map, add new request to existing list
				unknownRemoteMap.get(resourceName).add(unknown);
			else { //Otherwise, look at all system managers and send resource request messages to all of them for current resource
				int i = 0;
				while (i < systemManagers.size()) {
					//Increment count of managers queried
					unknown.put(managementRequest, unknown.get(managementRequest) + 1);
					ActorRef manager = systemManagers.get(i);
					manager.tell(new WhoHasResourceRequestMsg(resourceName, getSelf()), getSelf());
					i++;
				}

				//Finally, add resource with request message to unknownResourcesMap
				List<HashMap<Object, Integer>> temp = new LinkedList<>();
				temp.add(unknown);
				unknownRemoteMap.put(resourceName, temp);
			}
		}
	}

	//Helper method to forward an incoming message to a known remote manager
	//Parses message for type, then forwards it to the actor passed in as parameter, and logs the forwarding
	private void forwardRequestMessage(Object msg, ActorRef forwardTo, ActorRef from) {
		if (msg instanceof AccessRequestMsg) {
			AccessRequestMsg message = (AccessRequestMsg)msg;
			forwardTo.tell(message, from);
			log(LogMsg.makeAccessRequestForwardedLogMsg(getSelf(), forwardTo, message.getAccessRequest()));
		}
		else if (msg instanceof AccessReleaseMsg) {
			AccessReleaseMsg message = (AccessReleaseMsg)msg;
			forwardTo.tell(message, from);
			log(LogMsg.makeAccessReleaseForwardedLogMsg(getSelf(), forwardTo, message.getAccessRelease()));
		}
		else if (msg instanceof ManagementRequestMsg) {
			ManagementRequestMsg message = (ManagementRequestMsg)msg;
			forwardTo.tell(message, from);
			log(LogMsg.makeManagementRequestForwardedLogMsg(getSelf(), forwardTo, message.getRequest()));
		}
	}

//	//Helper method to easily deny any kind of request message
//	private void denyRequestMessage(Object msg, String denialReason) {
//		AccessRequestMsg request;
//		ManagementRequestMsg management;
//		AccessReleaseMsg release;
//		ActorRef user;
//
//		switch (denialReason) {
//			case "RESOURCE_NOT_FOUND":
//				if (msg instanceof AccessRequestMsg) {
//					request = (AccessRequestMsg)msg;
//					user = request.getReplyTo();
//					user.tell(new AccessRequestDeniedMsg(request.getAccessRequest(), AccessRequestDenialReason.RESOURCE_NOT_FOUND), getSelf());
//					log(LogMsg.makeAccessRequestDeniedLogMsg(user, getSelf(), request.getAccessRequest(), AccessRequestDenialReason.RESOURCE_NOT_FOUND));
//				}
//				else if (msg instanceof ManagementRequestMsg) {
//					management = (ManagementRequestMsg)msg;
//					user = management.getReplyTo();
//					user.tell(new ManagementRequestDeniedMsg(management.getRequest(), ManagementRequestDenialReason.RESOURCE_NOT_FOUND), getSelf());
//					log(LogMsg.makeManagementRequestDeniedLogMsg(user, getSelf(), management.getRequest(), ManagementRequestDenialReason.RESOURCE_NOT_FOUND));
//				}
//				//Not applicable for release messages, but makes WhoHasResourceResponseHandler a little neater
//				else if (msg instanceof AccessReleaseMsg) {
//					release = (AccessReleaseMsg)msg;
//					user = release.getSender();
//					log(LogMsg.makeAccessReleaseIgnoredLogMsg(user, getSelf(), release.getAccessRelease()));
//				}
//				break;
//			case "RESOURCE_BUSY":
//				request = (AccessRequestMsg)msg;
//				user = request.getReplyTo();
//				user.tell(new AccessRequestDeniedMsg(request.getAccessRequest(), AccessRequestDenialReason.RESOURCE_BUSY), getSelf());
//				log(LogMsg.makeAccessRequestDeniedLogMsg(user, getSelf(), request.getAccessRequest(), AccessRequestDenialReason.RESOURCE_BUSY));
//				break;
//			case "RESOURCE_DISABLED":
//				request = (AccessRequestMsg)msg;
//				user = request.getReplyTo();
//				user.tell(new AccessRequestDeniedMsg(request.getAccessRequest(), AccessRequestDenialReason.RESOURCE_DISABLED), getSelf());
//				log(LogMsg.makeAccessRequestDeniedLogMsg(user, getSelf(), request.getAccessRequest(), AccessRequestDenialReason.RESOURCE_DISABLED));
//				break;
//			case "ACCESS_HELD_BY_USER":
//				management = (ManagementRequestMsg)msg;
//				user = management.getReplyTo();
//				user.tell(new ManagementRequestDeniedMsg(management.getRequest(), ManagementRequestDenialReason.ACCESS_HELD_BY_USER), getSelf());
//				log(LogMsg.makeManagementRequestDeniedLogMsg(user, getSelf(), management.getRequest(), ManagementRequestDenialReason.ACCESS_HELD_BY_USER));
//				break;
//			default:
//				//Ignore bad access release message
//				release = (AccessReleaseMsg)msg;
//				user = release.getSender();
//				log(LogMsg.makeAccessReleaseIgnoredLogMsg(user, getSelf(), release.getAccessRelease()));
//				break;
//		}
//	}


	// You may want to add data structures for managing local resources and users, storing
	// remote managers, etc.
	//
	// REMEMBER:  YOU ARE NOT ALLOWED TO CREATE MUTABLE DATA STRUCTURES THAT ARE SHARED BY
	// MULTIPLE ACTORS!
	
	/* (non-Javadoc)
	 * 
	 * You must provide an implementation of the onReceive() method below.
	 * 
	 * @see akka.actor.UntypedActor#onReceive(java.lang.Object)
	 */
	//Parses received message for any of the valid messages requiring a response, and if found passes the
	//incoming message along with the sender as parameters to the appropriate helper method
	@Override
	public void onReceive(Object msg) throws Exception {
		//Reply to whoever sent the message to this ResourceManager
		if (msg instanceof AddInitialLocalResourcesRequestMsg) {
			AddInitialLocalResourcesHandler((AddInitialLocalResourcesRequestMsg)msg, getSender());
		}
		//Reply to whoever sent the message to this ResourceManager
		else if (msg instanceof AddLocalUsersRequestMsg) {
			AddLocalUsersHandler((AddLocalUsersRequestMsg)msg, getSender());
		}
		//Reply to whoever sent the message to this ResourceManager
		else if (msg instanceof AddRemoteManagersRequestMsg) {
			AddRemoteManagersHandler((AddRemoteManagersRequestMsg)msg, getSender());
		}
		//Reply to the user that sent the AccessRequestMsg
		else if (msg instanceof AccessRequestMsg) {
			AccessRequestMsg accessMsg = (AccessRequestMsg)msg;
			AccessRequestBlocking accessBlocking = new AccessRequestBlocking(accessMsg, false);
			AccessRequestHandler(accessBlocking, accessMsg.getReplyTo());
		}
		//Reply to the user that sent the AccessReleaseMsg
		else if (msg instanceof AccessReleaseMsg) {
			AccessReleaseMsg releaseMsg = (AccessReleaseMsg)msg;
			AccessReleaseHandler(releaseMsg, releaseMsg.getSender());
		}
		//Reply to the user that sent the ManagementRequestMsg
		else if (msg instanceof ManagementRequestMsg) {
			ManagementRequestMsg managementMsg = (ManagementRequestMsg)msg;
			ManagementRequestHandler(managementMsg, managementMsg.getReplyTo());
		}
		//Reply to the manager that originally sent the resource request
		else if (msg instanceof WhoHasResourceRequestMsg) {
			WhoHasResourceRequestMsg resourceRequest = (WhoHasResourceRequestMsg)msg;
			WhoHasResourceRequestHandler(resourceRequest, resourceRequest.getSender());
		}
		//Reply to the manager that sent the resource response
		else if (msg instanceof WhoHasResourceResponseMsg) {
			WhoHasResourceResponseMsg resourceResponse = (WhoHasResourceResponseMsg)msg;
			WhoHasResourceResponseHandler(resourceResponse, resourceResponse.getSender());
		}
	}
}
