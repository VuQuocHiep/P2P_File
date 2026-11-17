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
import vn.edu.p2p.client.mapper.TrackerResponseMapper;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@Service
public class PeerSessionService {
    private final PeerContext context;
    private final TrackerResponseMapper trackerResponseMapper;

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
        synchronized (context.storage.monitor()) {
            if (!context.profile.getOwner().isBlank()
                    && !context.profile.getOwner().equals(username))
                throw new IllegalArgumentException(
                        "Profile này thuộc "
                                + context.profile.getOwner()
                                + ". Dùng PEER_DATA khác cho tài khoản khác.");
        }
        JsonNode result =
                context.tracker.call(
                        "POST",
                        "/api/auth/login",
                        Map.of("username", username, "password", password));
        context.tracker.token(result.path("token").asText());
        context.user = result.path("user");
        context.peerDeletionService.synchronize();
        synchronized (context.storage.monitor()) {
            context.profile.update(username, name, host);
            context.storage.saveCheckpoint();
        }
        if ("APPROVED".equals(context.user.path("status").asText())) connectPeer();
        else context.connection = "Tài khoản đang chờ duyệt";
        context.peerDownloadService.dispatchQueued();
        return context.peerStatusService.status();
    }

    void connectPeer() throws Exception {
        Map<String, Object> body;
        synchronized (context.storage.monitor()) {
            body =
                    Map.of(
                            "id",
                            context.profile.getPeerId(),
                            "name",
                            context.profile.getName(),
                            "host",
                            context.profile.getHost(),
                            "port",
                            context.tcpPort);
        }
        JsonNode result = context.tracker.call("POST", "/api/peers", body);
        synchronized (context.storage.monitor()) {
            context.profile.updatePeerId(result.path("id").asText());
            context.events.append("Đăng ký peer " + context.profile.getName());
            context.storage.saveCheckpoint();
        }
        context.peerStatus = result.path("status").asText();
        context.connection = "Đã kết nối tracker";
    }

    public void logout() {
        context.tcpServer.fault("NORMAL", 0, 1);
        synchronized (context.storage.monitor()) {
            context.jobs.findAll().stream()
                    .filter(j -> !"COMPLETED".equals(j.status))
                    .forEach(j -> j.status = "PAUSED");
            context.storage.saveCheckpoint();
        }
        try {
            context.tracker.call("POST", "/api/auth/logout", Map.of());
        } catch (Exception ignored) {
        }
        context.tracker.token("");
        context.user = null;
        context.catalogSnapshot = context.json.createArrayNode();
        context.connection = "Đã đăng xuất";
    }

    void heartbeat() {
        context.peerDeletionService.retryPending();
        if (context.tracker.token().isBlank() || context.closing) return;
        try {
            context.peerDeletionService.synchronize();
            context.user = context.tracker.call("GET", "/api/auth/me", null);
            if (!"APPROVED".equals(context.user.path("status").asText())) {
                context.connection = "Tài khoản đang chờ duyệt";
                return;
            }
            String id;
            synchronized (context.storage.monitor()) {
                id = context.profile.getPeerId();
            }
            if (id.isBlank()) {
                connectPeer();
                synchronized (context.storage.monitor()) {
                    id = context.profile.getPeerId();
                }
            }
            JsonNode result =
                    context.tracker.call("POST", "/api/peers/heartbeat", Map.of("peerId", id));
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
