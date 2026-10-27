package vn.edu.p2p.common.realtime;

import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

final class SnapshotConnection {
    final WebSocketSession socket;
    volatile long pong = System.currentTimeMillis();
    long ping, lastRefresh;
    ScheduledFuture<?> task;
    final AtomicBoolean refreshPending = new AtomicBoolean();
    SnapshotConnection(WebSocketSession raw) {
        socket = new ConcurrentWebSocketSessionDecorator(raw, 2000, 1024 * 1024);
    }
}

