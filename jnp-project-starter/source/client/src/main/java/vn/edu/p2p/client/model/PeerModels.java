package vn.edu.p2p.client.model;

import vn.edu.p2p.common.model.Manifest;
import java.util.*;

/** Persisted models; mutable instances are guarded by the repository monitor. */
public final class PeerModels {
    public static class Entry {
        public Manifest manifest;
        public boolean complete, sharing = true;
        public String catalogStatus = "PENDING";
        public Set<Integer> chunks = new TreeSet<>();
    }
    public static class Job {
        public String id, fileId, status = "PAUSED", error = "";
        public long created, downloaded, finished;
        public Map<String, Long> sourceBytes = new LinkedHashMap<>();
        public Map<Integer, ChunkSource> chunkSources = new TreeMap<>();
    }
    public record ChunkSource(String peerId, String name, String owner, String host, int port, long receivedAt) {}
    public static class State {
        public String peerId = "", owner = "", name = "", host = "127.0.0.1";
        public Map<String, Entry> library = new LinkedHashMap<>();
        public Map<String, Job> jobs = new LinkedHashMap<>();
        public List<String> events = new ArrayList<>();
    }
    private PeerModels() {}
}
