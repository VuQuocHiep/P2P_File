package vn.edu.p2p.client.service;

import vn.edu.p2p.client.util.AtomicFiles;
import vn.edu.p2p.common.concurrent.NamedThreads;
import com.fasterxml.jackson.databind.*;
import vn.edu.p2p.common.model.Manifest;
import vn.edu.p2p.client.model.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.edu.p2p.client.dto.response.*;
import vn.edu.p2p.client.mapper.JobMapper;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@Service
public class PeerDownloadService {
    private final PeerContext context;
    private final JobMapper jobMapper;

    public PeerDownloadService(PeerContext context, JobMapper jobMapper) {
        this.context = context;
        this.jobMapper = jobMapper;
    }

    public DownloadResponse download(String fileId) throws Exception {
        context.peerSessionService.validId(fileId);
        context.peerSessionService.ready();
        JsonNode files = context.peerCatalogService.catalogData("");
        Manifest manifest = null;
        for (JsonNode file : files) {
            if (file.path("manifest").path("id").asText().equals(fileId))
                manifest = context.json.treeToValue(file.path("manifest"), Manifest.class);
        }
        if (manifest == null)
            throw new IllegalArgumentException("File chưa được duyệt hoặc đã bị ẩn.");
        manifest.validate();
        Job job;
        synchronized (context.storage.monitor()) {
            if (context.storage.isDeleted(fileId))
                throw new IllegalArgumentException("File đã được quản trị viên xóa toàn mạng.");
            if (context.benchmarking.get())
                throw new IllegalArgumentException("Đợi phép đo kết thúc trước khi tải file.");
            Entry entry = context.library.findByFileId(fileId);
            if (entry != null && entry.complete)
                throw new IllegalArgumentException("File đã có trong thư viện.");
            if (entry == null) {
                entry = new Entry();
                entry.manifest = manifest;
                entry.catalogStatus = "APPROVED";
                context.library.save(fileId, entry);
            }
            job = context.jobs.findByFileId(fileId);
            if (job == null) {
                job = new Job();
                job.id = UUID.randomUUID().toString();
                job.fileId = fileId;
                job.created = System.currentTimeMillis();
                context.jobs.save(job);
            }
            context.storage.saveCheckpoint();
            resume(job.id);
        }
        return jobMapper.toDownloadResponse(job);
    }

    public void resume(String jobId) {
        context.peerSessionService.ready();
        synchronized (context.storage.monitor()) {
            if (context.benchmarking.get())
                throw new IllegalArgumentException("Đợi phép đo kết thúc trước khi tải file.");
            Job job = context.jobs.findById(jobId);
            if (job == null || "COMPLETED".equals(job.status))
                throw new IllegalArgumentException("Phiên tải không tồn tại hoặc đã hoàn tất.");
            if (context.running.contains(jobId)) {
                if ("PAUSED".equals(job.status))
                    throw new IllegalArgumentException(
                            "Đang dừng các kết nối. Thử lại sau vài giây.");
                return;
            }
            if (context.running.size() >= 2)
                throw new IllegalArgumentException("Tối đa 2 file tải đồng thời.");
            job.status = "DOWNLOADING";
            job.error = "";
            context.running.add(jobId);
            context.speedBytes.put(jobId, new AtomicLong());
            context.speedStart.put(jobId, System.currentTimeMillis());
            context.events.append(
                    "Bắt đầu / tiếp tục tải "
                            + context.library.findByFileId(job.fileId).manifest.name());
            context.storage.saveCheckpoint();
        }
        context.coordinators.submit(() -> transfer(jobId));
    }

    public void pause(String jobId) {
        synchronized (context.storage.monitor()) {
            Job job = context.jobs.findById(jobId);
            if (job == null) throw new IllegalArgumentException("Không có phiên tải.");
            if (!"COMPLETED".equals(job.status)) {
                job.status = "PAUSED";
                context.events.append("Tạm dừng phiên " + jobId);
                context.storage.saveCheckpoint();
            }
        }
    }

    boolean active(String jobId) {
        synchronized (context.storage.monitor()) {
            Job job = context.jobs.findById(jobId);
            return !context.closing
                    && !context.tracker.token().isBlank()
                    && job != null
                    && !Set.of("PAUSED", "COMPLETED", "ERROR").contains(job.status);
        }
    }

    void transfer(String jobId) {
        ThreadPoolExecutor workers =
                NamedThreads.boundedPool("chunk-download-" + jobId.substring(0, 8), 4, 16);
        context.downloadPools.put(jobId, workers);
        try {
            Job job;
            Entry entry;
            synchronized (context.storage.monitor()) {
                job = context.jobs.findById(jobId);
                if (job == null) return;
                entry = context.library.findByFileId(job.fileId);
            }
            while (active(jobId)) {
                List<Integer> missing;
                synchronized (context.storage.monitor()) {
                    missing = new ArrayList<>();
                    for (int i = 0; i < entry.manifest.hashes().size(); i++)
                        if (!entry.chunks.contains(i)) missing.add(i);
                }
                if (missing.isEmpty()) {
                    assemble(jobId, entry);
                    break;
                }
                List<Source> sources;
                try {
                    sources = context.peerSourceService.available(job.fileId);
                } catch (Exception e) {
                    waiting(jobId, e.getMessage());
                    Thread.sleep(2000);
                    continue;
                }
                if (sources.isEmpty()) {
                    waiting(jobId, "Chưa có nguồn trực tuyến. Tự thử lại sau 2 giây.");
                    Thread.sleep(2000);
                    continue;
                }
                // Download rare chunks first. Only one worker writes a given index in each batch.
                missing.removeIf(i -> sources.stream().noneMatch(s -> s.chunks().contains(i)));
                if (missing.isEmpty()) {
                    waiting(jobId, "Nguồn hiện có chưa giữ phần còn thiếu. Đang chờ thêm nguồn.");
                    Thread.sleep(2000);
                    continue;
                }
                missing.sort(
                        Comparator.comparingLong(
                                i -> sources.stream().filter(s -> s.chunks().contains(i)).count()));
                var futures = new ArrayList<Future<?>>();
                AtomicInteger progress = new AtomicInteger();
                Map<String, AtomicInteger> failures = new ConcurrentHashMap<>();
                for (int n = 0; n < Math.min(16, missing.size()); n++) {
                    int index = missing.get(n), offset = n;
                    futures.add(
                            workers.submit(
                                    () -> {
                                        for (int attempt = 0;
                                                attempt < sources.size() && active(jobId);
                                                attempt++) {
                                            Source source =
                                                    sources.get(
                                                            (offset + attempt) % sources.size());
                                            if (!source.chunks().contains(index)
                                                    || failures.computeIfAbsent(
                                                                            source.id(),
                                                                            k ->
                                                                                    new AtomicInteger())
                                                                    .get()
                                                            >= 2) continue;
                                            try {
                                                byte[] bytes =
                                                        context.peerSourceService.fetchObserved(
                                                                source,
                                                                entry.manifest,
                                                                index,
                                                                "Tải file",
                                                                () -> !active(jobId));
                                                synchronized (context.storage.monitor()) {
                                                    if (!active(jobId)) return;
                                                    Path part =
                                                            context.storage.chunk(
                                                                    job.fileId, index);
                                                    Files.createDirectories(part.getParent());
                                                    Path tmp = part.resolveSibling(index + ".tmp");
                                                    Files.write(tmp, bytes);
                                                    AtomicFiles.move(tmp, part);
                                                    entry.chunks.add(index);
                                                    job.downloaded = completedBytes(entry);
                                                    job.chunkSources.put(
                                                            index,
                                                            new ChunkSource(
                                                                    source.id(),
                                                                    source.name(),
                                                                    source.owner(),
                                                                    source.host(),
                                                                    source.port(),
                                                                    System.currentTimeMillis()));
                                                    job.sourceBytes.merge(
                                                            source.name()
                                                                    + " · "
                                                                    + source.id().substring(0, 8),
                                                            (long) bytes.length,
                                                            Long::sum);
                                                    job.status = "DOWNLOADING";
                                                    job.error = "";
                                                    context.storage.saveCheckpoint();
                                                }
                                                context.speedBytes
                                                        .get(jobId)
                                                        .addAndGet(bytes.length);
                                                progress.incrementAndGet();
                                                return;
                                            } catch (Exception e) {
                                                if (!active(jobId)) return;
                                                if (failures.get(source.id()).getAndIncrement()
                                                        == 0)
                                                    synchronized (context.storage.monitor()) {
                                                        context.events.append(
                                                                "Nguồn "
                                                                        + source.name()
                                                                        + " lỗi: "
                                                                        + e.getMessage()
                                                                        + ". Thử nguồn khác.");
                                                    }
                                            }
                                        }
                                    }));
                }
                for (Future<?> f : futures) f.get();
                if (progress.get() == 0 && active(jobId)) {
                    waiting(jobId, "Chưa tải được phần thiếu. Đang tìm lại nguồn.");
                    Thread.sleep(2000);
                }
            }
        } catch (Exception e) {
            synchronized (context.storage.monitor()) {
                Job j = context.jobs.findById(jobId);
                if (j != null && !"PAUSED".equals(j.status)) {
                    j.status = "ERROR";
                    j.error = Objects.toString(e.getMessage(), e.getClass().getSimpleName());
                }
                context.storage.saveCheckpoint();
            }
        } finally {
            workers.shutdownNow();
            context.downloadPools.remove(jobId);
            context.running.remove(jobId);
            report(jobId);
        }
    }

    void waiting(String jobId, String message) {
        synchronized (context.storage.monitor()) {
            Job j = context.jobs.findById(jobId);
            if (active(jobId)) {
                j.status = "WAITING";
                j.error = message;
                context.storage.saveCheckpoint();
            }
        }
    }

    void assemble(String jobId, Entry entry) throws Exception {
        Path temporary = context.storage.root().resolve(entry.manifest.id() + ".assembling");
        try {
            try (var out = Files.newOutputStream(temporary)) {
                for (int i = 0; i < entry.manifest.hashes().size(); i++) {
                    if (!active(jobId)) return;
                    Files.copy(context.storage.chunk(entry.manifest.id(), i), out);
                }
            }
            if (!Manifest.hash(temporary).equals(entry.manifest.id()))
                throw new IOException(
                        "SHA-256 toàn file không khớp. Các chunk cần được kiểm tra lại.");
            synchronized (context.storage.monitor()) {
                if (!active(jobId)) return;
                AtomicFiles.move(temporary, context.storage.blob(entry.manifest.id()));
                entry.complete = true;
                Job job = context.jobs.findById(jobId);
                job.status = "COMPLETED";
                job.finished = System.currentTimeMillis();
                job.error = "";
                context.events.append("SHA-256 hợp lệ. Hoàn tất " + entry.manifest.name());
                context.storage.saveCheckpoint();
            }
            try {
                context.peerLibraryService.advertise(entry.manifest.id());
            } catch (Exception e) {
                context.connection = e.getMessage();
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    void report(String jobId) {
        Map<String, Object> body;
        synchronized (context.storage.monitor()) {
            Job j = context.jobs.findById(jobId);
            if (j == null) return;
            body =
                    Map.of(
                            "id",
                            j.id,
                            "peerId",
                            context.profile.getPeerId(),
                            "fileId",
                            j.fileId,
                            "status",
                            j.status,
                            "bytes",
                            completedBytes(context.library.findByFileId(j.fileId)));
        }
        try {
            context.tracker.call("POST", "/api/transfers", body);
        } catch (Exception ignored) {
            /* Next heartbeat retries. */
        }
    }

    long completedBytes(Entry entry) {
        if (entry.complete) return entry.manifest.size();
        return entry.chunks.stream().mapToLong(entry.manifest::length).sum();
    }

    public Map<Integer, ChunkSource> chunkHistory(String jobId) {
        synchronized (context.storage.monitor()) {
            Job job = context.jobs.findById(jobId);
            if (job == null) throw new IllegalArgumentException("Phiên tải không tồn tại.");
            return new TreeMap<>(job.chunkSources);
        }
    }
}
