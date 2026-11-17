package vn.edu.p2p.server.migration.legacy;

/** Legacy registry JSON model, retained for one-time migration. */
public class Peer {
    public String id, owner, name, host, status;
    public int port;
    public long lastSeen;
}
