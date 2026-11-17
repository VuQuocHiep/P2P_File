package vn.edu.p2p.client.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.p2p.client.mapper.JobMapper;
import vn.edu.p2p.client.mapper.TrackerResponseMapper;
import vn.edu.p2p.client.model.*;
import vn.edu.p2p.client.network.TrackerGateway;
import vn.edu.p2p.client.repository.*;
import vn.edu.p2p.client.storage.PeerStorage;
import vn.edu.p2p.common.model.Manifest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PeerDownloadQueueTest {
    @TempDir Path temp;
    final ObjectMapper json = new ObjectMapper();
    PeerContext context;
    PeerDownloadService service;

    @BeforeEach void open() throws Exception {
        var storage = new PeerStorage(temp.resolve("profile").toString(), json);
        var tracker = mock(TrackerGateway.class);
        when(tracker.token()).thenReturn("test-token");
        context = new PeerContext(storage, new PeerProfileRepository(storage), new LibraryRepository(storage),
                new DownloadJobRepository(storage), new PeerEventRepository(storage), tracker, json, 0, 0);
        context.user = json.readTree("{\"username\":\"alice\",\"status\":\"APPROVED\"}");
        context.peerStatus = "APPROVED";
        context.peerSessionService = new PeerSessionService(context, new TrackerResponseMapper(json));
        context.peerSourceService = mock(PeerSourceService.class);
        when(context.peerSourceService.available(anyString())).thenReturn(List.of());
        service = new PeerDownloadService(context, new JobMapper());
        context.peerDownloadService = service;
    }

    @AfterEach void close() throws Exception {
        context.closing = true;
        context.coordinators.shutdownNow();
        assertTrue(context.coordinators.awaitTermination(5, TimeUnit.SECONDS));
        context.downloadPools.values().forEach(ExecutorService::shutdownNow);
        context.pulse.shutdownNow();
        context.telemetry.shutdownNow();
        context.labWorker.shutdownNow();
        context.uploadBandwidth.close();
        context.tcpClient.close();
        context.storage.close();
    }

    Job job(int index) throws Exception {
        return job(index, 1);
    }

    Job job(int index, int size) throws Exception {
        Path source = temp.resolve("file-" + index);
        byte[] content = new byte[size];
        Arrays.fill(content, (byte) index);
        Files.write(source, content);
        var entry = new Entry();
        entry.manifest = Manifest.create(source, "file-" + index + ".bin");
        var job = new Job();
        job.id = UUID.randomUUID().toString();
        job.fileId = entry.manifest.id();
        job.created = index;
        synchronized (context.storage.monitor()) {
            context.library.save(job.fileId, entry);
            context.jobs.save(job);
            context.storage.saveCheckpoint();
        }
        return job;
    }

    void await(BooleanSupplier condition) {
        assertTimeoutPreemptively(Duration.ofSeconds(6), () -> {
            while (true) {
                synchronized (context.storage.monitor()) {
                    if (condition.getAsBoolean()) return;
                }
                Thread.sleep(20);
            }
        });
    }

    @Test void schedulesByPriorityThenFifoWithoutInterruptingActiveFiles() throws Exception {
        Job a = job(1), b = job(2), c = job(3), d = job(4), e = job(5);
        for (Job j : List.of(a, b, c, d, e)) service.resume(j.id);
        assertEquals(Set.of(a.id, b.id), context.running);
        assertEquals(List.of(c, d, e), context.jobs.findQueued());
        assertFalse(service.active(c.id));
        assertEquals(0, context.speedBytes.getOrDefault(c.id, new java.util.concurrent.atomic.AtomicLong()).get());
        long order = d.queueOrder;
        service.priority(c.id, "LOW");
        service.priority(d.id, "HIGH");
        service.priority(e.id, "HIGH");
        service.resume(d.id); // An idempotent resume must not move an existing queue entry.
        assertEquals(order, d.queueOrder);
        assertEquals(List.of(d, e, c), context.jobs.findQueued());
        assertEquals(Set.of(a.id, b.id), context.running);
        service.pause(a.id);
        await(() -> context.running.equals(Set.of(b.id, d.id)));
        assertEquals(List.of(e, c), context.jobs.findQueued());
        service.pause(e.id); // Pausing a queued file removes it without starting workers.
        service.pause(d.id);
        await(() -> context.running.equals(Set.of(b.id, c.id)));
        assertEquals("PAUSED", e.status);
        assertFalse(context.downloadPools.containsKey(e.id));
    }

    @Test void immediateResumeWaitsForOldWorkersAndJoinsTheBackOfItsPriority() throws Exception {
        Job a = job(1), b = job(2), c = job(3);
        service.resume(a.id);
        service.resume(b.id);
        service.resume(c.id);
        synchronized (context.storage.monitor()) {
            service.pause(a.id);
            service.resume(a.id);
            assertEquals(List.of(c, a), context.jobs.findQueued());
        }
        await(() -> context.running.equals(Set.of(b.id, c.id)));
        service.pause(b.id);
        await(() -> context.running.equals(Set.of(a.id, c.id)));
    }

    @Test void concurrentResumesNeverReserveMoreThanTwoSlotsOrDuplicateJobs() throws Exception {
        var jobs = new ArrayList<Job>();
        for (int i = 1; i <= 12; i++) jobs.add(job(i));
        var callers = Executors.newFixedThreadPool(8);
        try {
            var futures = new ArrayList<Future<?>>();
            for (int i = 0; i < 3; i++)
                for (Job j : jobs) futures.add(callers.submit(() -> service.resume(j.id)));
            for (var future : futures) future.get(5, TimeUnit.SECONDS);
        } finally {
            callers.shutdownNow();
        }
        assertEquals(2, context.running.size());
        assertEquals(10, context.jobs.findQueued().size());
        assertEquals(12, new HashSet<>(jobs.stream().map(j -> j.queueOrder).toList()).size());
    }

    @Test void completionAndAssemblyFailureBothReleaseASlotForTheNextFile() throws Exception {
        Job a = job(1), b = job(2), complete = job(3), broken = job(4), next = job(5);
        service.resume(a.id);
        service.resume(b.id);
        for (Job j : List.of(complete, broken)) {
            Entry entry = context.library.findByFileId(j.fileId);
            Path chunk = context.storage.chunk(j.fileId, 0);
            Files.createDirectories(chunk.getParent());
            Files.write(chunk, new byte[] {(byte) (j == complete ? 3 : 99)});
            entry.chunks.add(0);
        }
        context.peerLibraryService = mock(PeerLibraryService.class);
        service.resume(complete.id);
        service.resume(broken.id);
        service.resume(next.id);
        service.pause(a.id);
        await(() -> "COMPLETED".equals(complete.status) && "ERROR".equals(broken.status)
                && context.running.equals(Set.of(b.id, next.id)));
        assertArrayEquals(new byte[] {3}, Files.readAllBytes(context.storage.blob(complete.fileId)));
        assertTrue(broken.error.contains("SHA-256"));
    }

    @Test void queueAndPrioritySurviveRestartWhilePreviousActiveFilesRemainPaused() throws Exception {
        Job a = job(1), b = job(2), queued = job(3);
        service.resume(a.id);
        service.resume(b.id);
        service.resume(queued.id);
        service.priority(queued.id, "HIGH");
        long order = queued.queueOrder;
        close();
        open();
        assertEquals("PAUSED", context.jobs.findById(a.id).status);
        Job restored = context.jobs.findById(queued.id);
        assertEquals("QUEUED", restored.status);
        assertEquals(DownloadPriority.HIGH, restored.priority);
        assertEquals(order, restored.queueOrder);
        assertTrue(context.running.isEmpty()); // No transfer before login/approval.
        when(context.tracker.token()).thenReturn("");
        service.dispatchQueued();
        assertTrue(context.running.isEmpty());
        when(context.tracker.token()).thenReturn("new-session");
        context.peerStatus = "PENDING";
        service.dispatchQueued();
        assertTrue(context.running.isEmpty());
        context.peerStatus = "APPROVED";
        context.benchmarking.set(true);
        service.dispatchQueued();
        assertTrue(context.running.isEmpty());
        context.benchmarking.set(false);
        service.dispatchQueued();
        assertEquals(Set.of(queued.id), context.running);
    }

    @Test void badPriorityAndFailedCheckpointsCannotChangeQueueMetadata() throws Exception {
        Job job = job(1);
        service.priority(job.id, "LOW");
        for (String invalid : Arrays.asList(null, "", "URGENT", "high", "1"))
            assertThrows(IllegalArgumentException.class, () -> service.priority(job.id, invalid));
        assertThrows(IllegalArgumentException.class, () -> service.priority("missing", "HIGH"));
        Path blocked = context.storage.root().resolve("profile.json.tmp");
        Files.createDirectory(blocked);
        try {
            assertThrows(IllegalStateException.class, () -> service.priority(job.id, "HIGH"));
            assertThrows(IllegalStateException.class, () -> service.resume(job.id));
            assertEquals(DownloadPriority.LOW, job.priority);
            assertEquals("PAUSED", job.status);
            assertEquals(0, job.queueOrder);
            assertTrue(context.running.isEmpty());
        } finally {
            Files.delete(blocked);
        }
    }

    @Test void legacyJobsDefaultToNormalPriorityWithoutLosingProgress() throws Exception {
        Job job = job(1);
        close();
        Path profile = temp.resolve("profile/profile.json");
        var saved = json.readTree(profile.toFile());
        var legacy = (com.fasterxml.jackson.databind.node.ObjectNode) saved.path("jobs").path(job.id);
        legacy.remove("priority");
        legacy.remove("queueOrder");
        json.writeValue(profile.toFile(), saved);
        open();
        assertEquals(DownloadPriority.NORMAL, context.jobs.findById(job.id).priority);
        assertEquals("PAUSED", context.jobs.findById(job.id).status);
    }

    @Test void logoutPausesQueuedFilesSoTheyCannotStartAfterSlotsAreReleased() throws Exception {
        context.tcpServer = mock(vn.edu.p2p.client.network.TcpChunkServer.class);
        Job a = job(1), b = job(2), queued = job(3);
        for (Job j : List.of(a, b, queued)) service.resume(j.id);
        context.peerSessionService.logout();
        await(() -> context.running.isEmpty());
        assertEquals("PAUSED", queued.status);
        assertNull(context.user);
        assertTrue(context.jobs.findQueued().isEmpty());
    }

    @Test void deletingAnActiveFileWithPendingChunkTasksReleasesItsSlotForTheQueue() throws Exception {
        Job a = job(1, 16 * Manifest.CHUNK_SIZE), b = job(2), queued = job(3);
        var chunks = new TreeSet<Integer>();
        for (int i = 0; i < 16; i++) chunks.add(i);
        var source = new Source("remote-peer", "Source", "bob", "127.0.0.1", 9001, chunks, "ticket");
        when(context.peerSourceService.available(a.fileId)).thenReturn(List.of(source));
        var started = new CountDownLatch(4);
        when(context.peerSourceService.fetchObserved(any(), any(), anyInt(), anyString(), any()))
                .thenAnswer(call -> {
                    started.countDown();
                    new CountDownLatch(1).await();
                    return new byte[0];
                });
        for (Job j : List.of(a, b, queued)) service.resume(j.id);
        assertTrue(started.await(3, TimeUnit.SECONDS));
        await(() -> context.downloadPools.get(a.id).getQueue().size() == 12);
        new PeerDeletionService(context).apply(List.of(a.fileId));
        await(() -> context.running.equals(Set.of(b.id, queued.id)));
        assertNull(context.jobs.findById(a.id));
        assertFalse(context.downloadPools.containsKey(a.id));
        assertTrue(context.storage.isDeleted(a.fileId));
    }
}
