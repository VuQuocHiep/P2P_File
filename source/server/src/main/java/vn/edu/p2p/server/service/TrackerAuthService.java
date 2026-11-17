package vn.edu.p2p.server.service;

import vn.edu.p2p.server.dto.request.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.p2p.server.model.*;
import vn.edu.p2p.server.entity.*;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.edu.p2p.server.dto.response.*;
import vn.edu.p2p.server.mapper.AccountMapper;

/** Registry operations serialized on the shared context monitor. */
@Service
public class TrackerAuthService {
    private final TrackerContext context;
    private final TrackerAccess access;
    private final AccountMapper accountMapper;

    public TrackerAuthService(
            TrackerContext context, TrackerAccess access, AccountMapper accountMapper) {
        this.context = context;
        this.access = access;
        this.accountMapper = accountMapper;
    }

    public RegistrationResponse register(Login input) {
        synchronized (context) {
            var transaction = context.beginTransaction();
            try {
                access.checkName(input.username());
                access.require(
                        input.password() != null
                                && input.password().length() >= 8
                                && input.password()
                                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)
                                                .length
                                        <= 72,
                        "Mật khẩu cần ít nhất 8 ký tự, tối đa 72 byte UTF-8.");
                access.require(
                        !context.accounts.existsByUsername(input.username()),
                        "Tên tài khoản đã tồn tại.");
                var account =
                        new AccountEntity(
                                input.username(),
                                context.passwords.encode(input.password()),
                                "USER",
                                "PENDING");
                account.position = context.nextAccountPosition++;
                context.accounts.save(account);
                context.log(input.username(), "REGISTER", input.username());
                return new RegistrationResponse(
                        "Đăng ký thành công. Chờ quản trị viên duyệt.", "PENDING");
            } catch (Exception | Error failure) {
                context.rollback(transaction);
                throw failure;
            } finally {
                context.commit(transaction);
            }
        }
    }

    public LoginResponse login(Login input) {
        synchronized (context) {
            var transaction = context.beginTransaction();
            try {
                AccountEntity a = context.accounts.findByUsername(input.username()).orElse(null);
                if (a == null
                        || input.password() == null
                        || input.password().length() > 100
                        || !context.passwords.matches(input.password(), a.passwordHash))
                    throw new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED, "Sai tài khoản hoặc mật khẩu.");
                if ("BLOCKED".equals(a.status))
                    throw new ResponseStatusException(
                            HttpStatus.FORBIDDEN, "Tài khoản đã bị khóa.");
                context.sessions
                        .entrySet()
                        .removeIf(e -> e.getValue().expires() < System.currentTimeMillis());
                String token = UUID.randomUUID() + "-" + UUID.randomUUID();
                context.sessions.put(
                        token,
                        new Session(a.username, System.currentTimeMillis() + 12 * 3600_000L));
                context.log(a.username, "LOGIN", a.username);
                return new LoginResponse(token, accountMapper.toResponse(a));
            } catch (Exception | Error failure) {
                context.rollback(transaction);
                throw failure;
            } finally {
                context.commit(transaction);
            }
        }
    }

    public void logout(String token) {
        synchronized (context) {
            var transaction = context.beginTransaction();
            try {
                context.sessions.remove(token);
            } catch (Exception | Error failure) {
                context.rollback(transaction);
                throw failure;
            } finally {
                context.commit(transaction);
            }
        }
    }

    public UserResponse me(String token) {
        synchronized (context) {
            var transaction = context.beginTransaction();
            try {
                return accountMapper.toResponse(access.account(token, false));
            } catch (Exception | Error failure) {
                context.rollback(transaction);
                throw failure;
            } finally {
                context.commit(transaction);
            }
        }
    }
}
