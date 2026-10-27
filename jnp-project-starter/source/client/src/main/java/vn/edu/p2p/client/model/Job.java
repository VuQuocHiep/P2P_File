package vn.edu.p2p.client.model;

import java.util.*;

public class Job {
    public String id, fileId, status = "PAUSED", error = "";
    public long created, downloaded, finished;
    public Map<String, Long> sourceBytes = new LinkedHashMap<>();
    public Map<Integer, ChunkSource> chunkSources = new TreeMap<>();
}
