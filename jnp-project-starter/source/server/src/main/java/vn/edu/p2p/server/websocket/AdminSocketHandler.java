package vn.edu.p2p.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import vn.edu.p2p.common.realtime.SnapshotSocket;
import vn.edu.p2p.server.service.TrackerService;

@Component
public class AdminSocketHandler extends SnapshotSocket {
    private final TrackerService service;

    public AdminSocketHandler(ObjectMapper json, TrackerService service) {
        super(json, "tracker-websocket");
        this.service = service;
    }

    public boolean allowed(HttpSession session) {
        try {
            if (session == null) return false;
            Object token = session.getAttribute("token");
            return token instanceof String value && "ADMIN".equals(service.me(value).get("role"));
        } catch (Exception e) { return false; }
    }

    @Override protected Object snapshot(WebSocketSession socket) {
        HttpSession session = (HttpSession) socket.getAttributes().get("httpSession");
        if (!allowed(session)) throw new SecurityException("Phiên quản trị hết hạn.");
        try { return service.dashboard((String) session.getAttribute("token")); }
        catch (Exception e) { throw new SecurityException("Quyền quản trị không còn hợp lệ."); }
    }

    @Override @PreDestroy public void close() { super.close(); }
}
