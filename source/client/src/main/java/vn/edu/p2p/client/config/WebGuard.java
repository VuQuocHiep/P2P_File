package vn.edu.p2p.client.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import java.io.IOException;
import vn.edu.p2p.client.service.PeerSessionService;

@Component
class WebGuard extends OncePerRequestFilter {
    private final PeerSessionService sessions;

    WebGuard(PeerSessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader(
                "Content-Security-Policy",
                "default-src 'self'; connect-src 'self' ws: wss:; script-src 'self'; style-src 'self'; img-src 'self' data:; frame-ancestors 'none'; object-src 'none'; base-uri 'self'");
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        if (path.startsWith("/api/") || request.getRequestURI().startsWith("/api/")) {
            response.setHeader("Cache-Control", "no-store");
            if (!java.util.Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())
                    && !"1".equals(request.getHeader("X-PeerMesh"))) {
                response.sendError(403, "Thiếu header X-PeerMesh.");
                return;
            }
            boolean exclusive = path.equals("/api/auth/login") || path.equals("/api/auth/logout");
            var lock = sessions.sessionLock(exclusive);
            lock.lock();
            try {
                var session = request.getSession(false);
                if (session != null && sessions.ownsSession(
                        session.getAttribute(PeerSessionService.SESSION_ATTRIBUTE)))
                    request.setAttribute(PeerSessionService.SESSION_ATTRIBUTE, true);
                chain.doFilter(request, response);
            } finally {
                lock.unlock();
            }
            return;
        }
        chain.doFilter(request, response);
    }
}
