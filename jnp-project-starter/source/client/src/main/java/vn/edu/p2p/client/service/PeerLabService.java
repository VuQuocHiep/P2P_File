package vn.edu.p2p.client.service;

import vn.edu.p2p.common.concurrent.NamedThreads;
import com.fasterxml.jackson.databind.*;
import vn.edu.p2p.common.model.Manifest;
import vn.edu.p2p.client.model.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

final class PeerLabService {
    private final PeerContext context;
    PeerLabService(PeerContext context) { this.context = context; }
    void sampleNetwork() {
        synchronized (context.samples) {
        long now = System.nanoTime(), rx = ((Number) context.tcpClient.metrics().get("receivedBytes")).longValue(), tx = context.tcpServer.sentBytes();
        double seconds = Math.max(0.001, (now - context.sampleAt) / 1_000_000_000.0);
        context.sourceReceived.forEach((id, counter) -> {
            long total = counter.get(), previous = context.sourcePrevious.getOrDefault(id, 0L);
            context.sourceStats.put(id, Map.of("bytes", total, "chunks", context.sourceChunks.getOrDefault(id, new AtomicLong()).get(),
                    "speed", (long) ((total - previous) / seconds)));
            context.sourcePrevious.put(id, total);
        });
        context.samples.add(Map.of("at", System.currentTimeMillis(), "receive", (long) ((rx - context.sampleRx) / seconds),
                "send", (long) ((tx - context.sampleTx) / seconds)));
        if (context.samples.size() > 120) context.samples.remove(0);
        context.sampleAt = now; context.sampleRx = rx; context.sampleTx = tx;
        context.recentLinks.values().removeIf(link -> System.currentTimeMillis() - ((Number) link.get("at")).longValue() > 30000);
        }
    }
    List<Map<String, Object>> networkSamples() { synchronized (context.samples) { return new ArrayList<>(context.samples); } }
    void simulateFault(String mode, int delayMs, int seconds) {
        context.peerSessionService.ready(); context.tcpServer.fault(mode, delayMs, seconds);
        synchronized (context.store) { context.store.event("Mô phỏng nguồn: " + mode + " · " + seconds + " giây"); context.store.save(); }
    }
    Object startBenchmark(String fileId) throws Exception {
        context.peerSessionService.validId(fileId); context.peerSessionService.ready();
        synchronized (context.store) {
            if (!context.running.isEmpty() || !context.benchmarking.compareAndSet(false, true))
                throw new IllegalArgumentException("Chỉ đo một lần và cần tạm dừng các phiên tải trước.");
        }
        try {
            Manifest manifest = null;
            for (JsonNode file : (JsonNode) context.peerSessionService.catalog(""))
                if (fileId.equals(file.path("manifest").path("id").asText())) manifest = context.json.treeToValue(file.path("manifest"), Manifest.class);
            if (manifest == null) throw new IllegalArgumentException("File chưa được duyệt.");
            manifest.validate();
            Manifest selected = manifest;
            context.benchmark = Map.of("status", "RUNNING", "name", selected.name(), "phase", "Chuẩn bị nguồn", "results", List.of());
            context.labWorker.submit(() -> runBenchmark(selected));
            return Map.of("message", "Đang đo hai lượt TCP trên cùng tập chunk (tối đa 16 MiB mỗi lượt).");
        } catch (Exception e) { context.benchmarking.set(false); throw e; }
    }
    void runBenchmark(Manifest manifest) {
        var results = new ArrayList<Map<String, Object>>();
        try {
            List<Source> initial = context.peerSourceService.available(manifest.id());
            int count = Math.min(64, manifest.hashes().size());
            if (count < 2) throw new IOException("Chọn file có ít nhất 2 chunk để so sánh nhiều nguồn.");
            List<Source> complete = initial.stream().filter(s -> {
                for (int i = 0; i < count; i++) if (!s.chunks().contains(i)) return false;
                return true;
            }).toList();
            if (complete.size() < 2) throw new IOException("Cần ít nhất 2 nguồn khác peer này có đủ các chunk đo (tối đa 64 chunk đầu).");
            List<String> ids = complete.stream().map(Source::id).toList();
            for (boolean multi : List.of(false, true)) {
                String phase = multi ? "Nhiều nguồn" : "Một nguồn";
                context.benchmark = Map.of("status", "RUNNING", "name", manifest.name(), "phase", phase, "results", List.copyOf(results));
                List<Source> fresh = context.peerSourceService.available(manifest.id()); // renew short-lived tickets before each pass
                List<Source> sources = new ArrayList<>();
                for (String id : ids) fresh.stream().filter(s -> s.id().equals(id)).findFirst().ifPresent(sources::add);
                if (sources.size() != ids.size()) throw new IOException("Nguồn thay đổi giữa hai lượt. Hãy đo lại.");
                if (!multi) sources = List.of(sources.get(0));
                final List<Source> chosen = List.copyOf(sources);
                var pool = Executors.newFixedThreadPool(4, NamedThreads.factory("benchmark-chunk"));
                long started = System.nanoTime();
                var sourceBytes = new ConcurrentHashMap<String, Long>();
                var futures = new ArrayList<Future<?>>();
                try {
                    for (int i = 0; i < count; i++) {
                        int index = i;
                        futures.add(pool.submit(() -> {
                            if (!context.peerSessionService.loggedIn() || context.closing) throw new IOException("Phiên đo đã dừng.");
                            Source source = chosen.get(index % chosen.size());
                            byte[] bytes = context.peerSourceService.fetchObserved(source, manifest, index, "Đo " + phase);
                            sourceBytes.merge(source.name() + " · " + source.id().substring(0, 8), (long) bytes.length, Long::sum);
                            return null;
                        }));
                    }
                    for (Future<?> future : futures) future.get();
                } finally { pool.shutdownNow(); pool.awaitTermination(10, TimeUnit.SECONDS); }
                long nanos = Math.max(1, System.nanoTime() - started), total = sourceBytes.values().stream().mapToLong(Long::longValue).sum();
                results.add(Map.of("mode", phase, "bytes", total, "durationMs", nanos / 1_000_000.0,
                        "speed", total * 1_000_000_000.0 / nanos, "sourceBytes", new TreeMap<>(sourceBytes), "workers", 4, "chunks", count));
            }
            context.benchmark = Map.of("status", "COMPLETED", "name", manifest.name(), "results", List.copyOf(results));
        } catch (Exception e) {
            Throwable cause = e instanceof ExecutionException ? e.getCause() : e;
            context.benchmark = Map.of("status", "ERROR", "name", manifest.name(), "error", Objects.toString(cause.getMessage(), "Phép đo bị gián đoạn"), "results", List.copyOf(results));
        } finally { context.benchmarking.set(false); }
    }
}
