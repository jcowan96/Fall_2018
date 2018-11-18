package cmsc433.p4.messages;

import akka.actor.ActorRef;

public class WhoHasResourceRequestMsg {
	private final String resource_name;
	private final ActorRef sender;
	
	public WhoHasResourceRequestMsg (String resource, ActorRef sender) {
		this.resource_name = resource;
		this.sender = sender;
	}
	
	public String getResourceName () {
		return resource_name;
	}

	public ActorRef getSender () { return sender; }
	
	@Override 
	public String toString () {
		return "Who has " + resource_name + "?";
	}
}
