package vn.edu.p2p.common.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;
import vn.edu.p2p.common.concurrent.NamedThreads;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Authenticated snapshot stream, application refresh requests, and RFC 6455 ping/pong. */
public abstract class SnapshotSocket extends TextWebSocketHandler implements AutoCloseable {
    private final ObjectMapper json;
    private final ScheduledExecutorService timer;
    private final Map<String, SnapshotConnection> connections = new ConcurrentHashMap<>();
    private final AtomicLong sequences = new AtomicLong();
    private boolean closing;

    protected SnapshotSocket(ObjectMapper json, String threadName) {
        this.json = json;
        timer = Executors.newScheduledThreadPool(2, NamedThreads.factory(threadName));
    }

    /** Must revalidate the HTTP session/token on every push, not only at the handshake. */
    protected abstract Object snapshot(WebSocketSession session) throws Exception;

    @Override public synchronized void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (closing) { session.close(CloseStatus.GOING_AWAY); return; }
        if (connections.size() >= 32) { session.close(new CloseStatus(1013, "Server busy")); return; }
        var connection = new SnapshotConnection(session);
        connections.put(session.getId(), connection);
        connection.task = timer.scheduleWithFixedDelay(() -> push(connection), 0, 1000, TimeUnit.MILLISECONDS);
    }

    private void push(SnapshotConnection connection) {
        synchronized (connection) {
            try {
                if (!connection.socket.isOpen()) { remove(connection); return; }
                Object data = snapshot(connection.socket);
                long now = System.currentTimeMillis();
                if (now - connection.pong > 45_000) { disconnect(connection, CloseStatus.SESSION_NOT_RELIABLE); return; }
                String payload = json.writeValueAsString(Map.of("type", "snapshot", "sequence", sequences.incrementAndGet(),
                        "time", now, "data", data));
                connection.socket.sendMessage(new TextMessage(payload));
                if (now - connection.ping >= 15_000) {
                    connection.socket.sendMessage(new PingMessage(ByteBuffer.wrap(new byte[]{80, 77})));
                    connection.ping = now;
                }
            } catch (SecurityException | IllegalStateException e) {
                disconnect(connection, CloseStatus.POLICY_VIOLATION);
            } catch (Exception e) {
                disconnect(connection, CloseStatus.SERVER_ERROR);
            }
        }
    }

    @Override protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        SnapshotConnection connection = connections.get(session.getId());
        if (connection == null) return;
        if (!"{\"type\":\"refresh\"}".equals(message.getPayload())) {
            disconnect(connection, CloseStatus.BAD_DATA);
            return;
        }
        synchronized (connection) {
            if (System.currentTimeMillis() - connection.lastRefresh < 250) return;
            connection.lastRefresh = System.currentTimeMillis();
        }
        // Request parsing runs on a container thread; push serialization stays in our dedicated scheduler.
        // Coalesce requests: at most one queued refresh per session, even with a slow consumer.
        if (!connection.refreshPending.compareAndSet(false, true)) return;
        try {
            timer.execute(() -> {
                try { push(connection); }
                finally { connection.refreshPending.set(false); }
            });
        } catch (RejectedExecutionException ignored) {
            connection.refreshPending.set(false);
            disconnect(connection, CloseStatus.GOING_AWAY);
        }
    }

    @Override protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        SnapshotConnection connection = connections.get(session.getId());
        if (connection != null) connection.pong = System.currentTimeMillis();
    }

    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SnapshotConnection connection = connections.remove(session.getId());
        if (connection != null && connection.task != null) connection.task.cancel(false);
    }

    @Override public void handleTransportError(WebSocketSession session, Throwable exception) {
        SnapshotConnection connection = connections.get(session.getId());
        if (connection != null) disconnect(connection, CloseStatus.SERVER_ERROR);
    }

    private void remove(SnapshotConnection connection) {
        connections.remove(connection.socket.getId());
        if (connection.task != null) connection.task.cancel(false);
    }

    private void disconnect(SnapshotConnection connection, CloseStatus status) {
        remove(connection);
        try { if (connection.socket.isOpen()) connection.socket.close(status); }
        catch (Exception ignored) {}
    }

    public int connectionCount() { return connections.size(); }

    @Override public synchronized void close() {
        closing = true;
        connections.values().forEach(c -> disconnect(c, CloseStatus.GOING_AWAY));
        timer.shutdownNow();
    }
}
