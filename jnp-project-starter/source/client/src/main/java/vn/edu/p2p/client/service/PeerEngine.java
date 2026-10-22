package vn.edu.p2p.client.service;

import vn.edu.p2p.client.repository.PeerStore;
import vn.edu.p2p.client.network.TrackerGateway;
import vn.edu.p2p.client.network.TcpChunkServer;
import vn.edu.p2p.client.network.TcpChunkClient;
import vn.edu.p2p.common.concurrent.NamedThreads;
import com.fasterxml.jackson.databind.*;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.p2p.common.model.Manifest;
import vn.edu.p2p.common.protocol.Wire;
import vn.edu.p2p.client.model.PeerModels.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** TCP seeding, concurrent verified downloads, heartbeat, checkpointing and failover. */
@Service
public class PeerEngine {
    private final PeerStore store;
    private final TrackerGateway tracker;
    private final ObjectMapper json;
    private final int tcpPort;
    private final TcpChunkServer tcpServer;
    private final TcpChunkClient tcpClient = new TcpChunkClient();
    private final ExecutorService coordinators = Executors.newFixedThreadPool(2, NamedThreads.factory("download-coordinator"));
    private final ScheduledExecutorService pulse = Executors.newSingleThreadScheduledExecutor(NamedThreads.factory("peer-heartbeat"));
    private final ScheduledExecutorService telemetry = Executors.newSingleThreadScheduledExecutor(NamedThreads.factory("peer-metrics"));
    private final Set<String> running = ConcurrentHashMap.newKeySet();
    private final Map<String, ThreadPoolExecutor> downloadPools = new ConcurrentHashMap<>();
    private volatile JsonNode catalogSnapshot;
    private volatile JsonNode user;
    private volatile String connection = "Chưa đăng nhập", peerStatus = "PENDING";
    private volatile boolean closing;
    private final Map<String, AtomicLong> speedBytes = new ConcurrentHashMap<>();
    private final Map<String, Long> speedStart = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> links = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> recentLinks = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> sourceReceived = new ConcurrentHashMap<>(), sourceChunks = new ConcurrentHashMap<>();
    private final Map<String, Long> sourcePrevious = new HashMap<>();
    private final Map<String, Map<String, Long>> sourceStats = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> samples = new ArrayList<>();
    private long sampleAt = System.nanoTime(), sampleRx, sampleTx;
    private final ExecutorService labWorker = Executors.newSingleThreadExecutor(NamedThreads.factory("demo-benchmark"));
    private final AtomicBoolean benchmarking = new AtomicBoolean();
    private volatile Map<String, Object> benchmark = Map.of("status", "IDLE");

    public PeerEngine(PeerStore store, TrackerGateway tracker, ObjectMapper json,
            @Value("${peer.tcp-port}") int tcpPort, @Value("${peer.upload-delay-ms}") int delay) throws IOException {
        this.store = store; this.tracker = tracker; this.json = json; this.tcpPort = tcpPort;
        catalogSnapshot = json.createArrayNode();
        tcpServer = new TcpChunkServer(tcpPort, delay, this::readChunk);
        pulse.scheduleWithFixedDelay(this::heartbeat, 2, 5, TimeUnit.SECONDS);
        telemetry.scheduleWithFixedDelay(this::sampleNetwork, 1, 1, TimeUnit.SECONDS);
    }
    public Object register(Object input) throws Exception { return tracker.call("POST", "/api/auth/register", input); }
    public boolean loggedIn() { return user != null && !tracker.token().isBlank(); }
    public synchronized Object login(String username, String password, String name, String host) throws Exception {
        synchronized (store) {
            if (!store.snapshot().owner.isBlank() && !store.snapshot().owner.equals(username))
                throw new IllegalArgumentException("Profile này thuộc " + store.snapshot().owner + ". Dùng PEER_DATA khác cho tài khoản khác.");
        }
        JsonNode result = tracker.call("POST", "/api/auth/login", Map.of("username", username, "password", password));
        tracker.token(result.path("token").asText()); user = result.path("user");
        synchronized (store) {
            store.snapshot().owner = username; store.snapshot().name = name; store.snapshot().host = host; store.save();
        }
        if ("APPROVED".equals(user.path("status").asText())) connectPeer();
        else connection = "Tài khoản đang chờ duyệt";
        return status();
    }
    private void connectPeer() throws Exception {
        Map<String, Object> body;
        synchronized (store) {
            body = Map.of("id", store.snapshot().peerId, "name", store.snapshot().name, "host", store.snapshot().host, "port", tcpPort);
        }
        JsonNode result = tracker.call("POST", "/api/peers", body);
        synchronized (store) { store.snapshot().peerId = result.path("id").asText(); store.event("Đăng ký peer " + store.snapshot().name); store.save(); }
        peerStatus = result.path("status").asText(); connection = "Đã kết nối tracker";
    }
    public void logout() {
        tcpServer.fault("NORMAL", 0, 1);
        synchronized (store) {
            store.snapshot().jobs.values().stream().filter(j -> !"COMPLETED".equals(j.status)).forEach(j -> j.status = "PAUSED"); store.save();
        }
        try { tracker.call("POST", "/api/auth/logout", Map.of()); } catch (Exception ignored) {}
        tracker.token(""); user = null; catalogSnapshot = json.createArrayNode(); connection = "Đã đăng xuất";
    }
    private void heartbeat() {
        if (tracker.token().isBlank() || closing) return;
        try {
            user = tracker.call("GET", "/api/auth/me", null);
            if (!"APPROVED".equals(user.path("status").asText())) { connection = "Tài khoản đang chờ duyệt"; return; }
            String id;
            synchronized (store) { id = store.snapshot().peerId; }
            if (id.isBlank()) { connectPeer(); synchronized (store) { id = store.snapshot().peerId; } }
            JsonNode result = tracker.call("POST", "/api/peers/heartbeat", Map.of("peerId", id));
            peerStatus = result.path("status").asText(); connection = "Đã kết nối tracker";
            catalogSnapshot = tracker.call("GET", "/api/files", null);
            if (!"APPROVED".equals(peerStatus)) return;
            List<String> files; List<String> jobs;
            synchronized (store) { files = new ArrayList<>(store.snapshot().library.keySet()); jobs = new ArrayList<>(store.snapshot().jobs.keySet()); }
            for (String fileId : files) advertise(fileId);
            for (String jobId : jobs) report(jobId);
        } catch (Exception e) { connection = e.getMessage(); }
    }
    private void advertise(String fileId) throws Exception {
        Map<String, Object> body;
        synchronized (store) {
            Entry entry = store.snapshot().library.get(fileId);
            if (entry == null || !entry.sharing) return;
            body = Map.of("peerId", store.snapshot().peerId, "manifest", entry.manifest, "chunks", new ArrayList<>(entry.chunks));
        }
        var result = tracker.call("POST", "/api/files", body);
        synchronized (store) { Entry e = store.snapshot().library.get(fileId); if (e != null) { e.catalogStatus = result.path("status").asText(); store.save(); } }
    }
    public Object catalog(String query) throws Exception {
        return tracker.call("GET", "/api/files?q=" + URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8), null);
    }
    public Object sources(String id) throws Exception { validId(id); return tracker.call("GET", "/api/files/" + id + "/sources", null); }
    private static void validId(String id) {
        if (id == null || !id.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("File ID không hợp lệ.");
    }
    private void ready() {
        if (user == null || !"APPROVED".equals(user.path("status").asText()) || !"APPROVED".equals(peerStatus))
            throw new IllegalArgumentException("Tài khoản và peer cần được quản trị viên duyệt trước.");
    }
    public Object upload(MultipartFile upload) throws Exception {
        ready(); if (upload.isEmpty()) throw new IllegalArgumentException("Hãy chọn file có dữ liệu.");
        String name = Optional.ofNullable(upload.getOriginalFilename()).orElse("file.bin").replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        Path temporary = Files.createTempFile(store.root(), "upload-", ".tmp");
        try {
            upload.transferTo(temporary); Manifest manifest = Manifest.create(temporary, name);
            synchronized (store) {
                Entry previous = store.snapshot().library.get(manifest.id());
                if (previous == null) { previous = new Entry(); previous.manifest = manifest; }
                if (store.snapshot().jobs.values().stream().anyMatch(j -> j.fileId.equals(manifest.id()) && running.contains(j.id)))
                    throw new IllegalArgumentException("File đang tải. Hãy tạm dừng trước khi thêm file này.");
                PeerStore.atomicMove(temporary, store.blob(manifest.id())); previous.complete = true; previous.sharing = true;
                previous.chunks.clear(); for (int i = 0; i < manifest.hashes().size(); i++) previous.chunks.add(i);
                store.snapshot().library.put(manifest.id(), previous);
                store.event("Chia sẻ " + manifest.name()); store.save();
            }
            advertise(manifest.id()); return Map.of("message", "Đã chia sẻ. File mới cần quản trị viên duyệt.", "manifest", manifest);
        } finally { Files.deleteIfExists(temporary); }
    }
    public void sharing(String fileId, boolean enabled) throws Exception {
        validId(fileId); ready(); String peerId;
        synchronized (store) {
            Entry entry = store.snapshot().library.get(fileId); if (entry == null) throw new IllegalArgumentException("File không tồn tại.");
            entry.sharing = enabled; peerId = store.snapshot().peerId; store.save();
        }
        if (enabled) advertise(fileId); else tracker.call("DELETE", "/api/files/" + fileId + "/holders/" + peerId, null);
    }
    public Object download(String fileId) throws Exception {
        validId(fileId); ready();
        JsonNode files = (JsonNode) catalog(""); Manifest manifest = null;
        for (JsonNode file : files) {
            if (file.path("manifest").path("id").asText().equals(fileId)) manifest = json.treeToValue(file.path("manifest"), Manifest.class);
        }
        if (manifest == null) throw new IllegalArgumentException("File chưa được duyệt hoặc đã bị ẩn."); manifest.validate();
        Job job;
        synchronized (store) {
            if (benchmarking.get()) throw new IllegalArgumentException("Đợi phép đo kết thúc trước khi tải file.");
            Entry entry = store.snapshot().library.get(fileId);
            if (entry != null && entry.complete) throw new IllegalArgumentException("File đã có trong thư viện.");
            if (entry == null) { entry = new Entry(); entry.manifest = manifest; entry.catalogStatus = "APPROVED"; store.snapshot().library.put(fileId, entry); }
            job = store.snapshot().jobs.values().stream().filter(j -> j.fileId.equals(fileId)).findFirst().orElse(null);
            if (job == null) { job = new Job(); job.id = UUID.randomUUID().toString(); job.fileId = fileId; job.created = System.currentTimeMillis(); store.snapshot().jobs.put(job.id, job); }
            store.save();
            resume(job.id);
        }
        return Map.of("id", job.id);
    }
    public void resume(String jobId) {
        ready();
        synchronized (store) {
            if (benchmarking.get()) throw new IllegalArgumentException("Đợi phép đo kết thúc trước khi tải file.");
            Job job = store.snapshot().jobs.get(jobId);
            if (job == null || "COMPLETED".equals(job.status)) throw new IllegalArgumentException("Phiên tải không tồn tại hoặc đã hoàn tất.");
            if (running.contains(jobId)) { if ("PAUSED".equals(job.status)) throw new IllegalArgumentException("Đang dừng các kết nối. Thử lại sau vài giây."); return; }
            if (running.size() >= 2) throw new IllegalArgumentException("Tối đa 2 file tải đồng thời.");
            job.status = "DOWNLOADING"; job.error = ""; running.add(jobId);
            speedBytes.put(jobId, new AtomicLong()); speedStart.put(jobId, System.currentTimeMillis());
            store.event("Bắt đầu / tiếp tục tải " + store.snapshot().library.get(job.fileId).manifest.name()); store.save();
        }
        coordinators.submit(() -> transfer(jobId));
    }
    public void pause(String jobId) {
        synchronized (store) {
            Job job = store.snapshot().jobs.get(jobId);
            if (job == null) throw new IllegalArgumentException("Không có phiên tải.");
            if (!"COMPLETED".equals(job.status)) { job.status = "PAUSED"; store.event("Tạm dừng phiên " + jobId); store.save(); }
        }
    }
    private boolean active(String jobId) {
        synchronized (store) {
            Job job = store.snapshot().jobs.get(jobId);
            return !closing && !tracker.token().isBlank() && job != null && !Set.of("PAUSED", "COMPLETED", "ERROR").contains(job.status);
        }
    }
    private record Source(String id, String name, String owner, String host, int port, Set<Integer> chunks, String ticket) {}
    private byte[] fetchObserved(Source source, Manifest manifest, int index, String purpose) throws IOException {
        String key = UUID.randomUUID().toString();
        Map<String, Object> link = Map.of("peerId", source.id, "name", source.name, "host", source.host,
                "port", source.port, "chunk", index + 1, "purpose", purpose, "at", System.currentTimeMillis());
        links.put(key, link);
        try {
            byte[] bytes = tcpClient.fetch(source.host, source.port, source.ticket, manifest, index);
            sourceReceived.computeIfAbsent(source.id, id -> new AtomicLong()).addAndGet(bytes.length);
            sourceChunks.computeIfAbsent(source.id, id -> new AtomicLong()).incrementAndGet();
            recentLinks.put(source.id, Map.of("peerId", source.id, "name", source.name, "host", source.host,
                    "port", source.port, "chunk", index + 1, "purpose", purpose, "at", System.currentTimeMillis()));
            return bytes;
        } finally { links.remove(key); }
    }

    private void sampleNetwork() {
        synchronized (samples) {
        long now = System.nanoTime(), rx = ((Number) tcpClient.metrics().get("receivedBytes")).longValue(), tx = tcpServer.sentBytes();
        double seconds = Math.max(0.001, (now - sampleAt) / 1_000_000_000.0);
        sourceReceived.forEach((id, counter) -> {
            long total = counter.get(), previous = sourcePrevious.getOrDefault(id, 0L);
            sourceStats.put(id, Map.of("bytes", total, "chunks", sourceChunks.getOrDefault(id, new AtomicLong()).get(),
                    "speed", (long) ((total - previous) / seconds)));
            sourcePrevious.put(id, total);
        });
        samples.add(Map.of("at", System.currentTimeMillis(), "receive", (long) ((rx - sampleRx) / seconds),
                "send", (long) ((tx - sampleTx) / seconds)));
        if (samples.size() > 120) samples.remove(0);
        sampleAt = now; sampleRx = rx; sampleTx = tx;
        recentLinks.values().removeIf(link -> System.currentTimeMillis() - ((Number) link.get("at")).longValue() > 30000);
        }
    }
    private List<Map<String, Object>> networkSamples() { synchronized (samples) { return new ArrayList<>(samples); } }

    public void simulateFault(String mode, int delayMs, int seconds) {
        ready(); tcpServer.fault(mode, delayMs, seconds);
        synchronized (store) { store.event("Mô phỏng nguồn: " + mode + " · " + seconds + " giây"); store.save(); }
    }

    /** Two fresh TCP passes over the same bounded chunk set, without changing the library/checkpoint. */
    public Object startBenchmark(String fileId) throws Exception {
        validId(fileId); ready();
        synchronized (store) {
            if (!running.isEmpty() || !benchmarking.compareAndSet(false, true))
                throw new IllegalArgumentException("Chỉ đo một lần và cần tạm dừng các phiên tải trước.");
        }
        try {
            Manifest manifest = null;
            for (JsonNode file : (JsonNode) catalog(""))
                if (fileId.equals(file.path("manifest").path("id").asText())) manifest = json.treeToValue(file.path("manifest"), Manifest.class);
            if (manifest == null) throw new IllegalArgumentException("File chưa được duyệt.");
            manifest.validate();
            Manifest selected = manifest;
            benchmark = Map.of("status", "RUNNING", "name", selected.name(), "phase", "Chuẩn bị nguồn", "results", List.of());
            labWorker.submit(() -> runBenchmark(selected));
            return Map.of("message", "Đang đo hai lượt TCP trên cùng tập chunk (tối đa 16 MiB mỗi lượt).");
        } catch (Exception e) { benchmarking.set(false); throw e; }
    }
    private void runBenchmark(Manifest manifest) {
        var results = new ArrayList<Map<String, Object>>();
        try {
            List<Source> initial = available(manifest.id());
            int count = Math.min(64, manifest.hashes().size());
            if (count < 2) throw new IOException("Chọn file có ít nhất 2 chunk để so sánh nhiều nguồn.");
            List<Source> complete = initial.stream().filter(s -> {
                for (int i = 0; i < count; i++) if (!s.chunks.contains(i)) return false;
                return true;
            }).toList();
            if (complete.size() < 2) throw new IOException("Cần ít nhất 2 nguồn khác peer này có đủ các chunk đo (tối đa 64 chunk đầu).");
            List<String> ids = complete.stream().map(Source::id).toList();
            for (boolean multi : List.of(false, true)) {
                String phase = multi ? "Nhiều nguồn" : "Một nguồn";
                benchmark = Map.of("status", "RUNNING", "name", manifest.name(), "phase", phase, "results", List.copyOf(results));
                List<Source> fresh = available(manifest.id()); // renew short-lived tickets before each pass
                List<Source> sources = new ArrayList<>();
                for (String id : ids) fresh.stream().filter(s -> s.id.equals(id)).findFirst().ifPresent(sources::add);
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
                            if (!loggedIn() || closing) throw new IOException("Phiên đo đã dừng.");
                            Source source = chosen.get(index % chosen.size());
                            byte[] bytes = fetchObserved(source, manifest, index, "Đo " + phase);
                            sourceBytes.merge(source.name + " · " + source.id.substring(0, 8), (long) bytes.length, Long::sum);
                            return null;
                        }));
                    }
                    for (Future<?> future : futures) future.get();
                } finally { pool.shutdownNow(); pool.awaitTermination(10, TimeUnit.SECONDS); }
                long nanos = Math.max(1, System.nanoTime() - started), total = sourceBytes.values().stream().mapToLong(Long::longValue).sum();
                results.add(Map.of("mode", phase, "bytes", total, "durationMs", nanos / 1_000_000.0,
                        "speed", total * 1_000_000_000.0 / nanos, "sourceBytes", new TreeMap<>(sourceBytes), "workers", 4, "chunks", count));
            }
            benchmark = Map.of("status", "COMPLETED", "name", manifest.name(), "results", List.copyOf(results));
        } catch (Exception e) {
            Throwable cause = e instanceof ExecutionException ? e.getCause() : e;
            benchmark = Map.of("status", "ERROR", "name", manifest.name(), "error", Objects.toString(cause.getMessage(), "Phép đo bị gián đoạn"), "results", List.copyOf(results));
        } finally { benchmarking.set(false); }
    }
    private List<Source> available(String fileId) throws Exception {
        var result = new ArrayList<Source>(); String self;
        synchronized (store) { self = store.snapshot().peerId; }
        JsonNode list = tracker.call("GET", "/api/files/" + fileId + "/sources", null);
        for (JsonNode s : list) {
            if (self.equals(s.path("id").asText())) continue;
            try {
                String id = s.path("id").asText(); var chunks = new HashSet<Integer>(); s.path("chunks").forEach(n -> chunks.add(n.asInt()));
                String ticket = tracker.call("POST", "/api/tickets", Map.of("fileId", fileId, "sourceId", id)).path("ticket").asText();
                result.add(new Source(id, s.path("name").asText(), s.path("owner").asText(), s.path("host").asText(), s.path("port").asInt(), chunks, ticket));
            } catch (TrackerGateway.TrackerFailure ignored) { /* A source may go offline while tickets are issued. */ }
        }
        return result;
    }
    private void transfer(String jobId) {
        ThreadPoolExecutor workers = NamedThreads.boundedPool("chunk-download-" + jobId.substring(0, 8), 4, 16);
        downloadPools.put(jobId, workers);
        try {
            Job job; Entry entry;
            synchronized (store) { job = store.snapshot().jobs.get(jobId); entry = store.snapshot().library.get(job.fileId); }
            while (active(jobId)) {
                List<Integer> missing;
                synchronized (store) {
                    missing = new ArrayList<>();
                    for (int i = 0; i < entry.manifest.hashes().size(); i++) if (!entry.chunks.contains(i)) missing.add(i);
                }
                if (missing.isEmpty()) { assemble(jobId, entry); break; }
                List<Source> sources;
                try { sources = available(job.fileId); }
                catch (Exception e) { waiting(jobId, e.getMessage()); Thread.sleep(2000); continue; }
                if (sources.isEmpty()) { waiting(jobId, "Chưa có nguồn trực tuyến. Tự thử lại sau 2 giây."); Thread.sleep(2000); continue; }
                // Download rare chunks first. Only one worker writes a given index in each batch.
                missing.removeIf(i -> sources.stream().noneMatch(s -> s.chunks.contains(i)));
                if (missing.isEmpty()) { waiting(jobId, "Nguồn hiện có chưa giữ phần còn thiếu. Đang chờ thêm nguồn."); Thread.sleep(2000); continue; }
                missing.sort(Comparator.comparingLong(i -> sources.stream().filter(s -> s.chunks.contains(i)).count()));
                var futures = new ArrayList<Future<?>>(); AtomicInteger progress = new AtomicInteger();
                Map<String, AtomicInteger> failures = new ConcurrentHashMap<>();
                for (int n = 0; n < Math.min(16, missing.size()); n++) {
                    int index = missing.get(n), offset = n;
                    futures.add(workers.submit(() -> {
                        for (int attempt = 0; attempt < sources.size() && active(jobId); attempt++) {
                            Source source = sources.get((offset + attempt) % sources.size());
                            if (!source.chunks.contains(index) || failures.computeIfAbsent(source.id, k -> new AtomicInteger()).get() >= 2) continue;
                            try {
                                byte[] bytes = fetchObserved(source, entry.manifest, index, "Tải file");
                                synchronized (store) {
                                    if (!active(jobId)) return;
                                    Path part = store.chunk(job.fileId, index); Files.createDirectories(part.getParent());
                                    Path tmp = part.resolveSibling(index + ".tmp"); Files.write(tmp, bytes); PeerStore.atomicMove(tmp, part);
                                    entry.chunks.add(index); job.downloaded = completedBytes(entry);
                                    job.chunkSources.put(index, new ChunkSource(source.id, source.name, source.owner,
                                            source.host, source.port, System.currentTimeMillis()));
                                    job.sourceBytes.merge(source.name + " · " + source.id.substring(0, 8), (long) bytes.length, Long::sum);
                                    job.status = "DOWNLOADING"; job.error = ""; store.save();
                                }
                                speedBytes.get(jobId).addAndGet(bytes.length); progress.incrementAndGet(); return;
                            } catch (Exception e) {
                                if (failures.get(source.id).getAndIncrement() == 0) synchronized (store) {
                                    store.event("Nguồn " + source.name + " lỗi: " + e.getMessage() + ". Thử nguồn khác.");
                                }
                            }
                        }
                    }));
                }
                for (Future<?> f : futures) f.get();
                if (progress.get() == 0 && active(jobId)) { waiting(jobId, "Chưa tải được phần thiếu. Đang tìm lại nguồn."); Thread.sleep(2000); }
            }
        } catch (Exception e) {
            synchronized (store) { Job j = store.snapshot().jobs.get(jobId); if (!"PAUSED".equals(j.status)) { j.status = "ERROR"; j.error = Objects.toString(e.getMessage(), e.getClass().getSimpleName()); } store.save(); }
        } finally { workers.shutdownNow(); downloadPools.remove(jobId); running.remove(jobId); report(jobId); }
    }
    private void waiting(String jobId, String message) {
        synchronized (store) {
            Job j = store.snapshot().jobs.get(jobId); if (active(jobId)) { j.status = "WAITING"; j.error = message; store.save(); }
        }
    }
    private void assemble(String jobId, Entry entry) throws Exception {
        Path temporary = store.root().resolve(entry.manifest.id() + ".assembling");
        try {
            try (var out = Files.newOutputStream(temporary)) {
                for (int i = 0; i < entry.manifest.hashes().size(); i++) {
                    if (!active(jobId)) return;
                    Files.copy(store.chunk(entry.manifest.id(), i), out);
                }
            }
            if (!Manifest.hash(temporary).equals(entry.manifest.id())) throw new IOException("SHA-256 toàn file không khớp. Các chunk cần được kiểm tra lại.");
            synchronized (store) {
                if (!active(jobId)) return;
                PeerStore.atomicMove(temporary, store.blob(entry.manifest.id())); entry.complete = true;
                Job job = store.snapshot().jobs.get(jobId); job.status = "COMPLETED"; job.finished = System.currentTimeMillis(); job.error = "";
                store.event("SHA-256 hợp lệ. Hoàn tất " + entry.manifest.name()); store.save();
            }
            try { advertise(entry.manifest.id()); } catch (Exception e) { connection = e.getMessage(); }
        } finally { Files.deleteIfExists(temporary); }
    }
    private void report(String jobId) {
        Map<String, Object> body;
        synchronized (store) {
            Job j = store.snapshot().jobs.get(jobId); if (j == null) return;
            body = Map.of("id", j.id, "peerId", store.snapshot().peerId, "fileId", j.fileId, "status", j.status,
                    "bytes", completedBytes(store.snapshot().library.get(j.fileId)));
        }
        try { tracker.call("POST", "/api/transfers", body); } catch (Exception ignored) { /* Next heartbeat retries. */ }
    }
    private long completedBytes(Entry entry) {
        if (entry.complete) return entry.manifest.size();
        return entry.chunks.stream().mapToLong(entry.manifest::length).sum();
    }
    /** Authentication and repository access stay in the service; socket framing stays in network/. */
    private byte[] readChunk(Wire.Request request) throws Exception {
        validId(request.fileId());
        String peerId;
        synchronized (store) { peerId = store.snapshot().peerId; }
        tracker.call("POST", "/api/tickets/authorize", Map.of(
                "ticket", request.ticket(), "peerId", peerId, "fileId", request.fileId()));
        synchronized (store) {
            Entry entry = store.snapshot().library.get(request.fileId());
            if (entry == null || !entry.sharing || !entry.chunks.contains(request.chunk()))
                throw new IOException("Peer không chia sẻ chunk này.");
            int length = entry.manifest.length(request.chunk());
            byte[] bytes;
            if (entry.complete) {
                bytes = new byte[length];
                try (var file = new RandomAccessFile(store.blob(request.fileId()).toFile(), "r")) {
                    file.seek((long) request.chunk() * entry.manifest.chunkSize());
                    file.readFully(bytes);
                }
            } else bytes = Files.readAllBytes(store.chunk(request.fileId(), request.chunk()));
            if (bytes.length != length || !Manifest.hash(bytes).equals(entry.manifest.hashes().get(request.chunk())))
                throw new IOException("Chunk nguồn bị hỏng.");
            return bytes;
        }
    }
    public Map<String, Object> status() {
        synchronized (store) {
            var result = new LinkedHashMap<String, Object>();
            result.put("user", user); result.put("tracker", tracker.base()); result.put("connection", connection);
            result.put("peerId", store.snapshot().peerId); result.put("peerStatus", peerStatus); result.put("name", store.snapshot().name);
            result.put("host", store.snapshot().host); result.put("tcpPort", tcpPort); result.put("uploaded", tcpServer.sentBytes());
            result.put("catalog", catalogSnapshot);
            result.put("lab", Map.of("fault", tcpServer.fault(), "benchmark", benchmark, "samples", networkSamples(),
                    "links", new ArrayList<>(links.values()), "recentLinks", new ArrayList<>(recentLinks.values()),
                    "sourceStats", new TreeMap<>(sourceStats)));
            result.put("network", Map.of("upload", tcpServer.metrics(), "download", tcpClient.metrics(),
                    "downloadWorkers", downloadPools.values().stream().mapToInt(ThreadPoolExecutor::getActiveCount).sum(),
                    "activeDownloads", running.size(), "workerLimitPerFile", 4, "fileLimit", 2));
            var library = new ArrayList<Map<String, Object>>();
            for (Entry e : store.snapshot().library.values()) library.add(Map.of("manifest", e.manifest, "complete", e.complete,
                    "sharing", e.sharing, "catalogStatus", e.catalogStatus, "chunks", new ArrayList<>(e.chunks)));
            var jobs = new ArrayList<Map<String, Object>>();
            for (Job j : store.snapshot().jobs.values()) {
                Entry e = store.snapshot().library.get(j.fileId);
                long elapsed = Math.max(1, System.currentTimeMillis() - speedStart.getOrDefault(j.id, System.currentTimeMillis()));
                long speed = active(j.id) ? speedBytes.getOrDefault(j.id, new AtomicLong()).get() * 1000 / elapsed : 0;
                jobs.add(Map.of("id", j.id, "fileId", j.fileId, "name", e.manifest.name(), "status", j.status,
                        "bytes", completedBytes(e), "size", e.manifest.size(), "speed", speed, "error", j.error,
                        "sourceBytes", new LinkedHashMap<>(j.sourceBytes), "chunks", e.chunks.size()));
            }
            result.put("library", library); result.put("jobs", jobs); result.put("events", new ArrayList<>(store.snapshot().events)); return result;
        }
    }
    public record LocalFile(Path path, String name) {}
    public Map<Integer, ChunkSource> chunkHistory(String jobId) {
        synchronized (store) {
            Job job = store.snapshot().jobs.get(jobId);
            if (job == null) throw new IllegalArgumentException("Phiên tải không tồn tại.");
            return new TreeMap<>(job.chunkSources);
        }
    }
    public LocalFile localFile(String id) {
        validId(id); synchronized (store) {
            Entry entry = store.snapshot().library.get(id);
            if (entry == null || !entry.complete) throw new IllegalArgumentException("File chưa hoàn tất.");
            return new LocalFile(store.blob(id), entry.manifest.name());
        }
    }
    @PreDestroy public void stop() throws IOException {
        closing = true;
        tcpServer.close(); tcpClient.close(); pulse.shutdownNow(); coordinators.shutdownNow();
        labWorker.shutdownNow(); telemetry.shutdownNow();
        downloadPools.values().forEach(ExecutorService::shutdownNow);
        synchronized (store) { store.save(); }
    }
}
