package vn.edu.p2p.client.service;

import vn.edu.p2p.client.repository.PeerStore;
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

final class PeerDownloadService {
    private final PeerContext context;
    PeerDownloadService(PeerContext context) { this.context = context; }
    Object download(String fileId) throws Exception {
        context.peerSessionService.validId(fileId); context.peerSessionService.ready();
        JsonNode files = (JsonNode) context.peerSessionService.catalog(""); Manifest manifest = null;
        for (JsonNode file : files) {
            if (file.path("manifest").path("id").asText().equals(fileId)) manifest = context.json.treeToValue(file.path("manifest"), Manifest.class);
        }
        if (manifest == null) throw new IllegalArgumentException("File chưa được duyệt hoặc đã bị ẩn."); manifest.validate();
        Job job;
        synchronized (context.store) {
            if (context.benchmarking.get()) throw new IllegalArgumentException("Đợi phép đo kết thúc trước khi tải file.");
            Entry entry = context.store.snapshot().library.get(fileId);
            if (entry != null && entry.complete) throw new IllegalArgumentException("File đã có trong thư viện.");
            if (entry == null) { entry = new Entry(); entry.manifest = manifest; entry.catalogStatus = "APPROVED"; context.store.snapshot().library.put(fileId, entry); }
            job = context.store.snapshot().jobs.values().stream().filter(j -> j.fileId.equals(fileId)).findFirst().orElse(null);
            if (job == null) { job = new Job(); job.id = UUID.randomUUID().toString(); job.fileId = fileId; job.created = System.currentTimeMillis(); context.store.snapshot().jobs.put(job.id, job); }
            context.store.save();
            resume(job.id);
        }
        return Map.of("id", job.id);
    }
    void resume(String jobId) {
        context.peerSessionService.ready();
        synchronized (context.store) {
            if (context.benchmarking.get()) throw new IllegalArgumentException("Đợi phép đo kết thúc trước khi tải file.");
            Job job = context.store.snapshot().jobs.get(jobId);
            if (job == null || "COMPLETED".equals(job.status)) throw new IllegalArgumentException("Phiên tải không tồn tại hoặc đã hoàn tất.");
            if (context.running.contains(jobId)) { if ("PAUSED".equals(job.status)) throw new IllegalArgumentException("Đang dừng các kết nối. Thử lại sau vài giây."); return; }
            if (context.running.size() >= 2) throw new IllegalArgumentException("Tối đa 2 file tải đồng thời.");
            job.status = "DOWNLOADING"; job.error = ""; context.running.add(jobId);
            context.speedBytes.put(jobId, new AtomicLong()); context.speedStart.put(jobId, System.currentTimeMillis());
            context.store.event("Bắt đầu / tiếp tục tải " + context.store.snapshot().library.get(job.fileId).manifest.name()); context.store.save();
        }
        context.coordinators.submit(() -> transfer(jobId));
    }
    void pause(String jobId) {
        synchronized (context.store) {
            Job job = context.store.snapshot().jobs.get(jobId);
            if (job == null) throw new IllegalArgumentException("Không có phiên tải.");
            if (!"COMPLETED".equals(job.status)) { job.status = "PAUSED"; context.store.event("Tạm dừng phiên " + jobId); context.store.save(); }
        }
    }
    boolean active(String jobId) {
        synchronized (context.store) {
            Job job = context.store.snapshot().jobs.get(jobId);
            return !context.closing && !context.tracker.token().isBlank() && job != null && !Set.of("PAUSED", "COMPLETED", "ERROR").contains(job.status);
        }
    }
    void transfer(String jobId) {
        ThreadPoolExecutor workers = NamedThreads.boundedPool("chunk-download-" + jobId.substring(0, 8), 4, 16);
        context.downloadPools.put(jobId, workers);
        try {
            Job job; Entry entry;
            synchronized (context.store) { job = context.store.snapshot().jobs.get(jobId); entry = context.store.snapshot().library.get(job.fileId); }
            while (active(jobId)) {
                List<Integer> missing;
                synchronized (context.store) {
                    missing = new ArrayList<>();
                    for (int i = 0; i < entry.manifest.hashes().size(); i++) if (!entry.chunks.contains(i)) missing.add(i);
                }
                if (missing.isEmpty()) { assemble(jobId, entry); break; }
                List<Source> sources;
                try { sources = context.peerSourceService.available(job.fileId); }
                catch (Exception e) { waiting(jobId, e.getMessage()); Thread.sleep(2000); continue; }
                if (sources.isEmpty()) { waiting(jobId, "Chưa có nguồn trực tuyến. Tự thử lại sau 2 giây."); Thread.sleep(2000); continue; }
                // Download rare chunks first. Only one worker writes a given index in each batch.
                missing.removeIf(i -> sources.stream().noneMatch(s -> s.chunks().contains(i)));
                if (missing.isEmpty()) { waiting(jobId, "Nguồn hiện có chưa giữ phần còn thiếu. Đang chờ thêm nguồn."); Thread.sleep(2000); continue; }
                missing.sort(Comparator.comparingLong(i -> sources.stream().filter(s -> s.chunks().contains(i)).count()));
                var futures = new ArrayList<Future<?>>(); AtomicInteger progress = new AtomicInteger();
                Map<String, AtomicInteger> failures = new ConcurrentHashMap<>();
                for (int n = 0; n < Math.min(16, missing.size()); n++) {
                    int index = missing.get(n), offset = n;
                    futures.add(workers.submit(() -> {
                        for (int attempt = 0; attempt < sources.size() && active(jobId); attempt++) {
                            Source source = sources.get((offset + attempt) % sources.size());
                            if (!source.chunks().contains(index) || failures.computeIfAbsent(source.id(), k -> new AtomicInteger()).get() >= 2) continue;
                            try {
                                byte[] bytes = context.peerSourceService.fetchObserved(source, entry.manifest, index, "Tải file");
                                synchronized (context.store) {
                                    if (!active(jobId)) return;
                                    Path part = context.store.chunk(job.fileId, index); Files.createDirectories(part.getParent());
                                    Path tmp = part.resolveSibling(index + ".tmp"); Files.write(tmp, bytes); PeerStore.atomicMove(tmp, part);
                                    entry.chunks.add(index); job.downloaded = completedBytes(entry);
                                    job.chunkSources.put(index, new ChunkSource(source.id(), source.name(), source.owner(),
                                            source.host(), source.port(), System.currentTimeMillis()));
                                    job.sourceBytes.merge(source.name() + " · " + source.id().substring(0, 8), (long) bytes.length, Long::sum);
                                    job.status = "DOWNLOADING"; job.error = ""; context.store.save();
                                }
                                context.speedBytes.get(jobId).addAndGet(bytes.length); progress.incrementAndGet(); return;
                            } catch (Exception e) {
                                if (failures.get(source.id()).getAndIncrement() == 0) synchronized (context.store) {
                                    context.store.event("Nguồn " + source.name() + " lỗi: " + e.getMessage() + ". Thử nguồn khác.");
                                }
                            }
                        }
                    }));
                }
                for (Future<?> f : futures) f.get();
                if (progress.get() == 0 && active(jobId)) { waiting(jobId, "Chưa tải được phần thiếu. Đang tìm lại nguồn."); Thread.sleep(2000); }
            }
        } catch (Exception e) {
            synchronized (context.store) { Job j = context.store.snapshot().jobs.get(jobId); if (!"PAUSED".equals(j.status)) { j.status = "ERROR"; j.error = Objects.toString(e.getMessage(), e.getClass().getSimpleName()); } context.store.save(); }
        } finally { workers.shutdownNow(); context.downloadPools.remove(jobId); context.running.remove(jobId); report(jobId); }
    }
    void waiting(String jobId, String message) {
        synchronized (context.store) {
            Job j = context.store.snapshot().jobs.get(jobId); if (active(jobId)) { j.status = "WAITING"; j.error = message; context.store.save(); }
        }
    }
    void assemble(String jobId, Entry entry) throws Exception {
        Path temporary = context.store.root().resolve(entry.manifest.id() + ".assembling");
        try {
            try (var out = Files.newOutputStream(temporary)) {
                for (int i = 0; i < entry.manifest.hashes().size(); i++) {
                    if (!active(jobId)) return;
                    Files.copy(context.store.chunk(entry.manifest.id(), i), out);
                }
            }
            if (!Manifest.hash(temporary).equals(entry.manifest.id())) throw new IOException("SHA-256 toàn file không khớp. Các chunk cần được kiểm tra lại.");
            synchronized (context.store) {
                if (!active(jobId)) return;
                PeerStore.atomicMove(temporary, context.store.blob(entry.manifest.id())); entry.complete = true;
                Job job = context.store.snapshot().jobs.get(jobId); job.status = "COMPLETED"; job.finished = System.currentTimeMillis(); job.error = "";
                context.store.event("SHA-256 hợp lệ. Hoàn tất " + entry.manifest.name()); context.store.save();
            }
            try { context.peerLibraryService.advertise(entry.manifest.id()); } catch (Exception e) { context.connection = e.getMessage(); }
        } finally { Files.deleteIfExists(temporary); }
    }
    void report(String jobId) {
        Map<String, Object> body;
        synchronized (context.store) {
            Job j = context.store.snapshot().jobs.get(jobId); if (j == null) return;
            body = Map.of("id", j.id, "peerId", context.store.snapshot().peerId, "fileId", j.fileId, "status", j.status,
                    "bytes", completedBytes(context.store.snapshot().library.get(j.fileId)));
        }
        try { context.tracker.call("POST", "/api/transfers", body); } catch (Exception ignored) { /* Next heartbeat retries. */ }
    }
    long completedBytes(Entry entry) {
        if (entry.complete) return entry.manifest.size();
        return entry.chunks.stream().mapToLong(entry.manifest::length).sum();
    }
    Map<Integer, ChunkSource> chunkHistory(String jobId) {
        synchronized (context.store) {
            Job job = context.store.snapshot().jobs.get(jobId);
            if (job == null) throw new IllegalArgumentException("Phiên tải không tồn tại.");
            return new TreeMap<>(job.chunkSources);
        }
    }
}
