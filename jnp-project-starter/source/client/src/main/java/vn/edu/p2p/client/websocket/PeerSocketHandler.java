package vn.edu.p2p.client.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import vn.edu.p2p.common.realtime.SnapshotSocket;
import vn.edu.p2p.client.service.PeerEngine;

@Component
public class PeerSocketHandler extends SnapshotSocket {
    private final PeerEngine engine;

    public PeerSocketHandler(ObjectMapper json, PeerEngine engine) {
        super(json, "peer-websocket");
        this.engine = engine;
    }

    public boolean allowed(HttpSession session) {
        try {
            return session != null && Boolean.TRUE.equals(session.getAttribute("authenticated")) && engine.loggedIn();
        } catch (IllegalStateException e) { return false; }
    }

    @Override protected Object snapshot(WebSocketSession socket) {
        if (!allowed((HttpSession) socket.getAttributes().get("httpSession")))
            throw new SecurityException("Phiên peer hết hạn.");
        return engine.status();
    }

    @Override @PreDestroy public void close() { super.close(); }
}
