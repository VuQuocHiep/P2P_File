package vn.edu.p2p.server.dto;

import vn.edu.p2p.common.model.Manifest;
import java.util.List;

public final class TrackerRequests {
    public record Login(String username, String password) {}
    public record RegisterPeer(String id, String name, String host, int port) {}
    public record Advertisement(String peerId, Manifest manifest, List<Integer> chunks) {}
    public record Heartbeat(String peerId) {}
    public record TicketRequest(String fileId, String sourceId) {}
    public record CheckTicket(String ticket, String peerId, String fileId) {}
    public record Progress(String id, String peerId, String fileId, String status, long bytes) {}
    public record Moderation(String status) {}
    private TrackerRequests() {}
}
