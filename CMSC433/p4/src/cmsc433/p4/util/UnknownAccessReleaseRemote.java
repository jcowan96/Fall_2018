package cmsc433.p4.util;

import cmsc433.p4.messages.AccessReleaseMsg;

public class UnknownAccessReleaseRemote extends UnknownRemote {
    private AccessReleaseMsg releaseMsg;

    public UnknownAccessReleaseRemote(AccessReleaseMsg msg) {
        this.releaseMsg = msg;
    }

    public AccessReleaseMsg getRequestMsg() {
        return this.releaseMsg;
    }

}
