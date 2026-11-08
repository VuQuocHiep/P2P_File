package vn.edu.p2p.client.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import vn.edu.p2p.common.model.Manifest;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class BandwidthLimiterTest {
    private final byte[] payload = new byte[32 * 1024];

    private Manifest manifest(byte[] bytes) {
        String hash = Manifest.hash(bytes);
        return new Manifest(hash, "bandwidth.bin", bytes.length, Manifest.CHUNK_SIZE, List.of(hash));
    }

    @Test
    void uploadLimitIsSharedAcrossFourConnections() throws Exception {
        try (var server = new TcpChunkServer(0, 0, request -> payload, new BandwidthLimiter(128));
                var client = new TcpChunkClient()) {
            assertAggregateLimit(server, client);
        }
    }

    @Test
    void downloadLimitIsSharedAcrossFourConnections() throws Exception {
        try (var server = new TcpChunkServer(0, 0, request -> payload);
                var client = new TcpChunkClient(new BandwidthLimiter(128))) {
            assertAggregateLimit(server, client);
            assertEquals(4L * payload.length, client.readBytes());
        }
    }

    private void assertAggregateLimit(TcpChunkServer server, TcpChunkClient client) throws Exception {
        var workers = Executors.newFixedThreadPool(4);
        try {
            var start = new CountDownLatch(1);
            var futures = new ArrayList<Future<byte[]>>();
            long at = System.nanoTime();
            for (int i = 0; i < 4; i++) {
                futures.add(workers.submit(() -> {
                    start.await();
                    return client.fetch("127.0.0.1", server.port(), "ticket", manifest(payload), 0);
                }));
            }
            start.countDown();
            for (var future : futures) assertArrayEquals(payload, future.get(10, TimeUnit.SECONDS));
            long elapsed = System.nanoTime() - at;
            assertTrue(elapsed >= TimeUnit.MILLISECONDS.toNanos(800),
                    "128 KiB across all workers must take about 1 second at 128 KiB/s");
            assertEquals(4L * payload.length, client.metrics().get("receivedBytes"));
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void changingLimitsAndDisablingThemAffectsAnActiveUpload() throws Exception {
        var limiter = new BandwidthLimiter(1);
        var worker = Executors.newSingleThreadExecutor();
        try (var server = new TcpChunkServer(0, 0, request -> payload, limiter);
                var client = new TcpChunkClient()) {
            var future = worker.submit(() -> client.fetch("127.0.0.1", server.port(), "ticket", manifest(payload), 0));
            awaitProgress(client);
            assertFalse(future.isDone());
            limiter.setLimit(64);
            assertArrayEquals(payload, future.get(3, TimeUnit.SECONDS));
            limiter.setLimit(1);
            var unlimited = worker.submit(() -> client.fetch("127.0.0.1", server.port(), "ticket", manifest(payload), 0));
            long before = client.readBytes();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (client.readBytes() == before && System.nanoTime() < deadline) Thread.sleep(5);
            assertFalse(unlimited.isDone());
            limiter.setLimit(0);
            assertArrayEquals(payload, unlimited.get(2, TimeUnit.SECONDS));
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    void pausingAThrottledDownloadReleasesItsWaiter() throws Exception {
        var limiter = new BandwidthLimiter(1);
        var paused = new AtomicBoolean();
        var worker = Executors.newSingleThreadExecutor();
        try (var server = new TcpChunkServer(0, 0, request -> payload);
                var client = new TcpChunkClient(limiter)) {
            var future = worker.submit(() -> client.fetch("127.0.0.1", server.port(), "ticket", manifest(payload), 0, paused::get));
            awaitProgress(client);
            paused.set(true);
            var failure = assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
            assertInstanceOf(IOException.class, failure.getCause());
            limiter.setLimit(0);
            assertArrayEquals(payload, client.fetch("127.0.0.1", server.port(), "ticket", manifest(payload), 0));
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    void closingTheClientWakesThrottledDownloads() throws Exception {
        var worker = Executors.newSingleThreadExecutor();
        try (var server = new TcpChunkServer(0, 0, request -> payload);
                var client = new TcpChunkClient(new BandwidthLimiter(1))) {
            var future = worker.submit(() -> client.fetch("127.0.0.1", server.port(), "ticket", manifest(payload), 0));
            awaitProgress(client);
            client.close();
            var failure = assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
            assertInstanceOf(IOException.class, failure.getCause());
            assertEquals(0, client.metrics().get("connections"));
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    @Timeout(20)
    void lowUploadRatesKeepMakingProgressBeyondTheEightSecondReadTimeout() throws Exception {
        byte[] bytes = new byte[20 * 1024];
        try (var server = new TcpChunkServer(0, 0, request -> bytes, new BandwidthLimiter(2));
                var client = new TcpChunkClient()) {
            long started = System.nanoTime();
            assertArrayEquals(bytes, client.fetch("127.0.0.1", server.port(), "ticket", manifest(bytes), 0));
            assertTrue(System.nanoTime() - started > TimeUnit.SECONDS.toNanos(8));
        }
    }

    private void awaitProgress(TcpChunkClient client) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (client.readBytes() == 0 && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(client.readBytes() > 0, "A limited stream must start without waiting for the whole chunk");
    }
}
