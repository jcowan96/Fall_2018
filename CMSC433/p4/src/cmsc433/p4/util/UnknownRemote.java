package cmsc433.p4.util;

public class UnknownRemote {
    private Object requestMsg;
    private int counter = 0;

    private UnknownRemote(Object request) {
        this.requestMsg = request;
    }

    private void incrementCount() {
        counter += 1;
    }

    private void decrementCount() {
        counter -= 1;
    }

    private int getCounter() {
        return counter;
    }

    private Object getRequestMsg() {
        return requestMsg;
    }
}
