package vn.edu.p2p.client.network;

import vn.edu.p2p.common.concurrent.NamedThreads;
import vn.edu.p2p.common.protocol.Wire;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Acceptor + bounded worker pool. Each accepted socket has exactly one owner. */
public final class TcpChunkServer implements AutoCloseable {
    @FunctionalInterface
    public interface ChunkProvider { byte[] read(Wire.Request request) throws Exception; }

    private final ServerSocket listener;
    private final ExecutorService acceptor = Executors.newSingleThreadExecutor(NamedThreads.factory("tcp-accept"));
    private final ThreadPoolExecutor workers = NamedThreads.boundedPool("tcp-upload", 4, 16);
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final AtomicLong accepted = new AtomicLong(), rejected = new AtomicLong(), sentBytes = new AtomicLong();
    private final ChunkProvider provider;
    private final int delay;
    private volatile boolean closing;
    public record Fault(String mode, int delayMs, long until) {}
    private volatile Fault fault = new Fault("NORMAL", 0, 0);
    private final AtomicLong injected = new AtomicLong();

    public void fault(String mode, int delayMs, int seconds) {
        if (mode == null || !Set.of("NORMAL", "SLOW", "DISCONNECT", "CORRUPT").contains(mode)
                || delayMs < 0 || delayMs > 5000 || seconds < 1 || seconds > 120)
            throw new IllegalArgumentException("Chế độ lỗi không hợp lệ; độ trễ 0–5000 ms, thời hạn 1–120 giây.");
        fault = new Fault(mode, delayMs, "NORMAL".equals(mode) ? 0 : System.currentTimeMillis() + seconds * 1000L);
    }
    public Fault fault() {
        Fault current = fault;
        return current.until() > System.currentTimeMillis() ? current : new Fault("NORMAL", 0, 0);
    }

    public TcpChunkServer(int port, int delay, ChunkProvider provider) throws IOException {
        this.provider = provider;
        this.delay = Math.max(0, Math.min(5000, delay));
        listener = new ServerSocket();
        listener.setReuseAddress(true);
        listener.bind(new InetSocketAddress(port));
        acceptor.submit(this::accept);
    }

    private void accept() {
        while (!closing) {
            try {
                Socket socket = listener.accept();
                socket.setSoTimeout(8000);
                sockets.add(socket);
                accepted.incrementAndGet();
                try { workers.execute(() -> serve(socket)); }
                catch (RejectedExecutionException e) {
                    rejected.incrementAndGet();
                    closeSocket(socket);
                }
            } catch (IOException e) {
                if (!closing) {
                    // A failed accept must not turn into a busy-spin loop.
                    try { Thread.sleep(100); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                }
            }
        }
    }

    private void serve(Socket socket) {
        try (socket) {
            var input = new DataInputStream(socket.getInputStream());
            var output = new DataOutputStream(socket.getOutputStream());
            byte[] bytes;
            try {
                bytes = provider.read(Wire.read(input));
                if (delay > 0) Thread.sleep(delay);
                Fault current = fault();
                if (!"NORMAL".equals(current.mode())) {
                    injected.incrementAndGet();
                    if ("DISCONNECT".equals(current.mode())) return;
                    if ("SLOW".equals(current.mode())) Thread.sleep(current.delayMs());
                    if ("CORRUPT".equals(current.mode()) && bytes.length > 0) {
                        bytes = bytes.clone(); bytes[0] ^= 1;
                    }
                }
            } catch (Exception e) {
                output.writeByte(1);
                String message = Objects.toString(e.getMessage(), "Không thể phục vụ chunk.");
                output.writeUTF(message.substring(0, Math.min(500, message.length())));
                output.flush();
                return;
            }
            output.writeByte(0);
            output.writeInt(bytes.length);
            output.write(bytes);
            output.flush();
            sentBytes.addAndGet(bytes.length);
        } catch (IOException ignored) {
            // A receiver may cancel a connection when pausing or shutting down.
        } finally {
            sockets.remove(socket);
        }
    }

    private void closeSocket(Socket socket) {
        sockets.remove(socket);
        try { socket.close(); } catch (IOException ignored) {}
    }

    public int port() { return listener.getLocalPort(); }
    public long sentBytes() { return sentBytes.get(); }
    public Map<String, Object> metrics() {
        return Map.of("accepted", accepted.get(), "rejected", rejected.get(), "connections", sockets.size(),
                "activeWorkers", workers.getActiveCount(), "queued", workers.getQueue().size(), "workerLimit", 4,
                "queueLimit", 16, "sentBytes", sentBytes.get(), "injected", injected.get(),
                "remotes", sockets.stream().map(s -> Objects.toString(s.getRemoteSocketAddress(), "")).sorted().toList());
    }

    @Override public void close() throws IOException {
        closing = true;
        listener.close();
        sockets.forEach(this::closeSocket);
        acceptor.shutdownNow();
        workers.shutdownNow();
    }
}
