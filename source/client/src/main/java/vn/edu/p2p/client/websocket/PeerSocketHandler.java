package vn.edu.p2p.client.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import vn.edu.p2p.common.realtime.SnapshotSocket;
import vn.edu.p2p.client.service.PeerSessionService;
import vn.edu.p2p.client.service.PeerStatusService;

@Component
public class PeerSocketHandler extends SnapshotSocket {
    private final PeerSessionService sessionService;
    private final PeerStatusService statusService;

    public PeerSocketHandler(
            ObjectMapper json, PeerSessionService sessionService, PeerStatusService statusService) {
        super(json, "peer-websocket");
        this.sessionService = sessionService;
        this.statusService = statusService;
    }

    public boolean allowed(HttpSession session) {
        try {
            return session != null
                    && Boolean.TRUE.equals(session.getAttribute("authenticated"))
                    && sessionService.loggedIn();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    @Override
    protected Object snapshot(WebSocketSession socket) {
        if (!allowed((HttpSession) socket.getAttributes().get("httpSession")))
            throw new SecurityException("Phiên peer hết hạn.");
        return statusService.status();
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
    }
}
