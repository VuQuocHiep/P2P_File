package vn.edu.p2p.client.service;

import vn.edu.p2p.common.protocol.ChunkRequest;
import vn.edu.p2p.client.util.AtomicFiles;
import com.fasterxml.jackson.databind.*;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.p2p.common.model.Manifest;
import vn.edu.p2p.client.model.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.edu.p2p.client.dto.response.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@Service
public class PeerLibraryService {
    private final PeerContext context;

    public PeerLibraryService(PeerContext context) {
        this.context = context;
    }

    void advertise(String fileId) throws Exception {
        Map<String, Object> body;
        synchronized (context.storage.monitor()) {
            Entry entry = context.library.findByFileId(fileId);
            if (entry == null || !entry.sharing) return;
            body =
                    Map.of(
                            "peerId",
                            context.profile.getPeerId(),
                            "manifest",
                            entry.manifest,
                            "chunks",
                            new ArrayList<>(entry.chunks),
                            "scanConsent", entry.scanConsent);
        }
        JsonNode result;
        try {
            result = context.tracker.call("POST", "/api/files", body);
        } catch (vn.edu.p2p.client.exception.TrackerFailure failure) {
            if (failure.status == 410) context.peerDeletionService.apply(List.of(fileId));
            throw failure;
        }
        synchronized (context.storage.monitor()) {
            Entry e = context.library.findByFileId(fileId);
            if (e != null) {
                e.catalogStatus = result.path("status").asText();
                context.storage.saveCheckpoint();
            }
        }
    }

    public UploadResponse upload(MultipartFile upload) throws Exception {
        return upload(upload, false);
    }

    public UploadResponse upload(MultipartFile upload, boolean scanConsent) throws Exception {
        context.peerSessionService.ready();
        if (upload.isEmpty()) throw new IllegalArgumentException("Hãy chọn file có dữ liệu.");
        String name =
                Optional.ofNullable(upload.getOriginalFilename())
                        .orElse("file.bin")
                        .replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        Path temporary = Files.createTempFile(context.storage.root(), "upload-", ".tmp");
        try {
            upload.transferTo(temporary);
            Manifest manifest = Manifest.create(temporary, name);
            synchronized (context.storage.monitor()) {
                if (context.storage.isDeleted(manifest.id()))
                    throw new IllegalArgumentException("File đã được quản trị viên xóa toàn mạng.");
                Entry previous = context.library.findByFileId(manifest.id());
                if (previous == null) {
                    previous = new Entry();
                    previous.manifest = manifest;
                }
                if (context.jobs.findAll().stream()
                        .anyMatch(
                                j ->
                                        j.fileId.equals(manifest.id())
                                                && context.running.contains(j.id)))
                    throw new IllegalArgumentException(
                            "File đang tải. Hãy tạm dừng trước khi thêm file này.");
                AtomicFiles.move(temporary, context.storage.blob(manifest.id()));
                previous.complete = true;
                previous.scanConsent = scanConsent;
                previous.sharing = true;
                previous.chunks.clear();
                for (int i = 0; i < manifest.hashes().size(); i++) previous.chunks.add(i);
                context.library.save(manifest.id(), previous);
                context.events.append("Chia sẻ " + manifest.name());
                context.storage.saveCheckpoint();
            }
            advertise(manifest.id());
            return new UploadResponse("Đã chia sẻ. File mới cần quản trị viên duyệt.", manifest);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public void sharing(String fileId, boolean enabled) throws Exception {
        context.peerSessionService.validId(fileId);
        context.peerSessionService.ready();
        String peerId;
        synchronized (context.storage.monitor()) {
            Entry entry = context.library.findByFileId(fileId);
            if (entry == null) throw new IllegalArgumentException("File không tồn tại.");
            entry.sharing = enabled;
            peerId = context.profile.getPeerId();
            context.storage.saveCheckpoint();
        }
        if (enabled) advertise(fileId);
        else context.tracker.call("DELETE", "/api/files/" + fileId + "/holders/" + peerId, null);
    }

    public void scanConsent(String fileId, boolean enabled) throws Exception {
        context.peerSessionService.validId(fileId);
        context.peerSessionService.ready();
        synchronized (context.storage.monitor()) {
            Entry entry = context.library.findByFileId(fileId);
            if (entry == null) throw new IllegalArgumentException("File không tồn tại.");
            entry.scanConsent = enabled;
            context.events.append((enabled ? "Cho phép" : "Tắt cho phép") + " gửi VirusTotal: " + entry.manifest.name());
            context.storage.saveCheckpoint();
        }
        advertise(fileId);
    }

    byte[] readChunk(ChunkRequest request) throws Exception {
        context.peerSessionService.validId(request.fileId());
        String peerId;
        synchronized (context.storage.monitor()) {
            peerId = context.profile.getPeerId();
        }
        context.tracker.call(
                "POST",
                "/api/tickets/authorize",
                Map.of("ticket", request.ticket(), "peerId", peerId, "fileId", request.fileId()));
        synchronized (context.storage.monitor()) {
            Entry entry = context.library.findByFileId(request.fileId());
            if (entry == null || !entry.sharing || !entry.chunks.contains(request.chunk()))
                throw new IOException("Peer không chia sẻ chunk này.");
            int length = entry.manifest.length(request.chunk());
            byte[] bytes;
            if (entry.complete) {
                bytes = new byte[length];
                try (var file =
                        new RandomAccessFile(
                                context.storage.blob(request.fileId()).toFile(), "r")) {
                    file.seek((long) request.chunk() * entry.manifest.chunkSize());
                    file.readFully(bytes);
                }
            } else
                bytes =
                        Files.readAllBytes(
                                context.storage.chunk(request.fileId(), request.chunk()));
            if (bytes.length != length
                    || !Manifest.hash(bytes).equals(entry.manifest.hashes().get(request.chunk())))
                throw new IOException("Chunk nguồn bị hỏng.");
            return bytes;
        }
    }

    public LocalFile localFile(String id) {
        context.peerSessionService.validId(id);
        synchronized (context.storage.monitor()) {
            Entry entry = context.library.findByFileId(id);
            if (entry == null || !entry.complete)
                throw new IllegalArgumentException("File chưa hoàn tất.");
            return new LocalFile(context.storage.blob(id), entry.manifest.name());
        }
    }
}
