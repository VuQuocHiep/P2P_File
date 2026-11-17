package vn.edu.p2p.server.dto.response;

public record PeerResponse(
        String id,
        String owner,
        String name,
        String host,
        int port,
        String status,
        boolean online,
        long lastSeen) {}
