package vn.edu.p2p.server.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import vn.edu.p2p.server.dto.*;

final class TrackerRequestAuth {
    static String token(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) return header.substring(7);
        var session = request.getSession(false);
        return session == null ? "" : Objects.toString(session.getAttribute("token"), "");
    }
}
