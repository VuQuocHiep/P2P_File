package vn.edu.p2p.server.service;

import vn.edu.p2p.server.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.p2p.server.model.*;
import java.util.*;

/** Registry operations invoked under the TrackerService monitor. */
final class TrackerAuthService {
    private final TrackerContext context;
    private final TrackerAccess access;
    TrackerAuthService(TrackerContext context, TrackerAccess access) {
        this.context = context;
        this.access = access;
    }
    Map<String, Object> register(Login input) {
        access.checkName(input.username());
        access.require(input.password() != null && input.password().length() >= 8
                && input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72,
                "Mật khẩu cần ít nhất 8 ký tự, tối đa 72 byte UTF-8.");
        access.require(!context.state.accounts.containsKey(input.username()), "Tên tài khoản đã tồn tại.");
        context.state.accounts.put(input.username(), new Account(input.username(), context.passwords.encode(input.password()), "USER", "PENDING"));
        context.store.log(input.username(), "REGISTER", input.username()); context.store.save();
        return Map.of("message", "Đăng ký thành công. Chờ quản trị viên duyệt.", "status", "PENDING");
    }
    Map<String, Object> login(Login input) {
        Account a = context.state.accounts.get(input.username());
        if (a == null || input.password() == null || input.password().length() > 100
                || !context.passwords.matches(input.password(), a.passwordHash))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sai tài khoản hoặc mật khẩu.");
        if ("BLOCKED".equals(a.status)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản đã bị khóa.");
        context.sessions.entrySet().removeIf(e -> e.getValue().expires() < System.currentTimeMillis());
        String token = UUID.randomUUID() + "-" + UUID.randomUUID();
        context.sessions.put(token, new Session(a.username, System.currentTimeMillis() + 12 * 3600_000L));
        context.store.log(a.username, "LOGIN", a.username); context.store.save();
        return Map.of("token", token, "user", access.userView(a));
    }
    void logout(String token) { context.sessions.remove(token); }
    Map<String, Object> me(String token) { return access.userView(access.account(token, false)); }
}
