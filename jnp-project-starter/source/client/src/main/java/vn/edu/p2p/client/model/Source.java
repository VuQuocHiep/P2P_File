package vn.edu.p2p.client.model;

import java.util.Set;

public record Source(String id, String name, String owner, String host, int port, Set<Integer> chunks, String ticket) {}
