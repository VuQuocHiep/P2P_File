package vn.edu.p2p.common.protocol;

public record ChunkRequest(String ticket, String fileId, int chunk) {}
