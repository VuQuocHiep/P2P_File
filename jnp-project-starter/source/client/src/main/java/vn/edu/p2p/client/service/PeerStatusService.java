package vn.edu.p2p.client.service;

import com.fasterxml.jackson.databind.*;
import vn.edu.p2p.client.model.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

final class PeerStatusService {
    private final PeerContext context;
    PeerStatusService(PeerContext context) { this.context = context; }
    Map<String, Object> status() {
        synchronized (context.store) {
            var result = new LinkedHashMap<String, Object>();
            result.put("user", context.user); result.put("tracker", context.tracker.base()); result.put("connection", context.connection);
            result.put("peerId", context.store.snapshot().peerId); result.put("peerStatus", context.peerStatus); result.put("name", context.store.snapshot().name);
            result.put("host", context.store.snapshot().host); result.put("tcpPort", context.tcpPort); result.put("uploaded", context.tcpServer.sentBytes());
            result.put("catalog", context.catalogSnapshot);
            result.put("lab", Map.of("fault", context.tcpServer.fault(), "benchmark", context.benchmark, "samples", context.peerLabService.networkSamples(),
                    "links", new ArrayList<>(context.links.values()), "recentLinks", new ArrayList<>(context.recentLinks.values()),
                    "sourceStats", new TreeMap<>(context.sourceStats)));
            result.put("network", Map.of("upload", context.tcpServer.metrics(), "download", context.tcpClient.metrics(),
                    "downloadWorkers", context.downloadPools.values().stream().mapToInt(ThreadPoolExecutor::getActiveCount).sum(),
                    "activeDownloads", context.running.size(), "workerLimitPerFile", 4, "fileLimit", 2));
            var library = new ArrayList<Map<String, Object>>();
            for (Entry e : context.store.snapshot().library.values()) library.add(Map.of("manifest", e.manifest, "complete", e.complete,
                    "sharing", e.sharing, "catalogStatus", e.catalogStatus, "chunks", new ArrayList<>(e.chunks)));
            var jobs = new ArrayList<Map<String, Object>>();
            for (Job j : context.store.snapshot().jobs.values()) {
                Entry e = context.store.snapshot().library.get(j.fileId);
                long elapsed = Math.max(1, System.currentTimeMillis() - context.speedStart.getOrDefault(j.id, System.currentTimeMillis()));
                long speed = context.peerDownloadService.active(j.id) ? context.speedBytes.getOrDefault(j.id, new AtomicLong()).get() * 1000 / elapsed : 0;
                jobs.add(Map.of("id", j.id, "fileId", j.fileId, "name", e.manifest.name(), "status", j.status,
                        "bytes", context.peerDownloadService.completedBytes(e), "size", e.manifest.size(), "speed", speed, "error", j.error,
                        "sourceBytes", new LinkedHashMap<>(j.sourceBytes), "chunks", e.chunks.size()));
            }
            result.put("library", library); result.put("jobs", jobs); result.put("events", new ArrayList<>(context.store.snapshot().events)); return result;
        }
    }
}
