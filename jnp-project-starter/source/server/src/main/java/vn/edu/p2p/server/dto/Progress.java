package vn.edu.p2p.server.dto;

public record Progress(String id, String peerId, String fileId, String status, long bytes) {}
