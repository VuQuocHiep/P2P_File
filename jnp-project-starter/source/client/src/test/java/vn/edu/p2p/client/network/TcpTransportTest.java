package vn.edu.p2p.client.network;

import org.junit.jupiter.api.Test;
import vn.edu.p2p.common.concurrent.NamedThreads;
import vn.edu.p2p.common.model.Manifest;
import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TcpTransportTest {
    private final byte[] payload = "one verified chunk over a real socket".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private Manifest manifest() {
        String hash = Manifest.hash(payload);
        return new Manifest(hash, "sample.bin", payload.length, Manifest.CHUNK_SIZE, List.of(hash));
    }

    @Test void fourClientsAreServedConcurrentlyByIndependentWorkers() throws Exception {
        var barrier = new CyclicBarrier(4);
        var active = new AtomicInteger();
        var maximum = new AtomicInteger();
        ExecutorService clients = Executors.newFixedThreadPool(4, NamedThreads.factory("test-client"));
        try (var server = new TcpChunkServer(0, 0, request -> {
            int count = active.incrementAndGet();
            maximum.accumulateAndGet(count, Math::max);
            try { barrier.await(5, TimeUnit.SECONDS); return payload; }
            finally { active.decrementAndGet(); }
        }); var client = new TcpChunkClient()) {
            var tasks = new ArrayList<Future<byte[]>>();
            for (int i = 0; i < 4; i++) tasks.add(clients.submit(() ->
                    client.fetch("127.0.0.1", server.port(), "test-ticket", manifest(), 0)));
            for (var task : tasks) assertArrayEquals(payload, task.get(8, TimeUnit.SECONDS));
            assertEquals(4, maximum.get(), "Must overlap in the provider, not merely complete sequentially");
            assertEquals(4L, client.metrics().get("requests"));
        } finally { clients.shutdownNow(); }
    }

    @Test void corruptPayloadIsRejectedEvenWhenLengthIsCorrect() throws Exception {
        try (var server = new TcpChunkServer(0, 0, request -> {
            byte[] corrupt = payload.clone(); corrupt[0] ^= 1; return corrupt;
        }); var client = new TcpChunkClient()) {
            var error = assertThrows(IOException.class, () ->
                    client.fetch("127.0.0.1", server.port(), "test-ticket", manifest(), 0));
            assertTrue(error.getMessage().contains("SHA-256"));
            assertEquals(1L, client.metrics().get("failures"));
        }
    }

    @Test void overloadIsBoundedAndClosingServerClosesQueuedConnections() throws Exception {
        var sockets = new ArrayList<Socket>();
        var server = new TcpChunkServer(0, 0, request -> payload);
        try {
            // Send no frame: four workers block in read; the next sixteen connections queue.
            for (int i = 0; i < 24; i++) sockets.add(new Socket("127.0.0.1", server.port()));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (((Number) server.metrics().get("rejected")).longValue() == 0 && System.nanoTime() < deadline)
                Thread.sleep(10);
            assertTrue(((Number) server.metrics().get("rejected")).longValue() > 0);
            assertTrue(((Number) server.metrics().get("activeWorkers")).intValue() <= 4);
            assertTrue(((Number) server.metrics().get("queued")).intValue() <= 16);
            server.close();
            assertEquals(0, server.metrics().get("connections"));
        } finally {
            for (Socket socket : sockets) socket.close();
            server.close();
        }
    }

    @Test void demoFaultsReachReceiverAndResetWithoutChangingSource() throws Exception {
        byte[] original = payload.clone();
        try (var server = new TcpChunkServer(0, 0, request -> payload); var client = new TcpChunkClient()) {
            server.fault("CORRUPT", 0, 30);
            assertTrue(assertThrows(IOException.class, () -> client.fetch("127.0.0.1", server.port(), "ticket", manifest(), 0))
                    .getMessage().contains("SHA-256"));
            assertArrayEquals(original, payload, "Fault injection must never mutate stored/provider bytes");
            server.fault("DISCONNECT", 0, 30);
            assertThrows(IOException.class, () -> client.fetch("127.0.0.1", server.port(), "ticket", manifest(), 0));
            server.fault("SLOW", 150, 30);
            long start = System.nanoTime();
            assertArrayEquals(payload, client.fetch("127.0.0.1", server.port(), "ticket", manifest(), 0));
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) >= 140);
            server.fault("NORMAL", 0, 1);
            assertArrayEquals(payload, client.fetch("127.0.0.1", server.port(), "ticket", manifest(), 0));
            assertEquals(3L, server.metrics().get("injected"));
            assertEquals((long) payload.length * 2, client.metrics().get("receivedBytes"));
            assertThrows(IllegalArgumentException.class, () -> server.fault("INVALID", 0, 30));
            assertThrows(IllegalArgumentException.class, () -> server.fault("SLOW", 5001, 30));
        }
    }

    @Test void demoFaultAutomaticallyExpires() throws Exception {
        try (var server = new TcpChunkServer(0, 0, request -> payload); var client = new TcpChunkClient()) {
            server.fault("DISCONNECT", 0, 1);
            assertEquals("DISCONNECT", server.fault().mode());
            Thread.sleep(1050);
            assertEquals("NORMAL", server.fault().mode());
            assertArrayEquals(payload, client.fetch("127.0.0.1", server.port(), "ticket", manifest(), 0));
        }
    }
}
