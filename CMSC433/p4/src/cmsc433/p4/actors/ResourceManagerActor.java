package cmsc433.p4.actors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

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
	private Queue<AccessRequestMsg> blockingRequests = new LinkedList<AccessRequestMsg>();

	//TODO: Figure out what this actually does
	private HashMap<String, List<ManagementRequestMsg>> xxx = new HashMap<String, List<ManagementRequestMsg>>();

	//Maps name of local resources to users in the system and their access level to that resource
	private HashMap<String, List<UserLevel>> xx = new HashMap<String, List<UserLevel>>();

	//Maps non-local resources to their managers in the system to help in forwarding request messages more quickly
	private HashMap<String, ActorRef> resourceRemoteLocations = new HashMap<String, ActorRef>();
	
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
	private void AccessRequestHandler(AccessRequestMsg msg, ActorRef from) {
		AccessRequest request = msg.getAccessRequest();

//		if (!isQueued) {
//			log(LogMsg.makeAccessRequestReceivedLogMsg(msg.getReplyTo(), getSelf(), msg.getAccessRequest()));
//		}
	}

	//TODO
	//Process incoming access release messages, acknowledging that a resource has been released somewhere in the system
	private void AccessReleaseHandler(AccessReleaseMsg msg, ActorRef from) {
		AccessRelease accessRelease = msg.getAccessRelease();
		String resourceName = accessRelease.getResourceName();
		AccessType accessType = accessRelease.getType();
		boolean hasResource = localResources.containsKey(resourceName);

		log(LogMsg.makeAccessReleaseReceivedLogMsg(from, getSelf(), accessRelease)); //Log access release msg received

		if (localResources.containsKey(resourceName)) { //If the resource is local to this manager, process it here

		}
		else { //If the resource is not local, attempt to find where it exists, and forward the message
			if (resourceRemoteLocations.containsKey(resourceName)) { //If the location of this resource has already been found
				ActorRef remoteSource = resourceRemoteLocations.get(resourceName);
				remoteSource.tell(msg, from); //Forward the message to the resource location, keeping the sender the same
				log(LogMsg.makeAccessReleaseForwardedLogMsg(getSelf(), remoteSource, accessRelease));
			}
			else { //If we don't know where the resource is, we need to find it
				//TODO
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
		boolean hasResource = localResources.containsKey(resourceName);

		from.tell(new WhoHasResourceResponseMsg(resourceName, hasResource, getSelf()), getSelf());
	}

	//TODO
	private void WhoHasResourceResponseHandler(WhoHasResourceResponseMsg msg, ActorRef from) {

	}

	//Below here are private helper methods that are not called by onReceive directly, but are secondary
	//helpers to the message handlers



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
	@Override
	public void onReceive(Object msg) throws Exception {
		//Parses received message for any of the valid messages requiring a response, and if found passes the
		//incoming message along with the sender as parameters to the appropriate helper method
		if (msg instanceof AddInitialLocalResourcesRequestMsg) {
			AddInitialLocalResourcesHandler((AddInitialLocalResourcesRequestMsg)msg, getSender());
		}
		else if (msg instanceof AddLocalUsersRequestMsg) {
			AddLocalUsersHandler((AddLocalUsersRequestMsg)msg, getSender());
		}
		else if (msg instanceof AddRemoteManagersRequestMsg) {
			AddRemoteManagersHandler((AddRemoteManagersRequestMsg)msg, getSender());
		}
		else if (msg instanceof AccessRequestMsg) {
			AccessRequestHandler((AccessRequestMsg)(msg), getSender());
		}
		else if (msg instanceof AccessReleaseMsg) {
			AccessReleaseHandler((AccessReleaseMsg)msg, getSender());
		}
		else if (msg instanceof ManagementRequestMsg) {
			ManagementRequestHandler((ManagementRequestMsg)msg, getSender());
		}
		else if (msg instanceof WhoHasResourceRequestMsg) {
			WhoHasResourceRequestHandler((WhoHasResourceRequestMsg)msg, getSender());
		}
		else if (msg instanceof WhoHasResourceResponseMsg) {
			WhoHasResourceResponseHandler((WhoHasResourceResponseMsg)msg, getSender());
		}
	}
}
