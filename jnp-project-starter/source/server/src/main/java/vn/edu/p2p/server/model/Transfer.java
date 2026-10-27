package vn.edu.p2p.server.model;

public record Transfer(String id, String owner, String peerId, String fileId, String name,
                       String status, long bytes, long time) {}
