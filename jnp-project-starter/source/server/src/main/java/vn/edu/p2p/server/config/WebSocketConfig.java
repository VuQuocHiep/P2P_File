package vn.edu.p2p.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.*;
import org.springframework.http.server.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import vn.edu.p2p.server.websocket.AdminSocketHandler;
import java.util.Map;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final AdminSocketHandler handler;
    public WebSocketConfig(AdminSocketHandler handler) { this.handler = handler; }

    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Default origin policy is same-origin. Never use wildcard origins for cookie authentication.
        registry.addHandler(handler, "/ws/admin").addInterceptors(new HandshakeInterceptor() {
            @Override public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                    WebSocketHandler ws, Map<String, Object> attributes) {
                if (!(request instanceof ServletServerHttpRequest servlet)) return false;
                var session = servlet.getServletRequest().getSession(false);
                if (!handler.allowed(session)) { response.setStatusCode(HttpStatus.UNAUTHORIZED); return false; }
                attributes.put("httpSession", session);
                return true;
            }
            @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                    WebSocketHandler ws, Exception exception) {}
        });
    }
}
