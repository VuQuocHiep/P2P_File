package vn.edu.p2p.client.controller;

import vn.edu.p2p.client.dto.request.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.p2p.client.service.PeerSessionService;

final class PeerRequestAuth {
    static boolean authenticated(HttpServletRequest request) {
        return Boolean.TRUE.equals(request.getAttribute(PeerSessionService.SESSION_ATTRIBUTE));
    }

    static void auth(HttpServletRequest request) {
        if (!authenticated(request))
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Hãy đăng nhập vào peer này.");
    }
}
