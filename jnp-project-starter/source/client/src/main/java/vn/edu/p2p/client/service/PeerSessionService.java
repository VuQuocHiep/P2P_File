package vn.edu.p2p.client.service;

import com.fasterxml.jackson.databind.*;
import vn.edu.p2p.client.model.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

final class PeerSessionService {
    private final PeerContext context;
    PeerSessionService(PeerContext context) { this.context = context; }
    Object register(Object input) throws Exception { return context.tracker.call("POST", "/api/auth/register", input); }
    boolean loggedIn() { return context.user != null && !context.tracker.token().isBlank(); }
    Object login(String username, String password, String name, String host) throws Exception {
        synchronized (context.store) {
            if (!context.store.snapshot().owner.isBlank() && !context.store.snapshot().owner.equals(username))
                throw new IllegalArgumentException("Profile này thuộc " + context.store.snapshot().owner + ". Dùng PEER_DATA khác cho tài khoản khác.");
        }
        JsonNode result = context.tracker.call("POST", "/api/auth/login", Map.of("username", username, "password", password));
        context.tracker.token(result.path("token").asText()); context.user = result.path("user");
        synchronized (context.store) {
            context.store.snapshot().owner = username; context.store.snapshot().name = name; context.store.snapshot().host = host; context.store.save();
        }
        if ("APPROVED".equals(context.user.path("status").asText())) connectPeer();
        else context.connection = "Tài khoản đang chờ duyệt";
        return context.peerStatusService.status();
    }
    void connectPeer() throws Exception {
        Map<String, Object> body;
        synchronized (context.store) {
            body = Map.of("id", context.store.snapshot().peerId, "name", context.store.snapshot().name, "host", context.store.snapshot().host, "port", context.tcpPort);
        }
        JsonNode result = context.tracker.call("POST", "/api/peers", body);
        synchronized (context.store) { context.store.snapshot().peerId = result.path("id").asText(); context.store.event("Đăng ký peer " + context.store.snapshot().name); context.store.save(); }
        context.peerStatus = result.path("status").asText(); context.connection = "Đã kết nối tracker";
    }
    void logout() {
        context.tcpServer.fault("NORMAL", 0, 1);
        synchronized (context.store) {
            context.store.snapshot().jobs.values().stream().filter(j -> !"COMPLETED".equals(j.status)).forEach(j -> j.status = "PAUSED"); context.store.save();
        }
        try { context.tracker.call("POST", "/api/auth/logout", Map.of()); } catch (Exception ignored) {}
        context.tracker.token(""); context.user = null; context.catalogSnapshot = context.json.createArrayNode(); context.connection = "Đã đăng xuất";
    }
    void heartbeat() {
        if (context.tracker.token().isBlank() || context.closing) return;
        try {
            context.user = context.tracker.call("GET", "/api/auth/me", null);
            if (!"APPROVED".equals(context.user.path("status").asText())) { context.connection = "Tài khoản đang chờ duyệt"; return; }
            String id;
            synchronized (context.store) { id = context.store.snapshot().peerId; }
            if (id.isBlank()) { connectPeer(); synchronized (context.store) { id = context.store.snapshot().peerId; } }
            JsonNode result = context.tracker.call("POST", "/api/peers/heartbeat", Map.of("peerId", id));
            context.peerStatus = result.path("status").asText(); context.connection = "Đã kết nối tracker";
            context.catalogSnapshot = context.tracker.call("GET", "/api/files", null);
            if (!"APPROVED".equals(context.peerStatus)) return;
            List<String> files; List<String> jobs;
            synchronized (context.store) { files = new ArrayList<>(context.store.snapshot().library.keySet()); jobs = new ArrayList<>(context.store.snapshot().jobs.keySet()); }
            for (String fileId : files) context.peerLibraryService.advertise(fileId);
            for (String jobId : jobs) context.peerDownloadService.report(jobId);
        } catch (Exception e) { context.connection = e.getMessage(); }
    }
    Object catalog(String query) throws Exception {
        return context.tracker.call("GET", "/api/files?q=" + URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8), null);
    }
    Object sources(String id) throws Exception { validId(id); return context.tracker.call("GET", "/api/files/" + id + "/sources", null); }
    static void validId(String id) {
        if (id == null || !id.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("File ID không hợp lệ.");
    }
    void ready() {
        if (context.user == null || !"APPROVED".equals(context.user.path("status").asText()) || !"APPROVED".equals(context.peerStatus))
            throw new IllegalArgumentException("Tài khoản và peer cần được quản trị viên duyệt trước.");
    }
}
