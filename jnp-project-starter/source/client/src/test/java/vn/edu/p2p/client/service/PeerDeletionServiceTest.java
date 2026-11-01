package vn.edu.p2p.client.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.p2p.client.mapper.JobMapper;
import vn.edu.p2p.client.model.Entry;
import vn.edu.p2p.client.model.Job;
import vn.edu.p2p.client.network.TrackerGateway;
import vn.edu.p2p.client.repository.*;
import vn.edu.p2p.client.storage.PeerStorage;
import vn.edu.p2p.common.model.Manifest;
import static org.junit.jupiter.api.Assertions.*;

class PeerDeletionServiceTest {
    @TempDir Path temp;

    @Test
    void deletingAnActiveDownloadStopsWorkersAndDoesNotRecreateItsFiles() throws Exception {
        var json = new ObjectMapper();
        Path original = temp.resolve("original.bin");
        Files.write(original, new byte[] {1, 2, 3});
        var manifest = Manifest.create(original, "original.bin");
        try (var storage = new PeerStorage(temp.resolve("profile").toString(), json)) {
            var profile = new PeerProfileRepository(storage);
            var library = new LibraryRepository(storage);
            var jobs = new DownloadJobRepository(storage);
            var events = new PeerEventRepository(storage);
            var context =
                    new PeerContext(
                            storage,
                            profile,
                            library,
                            jobs,
                            events,
                            new TrackerGateway("http://127.0.0.1:9", json),
                            json,
                            0,
                            0);
            var workers =
                    new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
            try {
                var entry = new Entry();
                entry.manifest = manifest;
                entry.chunks.add(0);
                library.save(manifest.id(), entry);
                var job = new Job();
                job.id = "active-deletion-job";
                job.fileId = manifest.id();
                job.status = "DOWNLOADING";
                jobs.save(job);
                Files.copy(original, storage.blob(manifest.id()));
                Files.createDirectories(storage.chunk(manifest.id(), 0).getParent());
                Files.copy(original, storage.chunk(manifest.id(), 0));
                context.running.add(job.id);
                context.downloadPools.put(job.id, workers);
                var started = new CountDownLatch(1);
                workers.submit(
                        () -> {
                            started.countDown();
                            try {
                                new CountDownLatch(1).await();
                            } catch (InterruptedException expected) {
                                Thread.currentThread().interrupt();
                            }
                        });
                assertTrue(started.await(2, TimeUnit.SECONDS));
                new PeerDeletionService(context).apply(List.of(manifest.id()));
                assertTrue(workers.isShutdown());
                assertTrue(workers.awaitTermination(2, TimeUnit.SECONDS));
                assertNull(jobs.findById(job.id));
                assertNull(library.findByFileId(manifest.id()));
                context.peerDownloadService = new PeerDownloadService(context, new JobMapper());
                assertDoesNotThrow(() -> context.peerDownloadService.transfer(job.id));
                assertFalse(context.running.contains(job.id));
                assertFalse(Files.exists(storage.blob(manifest.id())));
                assertFalse(Files.exists(storage.chunk(manifest.id(), 0).getParent()));
                assertTrue(storage.isDeleted(manifest.id()));
                assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(original));
            } finally {
                workers.shutdownNow();
                context.coordinators.shutdownNow();
                context.pulse.shutdownNow();
                context.telemetry.shutdownNow();
                context.labWorker.shutdownNow();
                context.tcpClient.close();
            }
        }
    }
}
