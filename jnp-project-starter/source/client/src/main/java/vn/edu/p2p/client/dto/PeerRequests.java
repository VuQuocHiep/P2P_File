package vn.edu.p2p.client.dto;


public final class PeerRequests {
    public record Login(String username, String password, String name, String host) {}
    public record Registration(String username, String password) {}
    public record Download(String fileId) {}
    public record Sharing(boolean enabled) {}
    public record Fault(String mode, int delayMs, int seconds) {}
    private PeerRequests() {}
}
