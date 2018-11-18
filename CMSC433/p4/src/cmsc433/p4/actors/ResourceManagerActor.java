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
	private HashMap<String, Resource> localResources = new HashMap<String, Resource>();

	//All other managers this knows about in the system
	private ArrayList<ActorRef> systemManagers = new ArrayList<ActorRef>();

	//All local users to this manager
	private ArrayList<ActorRef> usersLocal = new ArrayList<ActorRef>();

	//Map of remote resource names and the manager that owns them
	private HashMap<String, ActorRef> remoteResources = new HashMap<String, ActorRef>();

	//Queue for access requests being blocked
	private Queue<AccessRequestMsg> blockingAccessRequests = new LinkedList<AccessRequestMsg>();

	//Maps resource name to the requests to disable it
	private HashMap<String, List<ManagementRequestMsg>> toDisable = new HashMap<String, List<ManagementRequestMsg>>();

	//Maps name of local resources to users in the system and their access level to that resource
	private HashMap<String, List<UserLevel>> userAccessLevels = new HashMap<String, List<UserLevel>>();

	//Maps non-local resources to their managers in the system to help in forwarding request messages more quickly
	private HashMap<String, ActorRef> resourceRemoteLocations = new HashMap<String, ActorRef>();

	//Map of resource name to unknown remote resource msg/counter combo, used when looking for a resource of unknown location
	private HashMap<String, List<UnknownRemote>> unknownRemoteMap = new HashMap<String, List<UnknownRemote>>();
	
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

		for (int i = 0; i < resources.size(); i++) {
			Resource res = resources.get(i);
			localResources.put(res.getName(), res); //Map the name and resource for O(1) resource location
			log(LogMsg.makeLocalResourceCreatedLogMsg(getSelf(), res.getName())); //Log creation of new local resource

			res.enable(); //Resources are disabled by default, so we must enable them
			log(LogMsg.makeResourceStatusChangedLogMsg(getSelf(), res.getName(), res.getStatus())); //Log change of resource status
		}

		//Send the reply message
		from.tell(new AddInitialLocalResourcesResponseMsg(msg), getSelf());
	}

	//Adds list of local users from message to localUsers, and replies to the incoming message
	private void AddLocalUsersHandler(AddLocalUsersRequestMsg msg, ActorRef from) {
		ArrayList<ActorRef> localUsers = msg.getLocalUsers();

		//Add received set of local users to the usersLocal list
		for (int i = 0; i < localUsers.size(); i++) {
			usersLocal.add(localUsers.get(i));
		}

		//Send the reply message
		from.tell(new AddLocalUsersResponseMsg(msg), getSelf());
	}

	//Adds incoming list of managers to known managers in system, and replies to message sender
	private void AddRemoteManagersHandler(AddRemoteManagersRequestMsg msg, ActorRef from) {
		ArrayList<ActorRef> managers = msg.getManagerList();

		//Add the managers into the list of system managers, as long as it is not this specific manager
		for (int i = 0; i < managers.size(); i++) {
			if (!managers.get(i).equals(getSelf()))
				systemManagers.add(managers.get(i));
		}

		//Send the reply message
		from.tell(new AddRemoteManagersResponseMsg(msg), getSelf());
	}

	//TODO
	//Handles incoming access requests
	private void AccessRequestHandler(AccessRequestBlocking block, ActorRef from) {
		AccessRequestMsg msg = block.getAccessRequestMsg();
		AccessRequest request = msg.getAccessRequest();
		String resourceName = request.getResourceName();
		boolean hasResource = localResources.containsKey(resourceName);
		if (!block.isBlocking())
			log(LogMsg.makeAccessRequestReceivedLogMsg(msg.getReplyTo(), getSelf(), request));

		if (hasResource) { //If the resource is contained locally, process it in place
			AccessRequestType requestType = request.getType();

			//Check if the resource was disabled before proceeding
			ResourceStatus resourceStatus = localResources.get(resourceName).getStatus();
			if (toDisable.containsKey(resourceName) || resourceStatus == ResourceStatus.DISABLED) {
				from.tell(new AccessRequestDeniedMsg(request, AccessRequestDenialReason.RESOURCE_DISABLED), getSelf()); //TODO: Is this right replyTo?
				log(LogMsg.makeAccessRequestDeniedLogMsg(from, getSelf(), request, AccessRequestDenialReason.RESOURCE_DISABLED));
				return; //TODO: Refactor to get rid of this
			}

			if (!userAccessLevels.containsKey(resourceName))
				userAccessLevels.put(resourceName, new ArrayList<UserLevel>());

			//If
			if (userAccessLevels.get(resourceName).size() == 0) {
				switch (requestType){
					case CONCURRENT_READ_BLOCKING:
					case CONCURRENT_READ_NONBLOCKING:
						UserLevel level = new UserLevel(from, AccessType.CONCURRENT_READ);
						userAccessLevels.get(resourceName).add(level);
						from.tell(new AccessRequestGrantedMsg(request), getSelf());
						log(LogMsg.makeAccessRequestGrantedLogMsg(from, getSelf(), request));
						break;
					case EXCLUSIVE_WRITE_BLOCKING:
					case EXCLUSIVE_WRITE_NONBLOCKING:
						UserLevel level2 = new UserLevel(from, AccessType.EXCLUSIVE_WRITE);
						userAccessLevels.get(resourceName).add(level2);
						from.tell(new AccessRequestGrantedMsg(request), getSelf());
						log(LogMsg.makeAccessRequestGrantedLogMsg(from, getSelf(), request));
						break;
					default:
						//Do nothing
						break;
				}
			}
			//Else it is not empty
			else {
				List<UserLevel> temp = userAccessLevels.get(resourceName);
				boolean access = true;

				for (int i = 0; i < temp.size(); i++) {
					AccessType accessType = temp.get(i).getAccessType();
					ActorRef user = temp.get(i).getUser();
					if (accessType == AccessType.EXCLUSIVE_WRITE && ! user.equals(from)) {
						access = false;
						break;
					}
					else if (accessType == AccessType.CONCURRENT_READ && !user.equals(from)) {
						if (requestType == AccessRequestType.EXCLUSIVE_WRITE_BLOCKING || requestType == AccessRequestType.EXCLUSIVE_WRITE_NONBLOCKING) {
							access = false;
							break;
						}
					}
				}

				//If the user can access the resource, reply with a access granted message
				if (access) {
					//TODO: rejigger this
					UserLevel userLevel = null;
					if (requestType == AccessRequestType.CONCURRENT_READ_BLOCKING || requestType == AccessRequestType.CONCURRENT_READ_NONBLOCKING) {
						userLevel = new UserLevel(from, AccessType.CONCURRENT_READ);
					}
					else {
						userLevel = new UserLevel(from, AccessType.EXCLUSIVE_WRITE);
					}

					userAccessLevels.get(resourceName).add(userLevel);
					from.tell(new AccessRequestGrantedMsg(request), getSelf()); //TODO: Is this right?
					log(LogMsg.makeAccessRequestGrantedLogMsg(from, getSelf(), request));
				}
				//Else the user can't access the resource, reply with an access denied message
				else {
					if (requestType == AccessRequestType.CONCURRENT_READ_BLOCKING || requestType == AccessRequestType.EXCLUSIVE_WRITE_BLOCKING)
						blockingAccessRequests.add(msg);
					else {
						from.tell(new AccessRequestDeniedMsg(request, AccessRequestDenialReason.RESOURCE_BUSY), getSelf());
						log(LogMsg.makeAccessRequestDeniedLogMsg(from, getSelf(), request, AccessRequestDenialReason.RESOURCE_BUSY));
					}
				}
			}
		}
		else { //If resource does not exist locally, either redirect the request if we know where to go, or find it if we dont
			if (remoteResources.containsKey(resourceName)) { //We know where to go, so redirect the message there
				ActorRef remoteManager = remoteResources.get(resourceName);
				remoteManager.tell(msg, getSelf()); //TODO: Should this be to getSender()?
				log(LogMsg.makeAccessRequestForwardedLogMsg(getSelf(), remoteManager, request));
			}
			else { //We don't know where it is, so we have to look for it in the system
				searchUnknownRemote(msg);
			}
		}
	}

	//TODO
	//Process incoming access release messages, acknowledging that a resource has been released somewhere in the system
	private void AccessReleaseHandler(AccessReleaseMsg msg, ActorRef from) {
		AccessRelease accessRelease = msg.getAccessRelease();
		String resourceName = accessRelease.getResourceName();
		AccessType accessType = accessRelease.getType();
		boolean hasResource = localResources.containsKey(resourceName);

		log(LogMsg.makeAccessReleaseReceivedLogMsg(from, getSelf(), accessRelease)); //Log access release msg received

		if (hasResource) { //If the resource is local to this manager, process it here
			List<UserLevel> temp = userAccessLevels.get(resourceName);
			boolean canAccess = false;

			for (int i = 0; i < temp.size(); i++) { //Iterate through list of users with access levels on the resource
				UserLevel accessLevel = temp.get(i);
				if (accessLevel.getUser().equals(from)) { //If the user is in the accessLevel list
					if (accessLevel.getAccessType().equals(accessType)) { //And access type is the same as in the request
						//Remove the user from the access list, set canAccess true, and log
						temp.remove(accessLevel); //TODO: Is this right?
						canAccess = true;
						log(LogMsg.makeAccessReleasedLogMsg(from, getSelf(), accessRelease));
					}
				}
			}

			//If the user can't access the message, log that the release was ignored, don't send a response
			if (!canAccess)
				log(LogMsg.makeAccessReleaseIgnoredLogMsg(from, getSelf(), accessRelease));

			if (temp.size() == 0) { //If there are no users whomst have tried to access the resource, check the toDisable requests
				if (toDisable.containsKey(resourceName) && localResources.get(resourceName).getStatus() == ResourceStatus.ENABLED) {
					localResources.get(resourceName).disable(); //Disable the resource
					log(LogMsg.makeResourceStatusChangedLogMsg(getSelf(), resourceName, localResources.get(resourceName).getStatus()));

					//Iterate through list of pending disable messages for the resource in question, and reply to them
					//with the news that the resource has been disabled
					List<ManagementRequestMsg> messages = toDisable.get(resourceName);
					for (int i = 0; i < messages.size(); i++) {
						ManagementRequest request = messages.get(i).getRequest();
						ActorRef target = messages.get(i).getReplyTo();
						//toDisable.get(resourceName).get(i)

						target.tell(new ManagementRequestGrantedMsg(request), getSelf());
						log(LogMsg.makeManagementRequestGrantedLogMsg(target, getSelf(), request));
					}
				}
			}
		}
		else { //If the resource is not local, attempt to find where it exists, and forward the message
			if (resourceRemoteLocations.containsKey(resourceName)) { //If the location of this resource has already been found
				//Forward the message to the resource location, keeping the sender the same
				ActorRef remoteSource = resourceRemoteLocations.get(resourceName);
				remoteSource.tell(msg, from);
				log(LogMsg.makeAccessReleaseForwardedLogMsg(getSelf(), remoteSource, accessRelease));
			}
			else { //If we don't know where the resource is, we need to find it
				searchUnknownRemote(msg);
			}
		}
	}

	//TODO
	private void ManagementRequestHandler(ManagementRequestMsg msg, ActorRef from) {
		ManagementRequest managementRequest = msg.getRequest();
	}

	//Checks to see if this resource manager has the resource in question, and sends a response with the result
	//to whatever resource manager sent the request
	private void WhoHasResourceRequestHandler(WhoHasResourceRequestMsg msg, ActorRef from) {
		String resourceName = msg.getResourceName();
		boolean hasResource = localResources.containsKey(resourceName); //True if this manager has the resource locally

		//Reply to sender with status about whether or not this manager has the requested resource locally
		from.tell(new WhoHasResourceResponseMsg(resourceName, hasResource, getSelf()), getSelf());
	}

	//TODO
	private void WhoHasResourceResponseHandler(WhoHasResourceResponseMsg msg, ActorRef from) {
		boolean hasResource = msg.getResult();
		String resourceName = msg.getResourceName();

		if (!hasResource) { //Respondant does not have resource available locally
			if (unknownRemoteMap.get(resourceName) != null) {
				UnknownRemote unk = unknownRemoteMap.get(resourceName).get(0);
				if (unk != null) {
					unk.decrementCount();
					if (unk.getCounter() == 0) {
						List<UnknownRemote> temp = unknownRemoteMap.get(resourceName);
						for (int i = 0; i < temp.size(); i++) {
							//TODO
						}
						unknownRemoteMap.remove(resourceName);
					}
				}
			}
		}
		else { //Respondent has the resource locally, so go through stored messages and update with the location
			log(LogMsg.makeRemoteResourceDiscoveredLogMsg(getSelf(), from, resourceName));

			List<UnknownRemote> temp = unknownRemoteMap.get(resourceName);
			if (temp != null) {
				for (int i = 0; i < temp.size(); i++) {
					//TODO
				}
				unknownRemoteMap.remove(resourceName);
				remoteResources.put(resourceName, from);
			}
		}
	}

	//Below here are private helper methods that are not called by onReceive directly, but are secondary
	//helpers to the message handlers

	//When a request comes in and the resource is not stored locally or in a known remote, this method is used to
	//both send out WhoHasResourceRequestMsg to all known managers in the system, as well as update the local
	//unknownRemoteResources list to process during WhoHasResourceResponseMsg
	//TODO: Do this better
	private void searchUnknownRemote(Object requestMsg) {
		if (requestMsg instanceof AccessRequestMsg) {
			AccessRequestMsg accessRequest = (AccessRequestMsg)requestMsg;
			String resourceName = accessRequest.getAccessRequest().getResourceName();
			UnknownRemote unk = new UnknownRemote(accessRequest);

			if (unknownRemoteMap.containsKey(resourceName)) //If resource is already in map, add new request to existing list
				unknownRemoteMap.get(resourceName).add(unk);
			else { //Otherwise, look at all system managers and send resource request messages to all of them for current resource
				int i = 0;
				while (i < systemManagers.size()) {
					unk.incrementCount();
					ActorRef manager = systemManagers.get(i);
					manager.tell(new WhoHasResourceRequestMsg(resourceName, getSelf()), getSelf());
					i++;
				}

				//Finally, add resource with request message to unknownResourceMap
				List<UnknownRemote> temp = new LinkedList<UnknownRemote>();
				temp.add(unk);
				unknownRemoteMap.put(resourceName, temp);
			}
		}
		else if (requestMsg instanceof AccessReleaseMsg) {
			AccessReleaseMsg releaseRequest = (AccessReleaseMsg)requestMsg;
			String resourceName = releaseRequest.getAccessRelease().getResourceName();
			UnknownRemote unk = new UnknownRemote(releaseRequest);

			if (unknownRemoteMap.containsKey(resourceName)) //If resource is already in map, add new request to existing list
				unknownRemoteMap.get(resourceName).add(unk);
			else { //Otherwise, look at all system managers and send resource request messages to all of them for current resource
				int i = 0;
				while (i < systemManagers.size()) {
					unk.incrementCount();
					ActorRef manager = systemManagers.get(i);
					manager.tell(new WhoHasResourceRequestMsg(resourceName, getSelf()), getSelf());
					i++;
				}

				//Finally, add resource with request message to unknownResourceMap
				List<UnknownRemote> temp = new LinkedList<UnknownRemote>();
				temp.add(unk);
				unknownRemoteMap.put(resourceName, temp);
			}
		}
		else if (requestMsg instanceof ManagementRequestMsg) {
			ManagementRequestMsg managementRequest = (ManagementRequestMsg)requestMsg;
			String resourceName = managementRequest.getRequest().getResourceName();
			UnknownRemote unk = new UnknownRemote(managementRequest);

			if (unknownRemoteMap.containsKey(resourceName)) //If resource is already in map, add new request to existing list
				unknownRemoteMap.get(resourceName).add(unk);
			else { //Otherwise, look at all system managers and send resource request messages to all of them for current resource
				int i = 0;
				while (i < systemManagers.size()) {
					unk.incrementCount();
					ActorRef manager = systemManagers.get(i);
					manager.tell(new WhoHasResourceRequestMsg(resourceName, getSelf()), getSelf());
					i++;
				}

				//Finally, add resource with request message to unknownResourcesMap
				List<UnknownRemote> temp = new LinkedList<UnknownRemote>();
				temp.add(unk);
				unknownRemoteMap.put(resourceName, temp);
			}
		}
	}


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

			if (!blockingAccessRequests.isEmpty()) {
				Iterator<AccessRequestMsg> iter = blockingAccessRequests.iterator();
				while (iter.hasNext()) {
					AccessRequestMsg message = iter.next();
					Acce
				}
			}
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
		//TODO: Might not need the getSender()
		//Reply to the manager that sent the resource response
		else if (msg instanceof WhoHasResourceResponseMsg) {
			WhoHasResourceResponseMsg resourceResponse = (WhoHasResourceResponseMsg)msg;
			WhoHasResourceResponseHandler(resourceResponse, resourceResponse.getSender());
		}
	}
}
