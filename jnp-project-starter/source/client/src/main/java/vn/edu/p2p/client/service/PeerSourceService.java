package vn.edu.p2p.client.service;

import vn.edu.p2p.client.exception.TrackerFailure;
import com.fasterxml.jackson.databind.*;
import vn.edu.p2p.common.model.Manifest;
import vn.edu.p2p.client.model.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

final class PeerSourceService {
    private final PeerContext context;
    PeerSourceService(PeerContext context) { this.context = context; }
    byte[] fetchObserved(Source source, Manifest manifest, int index, String purpose) throws IOException {
        String key = UUID.randomUUID().toString();
        Map<String, Object> link = Map.of("peerId", source.id(), "name", source.name(), "host", source.host(),
                "port", source.port(), "chunk", index + 1, "purpose", purpose, "at", System.currentTimeMillis());
        context.links.put(key, link);
        try {
            byte[] bytes = context.tcpClient.fetch(source.host(), source.port(), source.ticket(), manifest, index);
            context.sourceReceived.computeIfAbsent(source.id(), id -> new AtomicLong()).addAndGet(bytes.length);
            context.sourceChunks.computeIfAbsent(source.id(), id -> new AtomicLong()).incrementAndGet();
            context.recentLinks.put(source.id(), Map.of("peerId", source.id(), "name", source.name(), "host", source.host(),
                    "port", source.port(), "chunk", index + 1, "purpose", purpose, "at", System.currentTimeMillis()));
            return bytes;
        } finally { context.links.remove(key); }
    }
    List<Source> available(String fileId) throws Exception {
        var result = new ArrayList<Source>(); String self;
        synchronized (context.store) { self = context.store.snapshot().peerId; }
        JsonNode list = context.tracker.call("GET", "/api/files/" + fileId + "/sources", null);
        for (JsonNode s : list) {
            if (self.equals(s.path("id").asText())) continue;
            try {
                String id = s.path("id").asText(); var chunks = new HashSet<Integer>(); s.path("chunks").forEach(n -> chunks.add(n.asInt()));
                String ticket = context.tracker.call("POST", "/api/tickets", Map.of("fileId", fileId, "sourceId", id)).path("ticket").asText();
                result.add(new Source(id, s.path("name").asText(), s.path("owner").asText(), s.path("host").asText(), s.path("port").asInt(), chunks, ticket));
            } catch (TrackerFailure ignored) { /* A source may go offline while tickets are issued. */ }
        }
        return result;
    }
}
