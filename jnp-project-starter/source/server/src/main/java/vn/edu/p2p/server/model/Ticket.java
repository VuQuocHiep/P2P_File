package vn.edu.p2p.server.model;

public record Ticket(String username, String sourceId, String fileId, long expires) {}
