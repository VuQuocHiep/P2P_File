package vn.edu.p2p.server.service;

import vn.edu.p2p.server.dto.request.*;
import vn.edu.p2p.common.model.Manifest;
import vn.edu.p2p.server.model.*;
import vn.edu.p2p.server.entity.*;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.edu.p2p.server.dto.response.*;
import vn.edu.p2p.server.mapper.FileMapper;
import vn.edu.p2p.server.mapper.PeerMapper;

/** Registry operations serialized on the shared context monitor. */
@Service
public class TrackerFileService {
    private final TrackerContext context;
    private final TrackerAccess access;
    private final FileMapper fileMapper;
    private final PeerMapper peerMapper;

    public TrackerFileService(
            TrackerContext context,
            TrackerAccess access,
            FileMapper fileMapper,
            PeerMapper peerMapper) {
        this.context = context;
        this.access = access;
        this.fileMapper = fileMapper;
        this.peerMapper = peerMapper;
    }

    public FileResponse advertise(String token, Advertisement input) {
        synchronized (context) {
            var transaction = context.beginTransaction();
            try {
                AccountEntity a = access.account(token, true);
                PeerEntity p = access.ownPeer(a, input.peerId(), true);
                access.require(input.manifest() != null, "Thiếu manifest.");
                input.manifest().validate();
                if (context.deletions.existsById(input.manifest().id()))
                    throw new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.GONE,
                            "File đã được quản trị viên xóa toàn mạng.");
                access.require(
                        input.chunks() != null
                                && input.chunks().size() <= input.manifest().hashes().size()
                                && new HashSet<>(input.chunks()).size() == input.chunks().size()
                                && input.chunks().stream()
                                        .allMatch(
                                                i ->
                                                        i != null
                                                                && i >= 0
                                                                && i
                                                                        < input.manifest()
                                                                                .hashes()
                                                                                .size()),
                        "Danh sách chunk không hợp lệ.");
                FileEntity file = context.files.findFileById(input.manifest().id()).orElse(null);
                if (file == null) {
                    file = new FileEntity();
                    file.manifest = input.manifest();
                    file.status = "PENDING";
                    file.submittedBy = a.username;
                    file.id = file.manifest.id();
                    file.position = context.nextFilePosition++;
                    context.log(a.username, "SUBMIT_FILE", file.manifest.name());
                } else
                    access.require(
                            file.manifest.sameContent(input.manifest()),
                            "Manifest xung đột với file đã đăng ký.");
                file.holders.put(p.id, List.copyOf(input.chunks()));
                if (input.scanConsent()) file.scanConsentPeers.add(p.id);
                else file.scanConsentPeers.remove(p.id);
                p.lastSeen = System.currentTimeMillis();
                file = context.files.save(file);
                return fileMapper.toResponse(file, access.onlineSources(file));
            } catch (Exception | Error failure) {
                context.rollback(transaction);
                throw failure;
            } finally {
                context.commit(transaction);
            }
        }
    }

    public void unshare(String token, String peerId, String fileId) {
        synchronized (context) {
            var transaction = context.beginTransaction();
            try {
                access.ownPeer(access.account(token, true), peerId, false);
                FileEntity f = context.files.findFileById(fileId).orElse(null);
                if (f != null) {
                    f.holders.remove(peerId);
                    f.scanConsentPeers.remove(peerId);
                }
            } catch (Exception | Error failure) {
                context.rollback(transaction);
                throw failure;
            } finally {
                context.commit(transaction);
            }
        }
    }

    public List<FileResponse> files(String token, String query) {
        synchronized (context) {
            var transaction = context.beginTransaction();
            try {
                access.account(token, true);
                String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
                return context.files.findAllByOrderByPositionAsc().stream()
                        .filter(
                                f ->
                                        "APPROVED".equals(f.status)
                                                && f.manifest
                                                        .name()
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains(q))
                        .map(f -> fileMapper.toResponse(f, access.onlineSources(f)))
                        .toList();
            } catch (Exception | Error failure) {
                context.rollback(transaction);
                throw failure;
            } finally {
                context.commit(transaction);
            }
        }
    }

    public List<SourceResponse> sources(String token, String fileId) {
        synchronized (context) {
            var transaction = context.beginTransaction();
            try {
                access.account(token, true);
                FileEntity f = context.files.findFileById(fileId).orElse(null);
                access.require(
                        f != null && "APPROVED".equals(f.status),
                        "File chưa được duyệt hoặc đã bị ẩn.");
                var result = new ArrayList<SourceResponse>();
                f.holders.forEach(
                        (id, chunks) -> {
                            PeerEntity p = context.peers.findPeerById(id).orElse(null);
                            if (p != null && access.online(p)) {
                                result.add(
                                        peerMapper.toSourceResponse(p, access.online(p), chunks));
                            }
                        });
                return result;
            } catch (Exception | Error failure) {
                context.rollback(transaction);
                throw failure;
            } finally {
                context.commit(transaction);
            }
        }
    }

    public List<String> deletedFileIds(String token) {
        synchronized (context) {
            var transaction = context.beginTransaction();
            try {
                var session = context.sessions.get(token);
                if (session == null || session.expires() < System.currentTimeMillis())
                    throw new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.UNAUTHORIZED, "Cần đăng nhập lại.");
                return context.deletions.findAllByOrderByDeletedAtAsc().stream()
                        .map(d -> d.fileId)
                        .toList();
            } catch (Exception | Error failure) {
                context.rollback(transaction);
                throw failure;
            } finally {
                context.commit(transaction);
            }
        }
    }
}
