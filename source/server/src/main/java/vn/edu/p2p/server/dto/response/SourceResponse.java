package vn.edu.p2p.server.dto.response;

import java.util.List;

public record SourceResponse(
        String id,
        String owner,
        String name,
        String host,
        int port,
        String status,
        boolean online,
        long lastSeen,
        List<Integer> chunks) {}
