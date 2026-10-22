package vn.edu.p2p.client.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.io.IOException;

@Component
class WebGuard extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff"); response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Content-Security-Policy", "default-src 'self'; connect-src 'self' ws: wss:; script-src 'self'; style-src 'self'; img-src 'self' data:; frame-ancestors 'none'; object-src 'none'; base-uri 'self'");
        if (request.getRequestURI().startsWith("/api/")) {
            response.setHeader("Cache-Control", "no-store");
            if (!java.util.Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod()) && !"1".equals(request.getHeader("X-PeerMesh"))) {
                response.sendError(403, "Thiếu header X-PeerMesh."); return;
            }
        }
        chain.doFilter(request, response);
    }
}

