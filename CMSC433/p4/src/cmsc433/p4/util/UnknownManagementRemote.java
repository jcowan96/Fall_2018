package cmsc433.p4.util;

import cmsc433.p4.messages.ManagementRequestMsg;

public class UnknownManagementRemote extends UnknownRemote {
    private ManagementRequestMsg managementRequest;

    public UnknownManagementRemote(ManagementRequestMsg msg) {
        this.managementRequest = msg;
    }

    public ManagementRequestMsg getRequestMsg() {
        return managementRequest;
    }

}
