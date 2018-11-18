package cmsc433.p4.util;
import akka.actor.ActorRef;
import cmsc433.p4.enums.AccessType;

//Class basically is a tuple holding a ActorRef representing a user and the level of access the user has
//with a certain resource
public class UserLevel {
    private ActorRef user;
    private AccessType accessType;

    private UserLevel(ActorRef usr, AccessType at) {
        this.user = usr;
        this.accessType = at;
    }

    public ActorRef getUser() {
        return user;
    }

    public AccessType getAccessType() {
        return accessType;
    }
}
