package cmsc433.p4.util;

public class UnknownRemote {
    private Object requestMsg;
    private int counter;

    public UnknownRemote() {
        this.requestMsg = null;
        this.counter = 0;
    }

    public UnknownRemote(Object request) {
        this.requestMsg = request;
        this.counter = 0;
    }

    public void incrementCount() {
        counter += 1;
    }

    public void decrementCount() {
        counter -= 1;
    }

    public int getCounter() {
        return counter;
    }

    public Object getRequestMsg() {
        return requestMsg;
    }
}
