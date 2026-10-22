package vn.edu.p2p.common.concurrent;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Explicit names make pool ownership observable in thread dumps and a live demo. */
public final class NamedThreads {
    private NamedThreads() {}

    public static ThreadFactory factory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return task -> new Thread(task, prefix + "-" + sequence.incrementAndGet());
    }

    public static ThreadPoolExecutor boundedPool(String prefix, int size, int queueCapacity) {
        return new ThreadPoolExecutor(size, size, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity), factory(prefix), new ThreadPoolExecutor.AbortPolicy());
    }
}
