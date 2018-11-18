package cmsc433.p4.util;

import cmsc433.p4.messages.AccessRequestMsg;

public class UnknownAccessRequestRemote extends UnknownRemote {
    private AccessRequestMsg accessRequest;

    public UnknownAccessRequestRemote(AccessRequestMsg msg) {
        this.accessRequest = msg;
    }

    public AccessRequestMsg getRequestMsg() {
        return accessRequest;
    }
}
