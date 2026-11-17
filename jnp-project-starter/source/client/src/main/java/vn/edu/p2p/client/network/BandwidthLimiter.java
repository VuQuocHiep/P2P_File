package vn.edu.p2p.client.network;

import java.io.InterruptedIOException;
import java.util.ArrayDeque;
import java.util.function.BooleanSupplier;

/** One token bucket shared by all payload transfers in a direction; 0 means unlimited. */
public final class BandwidthLimiter implements AutoCloseable {
    public static final long MAX_LIMIT_KIB = 1_048_576;
    private final Object monitor = new Object();
    private final ArrayDeque<Object> waiters = new ArrayDeque<>();
    private long bytesPerSecond;
    private long updatedAt = System.nanoTime();
    private double tokens;
    private boolean closed;

    public BandwidthLimiter() {}

    public BandwidthLimiter(long limitKiB) {
        setLimit(limitKiB);
    }

    public void setLimit(long limitKiB) {
        if (limitKiB < 0 || limitKiB > MAX_LIMIT_KIB)
            throw new IllegalArgumentException("Giới hạn cần trong khoảng 0–1048576 KiB/s.");
        synchronized (monitor) {
            bytesPerSecond = limitKiB * 1024;
            tokens = quantum();
            updatedAt = System.nanoTime();
            monitor.notifyAll();
        }
    }

    private int quantum() {
        // Short grants keep every connection progressing within the existing read timeout.
        return (int) Math.min(8192, Math.max(1, bytesPerSecond / 16));
    }

    public int acquire(int requested, BooleanSupplier cancelled) throws InterruptedIOException {
        if (requested <= 0) throw new IllegalArgumentException("Cần số byte dương.");
        Object waiter = new Object();
        synchronized (monitor) {
            waiters.addLast(waiter);
        }
        try {
            while (true) {
                // The callback may acquire the profile monitor: never call it under our lock.
                if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean())
                    throw new InterruptedIOException("Đã dừng truyền chunk.");
                synchronized (monitor) {
                    if (closed) throw new InterruptedIOException("Kết nối TCP đang dừng.");
                    if (bytesPerSecond == 0) return Math.min(requested, 8192);
                    long now = System.nanoTime();
                    tokens =
                            Math.min(
                                    quantum(),
                                    tokens + (now - updatedAt) / 1_000_000_000.0 * bytesPerSecond);
                    updatedAt = now;
                    int grant = Math.min(requested, quantum());
                    if (waiters.peekFirst() == waiter && tokens >= grant) {
                        tokens -= grant;
                        return grant;
                    }
                    // Waiting releases the monitor; recheck cancellation and changed limits promptly.
                    try {
                        long waitNanos = 25_000_000L;
                        if (waiters.peekFirst() == waiter) {
                            long missingNanos =
                                    (long) Math.ceil((grant - tokens) * 1_000_000_000.0 / bytesPerSecond);
                            waitNanos = Math.min(waitNanos, Math.max(1, missingNanos));
                        }
                        monitor.wait(waitNanos / 1_000_000, (int) (waitNanos % 1_000_000));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedIOException("Đã dừng truyền chunk.");
                    }
                }
            }
        } finally {
            synchronized (monitor) {
                waiters.remove(waiter);
                monitor.notifyAll();
            }
        }
    }

    @Override
    public void close() {
        synchronized (monitor) {
            closed = true;
            monitor.notifyAll();
        }
    }
}
