package vn.edu.p2p.client.exception;

public class TrackerFailure extends java.io.IOException {
    public final int status;
    public TrackerFailure(int status, String message) { super(message); this.status = status; }
}
