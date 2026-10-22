package vn.edu.p2p.server.model;

import vn.edu.p2p.common.model.Manifest;
import java.util.*;

/** Persisted registry models; mutations are guarded by the TrackerService monitor. */
public final class TrackerModels {
    public static class Account {
        public String username, passwordHash, role, status;
        public long created;
        public Account() {}
        public Account(String username, String passwordHash, String role, String status) {
            this.username = username; this.passwordHash = passwordHash; this.role = role; this.status = status;
            this.created = System.currentTimeMillis();
        }
    }
    public static class Peer {
        public String id, owner, name, host, status;
        public int port;
        public long lastSeen;
    }
    public static class SharedFile {
        public Manifest manifest;
        public String status, submittedBy;
        public Map<String, List<Integer>> holders = new LinkedHashMap<>();
    }
    public record Audit(long time, String actor, String action, String target) {}
    public record Transfer(String id, String owner, String peerId, String fileId, String name,
                           String status, long bytes, long time) {}
    public static class Snapshot {
        public Map<String, Account> accounts = new LinkedHashMap<>();
        public Map<String, Peer> peers = new LinkedHashMap<>();
        public Map<String, SharedFile> files = new LinkedHashMap<>();
        public List<Audit> audit = new ArrayList<>();
        public Map<String, Transfer> transfers = new LinkedHashMap<>();
    }
    private TrackerModels() {}
}
