package vn.edu.p2p.server.service;

import vn.edu.p2p.server.repository.TrackerState;
import vn.edu.p2p.server.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.edu.p2p.server.model.*;
import java.util.*;

@Service
public class TrackerService {
    private final TrackerContext context;
    private final TrackerAccess access;
    private final TrackerAuthService trackerAuthService;
    private final TrackerPeerService trackerPeerService;
    private final TrackerFileService trackerFileService;
    private final TrackerTicketService trackerTicketService;
    private final TrackerTransferService trackerTransferService;
    private final TrackerAdminService trackerAdminService;
    public TrackerService(TrackerState store, @Value("${peermesh.admin.username}") String admin,
                          @Value("${peermesh.admin.password}") String password) {
        context = new TrackerContext(store);
        access = new TrackerAccess(context);
        trackerAuthService = new TrackerAuthService(context, access);
        trackerPeerService = new TrackerPeerService(context, access);
        trackerFileService = new TrackerFileService(context, access);
        trackerTicketService = new TrackerTicketService(context, access);
        trackerTransferService = new TrackerTransferService(context, access);
        trackerAdminService = new TrackerAdminService(context, access);
        if (context.state.accounts.values().stream().noneMatch(a -> "ADMIN".equals(a.role))) {
            access.checkName(admin);
            access.require(password.length() >= 12 && password.length() <= 72,
                    "Lần chạy đầu cần ADMIN_PASSWORD dài 12–72 ký tự.");
            context.state.accounts.put(admin, new Account(admin, context.passwords.encode(password), "ADMIN", "APPROVED"));
            store.log(admin, "BOOTSTRAP_ADMIN", admin); store.save();
        }
    }
    public synchronized Map<String, Object> register(Login input) { return trackerAuthService.register(input); }
    public synchronized Map<String, Object> login(Login input) { return trackerAuthService.login(input); }
    public synchronized void logout(String token) { trackerAuthService.logout(token); }
    public synchronized Map<String, Object> me(String token) { return trackerAuthService.me(token); }
    public synchronized Map<String, Object> registerPeer(String token, RegisterPeer input) throws Exception { return trackerPeerService.registerPeer(token, input); }
    public synchronized Map<String, Object> heartbeat(String token, Heartbeat input) { return trackerPeerService.heartbeat(token, input); }
    public synchronized Map<String, Object> advertise(String token, Advertisement input) { return trackerFileService.advertise(token, input); }
    public synchronized void unshare(String token, String peerId, String fileId) { trackerFileService.unshare(token, peerId, fileId); }
    public synchronized List<Map<String, Object>> files(String token, String query) { return trackerFileService.files(token, query); }
    public synchronized List<Map<String, Object>> sources(String token, String fileId) { return trackerFileService.sources(token, fileId); }
    public synchronized Map<String, Object> ticket(String token, TicketRequest input) { return trackerTicketService.ticket(token, input); }
    public synchronized Map<String, Object> authorize(String token, CheckTicket input) { return trackerTicketService.authorize(token, input); }
    public synchronized void report(String token, Progress input) { trackerTransferService.report(token, input); }
    public synchronized Map<String, Object> dashboard(String token) { return trackerAdminService.dashboard(token); }
    public synchronized void moderate(String token, String kind, String id, String status) { trackerAdminService.moderate(token, kind, id, status); }
}
