package vn.edu.p2p.client.service;

import com.fasterxml.jackson.databind.*;
import vn.edu.p2p.client.model.*;
import vn.edu.p2p.client.dto.request.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.edu.p2p.client.dto.response.*;
import vn.edu.p2p.client.exception.TrackerFailure;
import vn.edu.p2p.client.mapper.TrackerResponseMapper;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class PeerSessionService {
    public static final String SESSION_ATTRIBUTE = "peerSessionId";
    private final PeerContext context;
    private final TrackerResponseMapper trackerResponseMapper;
    private final ReentrantReadWriteLock sessionLock = new ReentrantReadWriteLock(true);
    private volatile String sessionId;

    public Lock sessionLock(boolean exclusive) {
        return exclusive ? sessionLock.writeLock() : sessionLock.readLock();
    }

    public String sessionId() {
        return sessionId;
    }

    public boolean ownsSession(Object id) {
        return id instanceof String && id.equals(sessionId) && loggedIn();
    }

    public PeerSessionService(PeerContext context, TrackerResponseMapper trackerResponseMapper) {
        this.context = context;
        this.trackerResponseMapper = trackerResponseMapper;
    }

    public RegistrationResponse register(Registration input) throws Exception {
        return trackerResponseMapper.toRegistrationResponse(
                context.tracker.call("POST", "/api/auth/register", input));
    }

    public boolean loggedIn() {
        return context.user != null && !context.tracker.token().isBlank();
    }

    public synchronized PeerStatusResponse login(Login input) throws Exception {
        if (input.username() == null
                || input.password() == null
                || input.name() == null
                || input.host() == null)
            throw new IllegalArgumentException("Điền đủ thông tin đăng nhập và peer.");
        String username = input.username(),
                password = input.password(),
                name = input.name(),
                host = input.host();
        boolean changingAccount;
        synchronized (context.storage.monitor()) {
            String owner = context.profile.getOwner();
            changingAccount = !owner.isBlank() && !owner.equals(username);
            if (changingAccount && (!context.running.isEmpty() || context.benchmarking.get()
                    || (context.peerMalwareService != null && context.peerMalwareService.isBusy())))
                throw new IllegalArgumentException(
                        "Hãy chờ peer hoàn tất tác vụ đang chạy rồi đăng nhập tài khoản khác.");
        }
        JsonNode result =
                context.tracker.call(
                        "POST",
                        "/api/auth/login",
                        Map.of("username", username, "password", password));
        synchronized (context.storage.monitor()) {
            if (changingAccount) context.storage.selectAccount(username);
            sessionId = null;
            context.profile.update(username, name, host);
            context.storage.saveCheckpoint();
            if (changingAccount) resetAccountRuntime();
        }
        revokeTrackerSession();
        context.tracker.token(result.path("token").asText());
        context.user = result.path("user");
        context.peerStatus = "PENDING";
        context.catalogSnapshot = context.json.createArrayNode();
        try {
            context.peerDeletionService.synchronize();
            if ("APPROVED".equals(context.user.path("status").asText())) connectPeer();
            else context.connection = "Tài khoản đang chờ duyệt";
            PeerStatusResponse status = context.peerStatusService.status();
            context.peerDownloadService.dispatchQueued();
            sessionId = UUID.randomUUID().toString();
            return status;
        } catch (Exception failure) {
            revokeTrackerSession();
            context.user = null;
            context.peerStatus = "PENDING";
            throw failure;
        }
    }

    private void revokeTrackerSession() {
        if (!context.tracker.token().isBlank()) {
            try {
                context.tracker.call("POST", "/api/auth/logout", Map.of());
            } catch (Exception ignored) {
                // The old tracker session may already have expired.
            }
        }
        context.tracker.token("");
    }

    private void resetAccountRuntime() {
        context.uploadBandwidth.setLimit(context.profile.getUploadLimitKiB());
        context.downloadBandwidth.setLimit(context.profile.getDownloadLimitKiB());
        context.tcpServer.fault("NORMAL", 0, 1);
        context.speedBytes.clear();
        context.speedStart.clear();
        context.benchmark = Map.of("status", "IDLE");
        synchronized (context.samples) {
            context.links.clear();
            context.recentLinks.clear();
            context.sourceReceived.clear();
            context.sourceChunks.clear();
            context.sourcePrevious.clear();
            context.sourceStats.clear();
            context.samples.clear();
        }
    }

    void connectPeer() throws Exception {
        String id = peerId();
        try {
            registerPeer(id);
        } catch (TrackerFailure failure) {
            if (id.isBlank() || !peerIdRejected(failure)) throw failure;
            registerPeer("");
        }
    }

    private void registerPeer(String id) throws Exception {
        Map<String, Object> body;
        synchronized (context.storage.monitor()) {
            body =
                    Map.of(
                            "id",
                            id,
                            "name",
                            context.profile.getName(),
                            "host",
                            context.profile.getHost(),
                            "port",
                            context.tcpPort);
        }
        JsonNode result = context.tracker.call("POST", "/api/peers", body);
        String returnedId = result.path("id").asText();
        if (returnedId.isBlank()) throw new IOException("Tracker chưa trả về mã thiết bị.");
        synchronized (context.storage.monitor()) {
            if (!returnedId.equals(context.profile.getPeerId())) {
                context.profile.updatePeerId(returnedId);
                context.events.append("Đăng ký peer " + context.profile.getName());
                context.storage.saveCheckpoint();
            }
        }
        context.peerStatus = result.path("status").asText();
        context.connection = "Đã kết nối tracker";
    }

    private String peerId() {
        synchronized (context.storage.monitor()) {
            return context.profile.getPeerId();
        }
    }

    private boolean peerIdRejected(TrackerFailure failure) {
        return failure.status == 403
                && "Peer không thuộc tài khoản hoặc chưa được duyệt.".equals(failure.getMessage());
    }

    /** Reports progress and re-registers once when the tracker no longer knows this peer. */
    private JsonNode callHeartbeat(String id) throws Exception {
        try {
            return context.tracker.call("POST", "/api/peers/heartbeat", Map.of("peerId", id));
        } catch (TrackerFailure failure) {
            if (!peerIdRejected(failure)) throw failure;
            registerPeer("");
            return context.tracker.call(
                    "POST", "/api/peers/heartbeat", Map.of("peerId", peerId()));
        }
    }

    public void logout() {
        context.tcpServer.fault("NORMAL", 0, 1);
        synchronized (context.storage.monitor()) {
            context.jobs.findAll().stream()
                    .filter(j -> !"COMPLETED".equals(j.status))
                    .forEach(j -> j.status = "PAUSED");
            context.storage.saveCheckpoint();
        }
        sessionId = null;
        revokeTrackerSession();
        context.user = null;
        context.catalogSnapshot = context.json.createArrayNode();
        context.connection = "Đã đăng xuất";
    }

    void heartbeat() {
        Lock lock = sessionLock(false);
        lock.lock();
        try {
            heartbeatCurrentAccount();
        } finally {
            lock.unlock();
        }
    }

    private void heartbeatCurrentAccount() {
        context.peerDeletionService.retryPending();
        if (context.tracker.token().isBlank() || context.closing) return;
        try {
            context.peerDeletionService.synchronize();
            context.user = context.tracker.call("GET", "/api/auth/me", null);
            if (!"APPROVED".equals(context.user.path("status").asText())) {
                context.connection = "Tài khoản đang chờ duyệt";
                return;
            }
            String id = peerId();
            if (id.isBlank()) {
                connectPeer();
                id = peerId();
            }
            JsonNode result = callHeartbeat(id);
            context.peerStatus = result.path("status").asText();
            context.connection = "Đã kết nối tracker";
            context.catalogSnapshot = context.tracker.call("GET", "/api/files", null);
            if (!"APPROVED".equals(context.peerStatus)) return;
            context.peerDownloadService.dispatchQueued();
            List<String> files;
            List<String> jobs;
            synchronized (context.storage.monitor()) {
                files = new ArrayList<>(context.library.findFileIds());
                jobs = new ArrayList<>(context.jobs.findIds());
            }
            for (String fileId : files) context.peerLibraryService.advertise(fileId);
            for (String jobId : jobs) context.peerDownloadService.report(jobId);
            if (context.peerMalwareService != null) context.peerMalwareService.check();
        } catch (Exception e) {
            context.connection = e.getMessage();
        }
    }

    static void validId(String id) {
        if (id == null || !id.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("File ID không hợp lệ.");
    }

    void ready() {
        if (context.user == null
                || !"APPROVED".equals(context.user.path("status").asText())
                || !"APPROVED".equals(context.peerStatus))
            throw new IllegalArgumentException(
                    "Tài khoản và peer cần được quản trị viên duyệt trước.");
    }
}
