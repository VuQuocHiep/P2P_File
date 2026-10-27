package vn.edu.p2p.server.model;

public record Audit(long time, String actor, String action, String target) {}
