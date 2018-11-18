package cmsc433.p4.util;

import cmsc433.p4.messages.AccessRequestMsg;

public class AccessRequestBlocking {
    private AccessRequestMsg accessRequestMsg;
    private boolean isBlocking;

    public AccessRequestBlocking(AccessRequestMsg msg, boolean blocking) {
        this.accessRequestMsg = msg;
        this.isBlocking = blocking;
    }

    public AccessRequestMsg getAccessRequestMsg() {
        return accessRequestMsg;
    }

    public boolean isBlocking() {
        return isBlocking;
    }
}
