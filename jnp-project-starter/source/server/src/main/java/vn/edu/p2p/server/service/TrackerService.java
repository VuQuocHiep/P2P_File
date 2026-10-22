package vn.edu.p2p.server.service;

import vn.edu.p2p.server.repository.TrackerState;
import vn.edu.p2p.server.dto.TrackerRequests.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.p2p.common.model.Manifest;
import vn.edu.p2p.server.model.TrackerModels.*;
import java.net.InetAddress;
import java.util.*;

@Service
public class TrackerService {
    private final TrackerState store;
    private final Snapshot state;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    private final Map<String, Session> sessions = new HashMap<>();
    private final Map<String, Ticket> tickets = new HashMap<>();
    private record Session(String username, long expires) {}
    private record Ticket(String username, String sourceId, String fileId, long expires) {}

    public TrackerService(TrackerState store, @Value("${peermesh.admin.username}") String admin,
                          @Value("${peermesh.admin.password}") String password) {
        this.store = store; state = store.snapshot();
        if (state.accounts.values().stream().noneMatch(a -> "ADMIN".equals(a.role))) {
            checkName(admin);
            require(password.length() >= 12 && password.length() <= 72,
                    "Lần chạy đầu cần ADMIN_PASSWORD dài 12–72 ký tự.");
            state.accounts.put(admin, new Account(admin, passwords.encode(password), "ADMIN", "APPROVED"));
            store.log(admin, "BOOTSTRAP_ADMIN", admin); store.save();
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
    private static void checkName(String value) {
        require(value != null && value.matches("[a-zA-Z0-9_.-]{3,40}"), "Tên tài khoản cần 3–40 ký tự chữ, số, _, . hoặc -.");
    }
    public synchronized Map<String, Object> register(Login input) {
        checkName(input.username());
        require(input.password() != null && input.password().length() >= 8
                && input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72,
                "Mật khẩu cần ít nhất 8 ký tự, tối đa 72 byte UTF-8.");
        require(!state.accounts.containsKey(input.username()), "Tên tài khoản đã tồn tại.");
        state.accounts.put(input.username(), new Account(input.username(), passwords.encode(input.password()), "USER", "PENDING"));
        store.log(input.username(), "REGISTER", input.username()); store.save();
        return Map.of("message", "Đăng ký thành công. Chờ quản trị viên duyệt.", "status", "PENDING");
    }
    public synchronized Map<String, Object> login(Login input) {
        Account a = state.accounts.get(input.username());
        if (a == null || input.password() == null || input.password().length() > 100
                || !passwords.matches(input.password(), a.passwordHash))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sai tài khoản hoặc mật khẩu.");
        if ("BLOCKED".equals(a.status)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản đã bị khóa.");
        sessions.entrySet().removeIf(e -> e.getValue().expires < System.currentTimeMillis());
        String token = UUID.randomUUID() + "-" + UUID.randomUUID();
        sessions.put(token, new Session(a.username, System.currentTimeMillis() + 12 * 3600_000L));
        store.log(a.username, "LOGIN", a.username); store.save();
        return Map.of("token", token, "user", userView(a));
    }
    public synchronized void logout(String token) { sessions.remove(token); }
    private Account account(String token, boolean approved) {
        Session session = sessions.get(token);
        if (session == null || session.expires < System.currentTimeMillis())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cần đăng nhập lại.");
        Account a = state.accounts.get(session.username);
        if (a == null || "BLOCKED".equals(a.status) || (approved && !"APPROVED".equals(a.status)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản chưa được duyệt hoặc đã bị khóa.");
        return a;
    }
    private Account admin(String token) {
        Account a = account(token, true);
        if (!"ADMIN".equals(a.role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ dành cho quản trị viên.");
        return a;
    }
    private Peer ownPeer(Account a, String id, boolean approved) {
        Peer p = state.peers.get(id);
        if (p == null || !p.owner.equals(a.username) || (approved && !"APPROVED".equals(p.status)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Peer không thuộc tài khoản hoặc chưa được duyệt.");
        return p;
    }
    private Map<String, Object> userView(Account a) {
        return Map.of("username", a.username, "role", a.role, "status", a.status, "created", a.created);
    }
    public synchronized Map<String, Object> me(String token) { return userView(account(token, false)); }
    public synchronized Map<String, Object> registerPeer(String token, RegisterPeer input) throws Exception {
        Account a = account(token, true);
        require(input.name() != null && !input.name().isBlank() && input.name().length() <= 60, "Tên peer cần 1–60 ký tự.");
        require(input.host() != null && input.host().length() <= 45 && input.host().matches("[0-9a-fA-F:.]+"), "Host phải là địa chỉ IP.");
        var address = InetAddress.getByName(input.host());
        require(!address.isAnyLocalAddress() && !address.isMulticastAddress(), "Địa chỉ IP không hợp lệ.");
        require(input.port() >= 1024 && input.port() <= 65535, "TCP port cần trong khoảng 1024–65535.");
        String id = input.id();
        Peer p;
        if (id == null || id.isBlank()) {
            p = new Peer(); p.id = UUID.randomUUID().toString(); p.owner = a.username; p.status = "PENDING";
            state.peers.put(p.id, p);
        } else {
            p = ownPeer(a, id, false);
            if (!Objects.equals(p.host, input.host()) || p.port != input.port()) p.status = "PENDING";
        }
        p.name = input.name(); p.host = input.host(); p.port = input.port(); p.lastSeen = System.currentTimeMillis();
        store.log(a.username, "REGISTER_PEER", p.id); store.save(); return peerView(p);
    }
    private boolean online(Peer p) {
        Account owner = state.accounts.get(p.owner);
        return "APPROVED".equals(p.status) && owner != null && "APPROVED".equals(owner.status)
                && System.currentTimeMillis() - p.lastSeen < 20_000;
    }
    private Map<String, Object> peerView(Peer p) {
        return Map.of("id", p.id, "owner", p.owner, "name", p.name, "host", p.host,
                "port", p.port, "status", p.status, "online", online(p), "lastSeen", p.lastSeen);
    }
    public synchronized Map<String, Object> heartbeat(String token, Heartbeat input) {
        Peer p = ownPeer(account(token, true), input.peerId(), false);
        p.lastSeen = System.currentTimeMillis(); store.save(); return peerView(p);
    }
    public synchronized Map<String, Object> advertise(String token, Advertisement input) {
        Account a = account(token, true); Peer p = ownPeer(a, input.peerId(), true);
        require(input.manifest() != null, "Thiếu manifest."); input.manifest().validate();
        require(input.chunks() != null && input.chunks().size() <= input.manifest().hashes().size()
                && new HashSet<>(input.chunks()).size() == input.chunks().size()
                && input.chunks().stream().allMatch(i -> i != null && i >= 0 && i < input.manifest().hashes().size()), "Danh sách chunk không hợp lệ.");
        SharedFile file = state.files.get(input.manifest().id());
        if (file == null) {
            file = new SharedFile(); file.manifest = input.manifest(); file.status = "PENDING"; file.submittedBy = a.username;
            state.files.put(file.manifest.id(), file); store.log(a.username, "SUBMIT_FILE", file.manifest.name());
        } else require(file.manifest.sameContent(input.manifest()), "Manifest xung đột với file đã đăng ký.");
        file.holders.put(p.id, List.copyOf(input.chunks())); p.lastSeen = System.currentTimeMillis(); store.save();
        return fileView(file);
    }
    public synchronized void unshare(String token, String peerId, String fileId) {
        ownPeer(account(token, true), peerId, false);
        SharedFile f = state.files.get(fileId);
        if (f != null) { f.holders.remove(peerId); store.save(); }
    }
    private Map<String, Object> fileView(SharedFile f) {
        long sources = f.holders.keySet().stream().map(state.peers::get).filter(Objects::nonNull).filter(this::online).count();
        return Map.of("manifest", f.manifest, "status", f.status, "submittedBy", f.submittedBy,
                "sources", sources, "holders", f.holders.size());
    }
    public synchronized List<Map<String, Object>> files(String token, String query) {
        account(token, true); String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return state.files.values().stream().filter(f -> "APPROVED".equals(f.status)
                && f.manifest.name().toLowerCase(Locale.ROOT).contains(q)).map(this::fileView).toList();
    }
    public synchronized List<Map<String, Object>> sources(String token, String fileId) {
        account(token, true); SharedFile f = state.files.get(fileId);
        require(f != null && "APPROVED".equals(f.status), "File chưa được duyệt hoặc đã bị ẩn.");
        var result = new ArrayList<Map<String, Object>>();
        f.holders.forEach((id, chunks) -> {
            Peer p = state.peers.get(id);
            if (p != null && online(p)) {
                var view = new LinkedHashMap<>(peerView(p)); view.put("chunks", chunks); result.add(view);
            }
        }); return result;
    }
    public synchronized Map<String, Object> ticket(String token, TicketRequest input) {
        Account a = account(token, true); SharedFile f = state.files.get(input.fileId()); Peer p = state.peers.get(input.sourceId());
        require(f != null && "APPROVED".equals(f.status) && p != null && online(p) && f.holders.containsKey(p.id), "Nguồn không khả dụng.");
        tickets.entrySet().removeIf(e -> e.getValue().expires < System.currentTimeMillis());
        String ticket = UUID.randomUUID().toString();
        tickets.put(ticket, new Ticket(a.username, p.id, f.manifest.id(), System.currentTimeMillis() + 60_000));
        return Map.of("ticket", ticket, "expiresIn", 60);
    }
    public synchronized Map<String, Object> authorize(String token, CheckTicket input) {
        Peer p = ownPeer(account(token, true), input.peerId(), true); Ticket ticket = tickets.get(input.ticket());
        SharedFile f = state.files.get(input.fileId());
        if (ticket == null || ticket.expires < System.currentTimeMillis() || !ticket.sourceId.equals(p.id)
                || !ticket.fileId.equals(input.fileId()) || f == null || !"APPROVED".equals(f.status)
                || !f.holders.containsKey(p.id) || !"APPROVED".equals(state.accounts.get(ticket.username).status))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vé tải hết hạn hoặc quyền tải đã bị thu hồi.");
        return Map.of("allowed", true);
    }
    public synchronized void report(String token, Progress input) {
        Account a = account(token, true); ownPeer(a, input.peerId(), true);
        SharedFile f = state.files.get(input.fileId());
        require(input.id() != null && input.id().matches("[a-zA-Z0-9-]{1,64}") && f != null
                && Set.of("DOWNLOADING", "WAITING", "PAUSED", "COMPLETED", "ERROR").contains(input.status())
                && input.bytes() >= 0 && input.bytes() <= f.manifest.size(), "Báo cáo tiến độ không hợp lệ.");
        String key = input.peerId() + ":" + input.id();
        state.transfers.put(key, new Transfer(input.id(), a.username, input.peerId(), input.fileId(),
                f.manifest.name(), input.status(), input.bytes(), System.currentTimeMillis()));
        while (state.transfers.size() > 1000) state.transfers.remove(state.transfers.keySet().iterator().next());
        store.save();
    }
    public synchronized Map<String, Object> dashboard(String token) {
        admin(token);
        return Map.of("accounts", state.accounts.values().stream().map(this::userView).toList(),
                "peers", state.peers.values().stream().map(this::peerView).toList(),
                "files", state.files.values().stream().map(this::fileView).toList(),
                "transfers", new ArrayList<>(state.transfers.values()),
                "audit", state.audit.stream().sorted(Comparator.comparingLong(Audit::time).reversed()).limit(100).toList());
    }
    public synchronized void moderate(String token, String kind, String id, String status) {
        Account a = admin(token);
        require(Set.of("APPROVED", "PENDING", "BLOCKED").contains(status), "Trạng thái không hợp lệ.");
        switch (kind) {
            case "accounts" -> {
                Account target = state.accounts.get(id); require(target != null, "Không có tài khoản.");
                require(!"ADMIN".equals(target.role), "Không thay đổi trạng thái quản trị viên."); target.status = status;
            }
            case "peers" -> { Peer p = state.peers.get(id); require(p != null, "Không có peer."); p.status = status; }
            case "files" -> { SharedFile f = state.files.get(id); require(f != null, "Không có file."); f.status = status; }
            default -> throw new IllegalArgumentException("Loại đối tượng không hợp lệ.");
        }
        store.log(a.username, kind + "_" + status, id); store.save();
    }
}
