package vn.edu.p2p.client.controller;

import vn.edu.p2p.client.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

final class PeerRequestAuth {
    static void auth(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null || !Boolean.TRUE.equals(session.getAttribute("authenticated")))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Hãy đăng nhập vào peer này.");
    }
}
