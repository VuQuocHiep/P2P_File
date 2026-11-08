package vn.edu.p2p.client.network;

import vn.edu.p2p.common.protocol.ChunkRequest;
import vn.edu.p2p.common.model.Manifest;
import vn.edu.p2p.common.protocol.Wire;
import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** One bounded, verified chunk transaction per socket; no assumptions about TCP read boundaries. */
public final class TcpChunkClient implements AutoCloseable {
    private final AtomicLong requests = new AtomicLong(), failures = new AtomicLong();
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private volatile boolean closing;
    private final AtomicLong receivedBytes = new AtomicLong();
    private final AtomicLong readBytes = new AtomicLong();
    private final BandwidthLimiter bandwidth;

    public TcpChunkClient() {
        this(new BandwidthLimiter());
    }

    public TcpChunkClient(BandwidthLimiter bandwidth) {
        this.bandwidth = bandwidth;
    }

    public byte[] fetch(String host, int port, String ticket, Manifest manifest, int index)
            throws IOException {
        return fetch(host, port, ticket, manifest, index, () -> false);
    }

    public byte[] fetch(
            String host, int port, String ticket, Manifest manifest, int index,
            BooleanSupplier cancelled) throws IOException {
        int expected = manifest.length(index);
        requests.incrementAndGet();
        try (Socket socket = new Socket()) {
            sockets.add(socket);
            try {
                if (closing) throw new IOException("Ứng dụng peer đang dừng.");
                socket.connect(new InetSocketAddress(host, port), 2000);
                socket.setSoTimeout(8000);
                var input = new DataInputStream(socket.getInputStream());
                var output = new DataOutputStream(socket.getOutputStream());
                Wire.write(output, new ChunkRequest(ticket, manifest.id(), index));
                if (input.readUnsignedByte() != 0) throw new IOException(input.readUTF());
                if (input.readInt() != expected) throw new IOException("Kích thước chunk sai.");
                byte[] bytes = new byte[expected];
                int offset = 0;
                while (offset < expected) {
                    // Size the next read from ready bytes, not the whole grant: a slow sender
                    // must still update live counters. A minimum of 1 also detects EOF correctly.
                    int requested = Math.min(expected - offset, Math.max(1, input.available()));
                    int grant =
                            bandwidth.acquire(
                                    requested,
                                    () -> closing || socket.isClosed() || cancelled.getAsBoolean());
                    int count = input.readNBytes(bytes, offset, grant);
                    readBytes.addAndGet(count);
                    if (count != grant) throw new EOFException("Dữ liệu chunk chưa đầy đủ.");
                    offset += count;
                }
                if (!Manifest.hash(bytes).equals(manifest.hashes().get(index)))
                    throw new IOException("SHA-256 chunk sai.");
                receivedBytes.addAndGet(bytes.length);
                return bytes;
            } finally {
                sockets.remove(socket);
            }
        } catch (IOException e) {
            failures.incrementAndGet();
            throw e;
        }
    }

    public Map<String, Object> metrics() {
        return Map.of(
                "requests",
                requests.get(),
                "failures",
                failures.get(),
                "connections",
                sockets.size(),
                "receivedBytes",
                receivedBytes.get());
    }

    /** Payload consumed from sockets, including chunks not yet fully verified. */
    public long readBytes() {
        return readBytes.get();
    }

    @Override
    public void close() {
        closing = true;
        bandwidth.close();
        sockets.forEach(
                socket -> {
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                });
    }
}
