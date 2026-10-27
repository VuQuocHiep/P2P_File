package vn.edu.p2p.client.model;

public record ChunkSource(String peerId, String name, String owner, String host, int port, long receivedAt) {}
