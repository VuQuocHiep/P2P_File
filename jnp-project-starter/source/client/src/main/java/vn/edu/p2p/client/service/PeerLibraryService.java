package vn.edu.p2p.client.service;

import vn.edu.p2p.common.protocol.ChunkRequest;
import vn.edu.p2p.client.repository.PeerStore;
import com.fasterxml.jackson.databind.*;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.p2p.common.model.Manifest;
import vn.edu.p2p.client.model.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

final class PeerLibraryService {
    private final PeerContext context;
    PeerLibraryService(PeerContext context) { this.context = context; }
    void advertise(String fileId) throws Exception {
        Map<String, Object> body;
        synchronized (context.store) {
            Entry entry = context.store.snapshot().library.get(fileId);
            if (entry == null || !entry.sharing) return;
            body = Map.of("peerId", context.store.snapshot().peerId, "manifest", entry.manifest, "chunks", new ArrayList<>(entry.chunks));
        }
        var result = context.tracker.call("POST", "/api/files", body);
        synchronized (context.store) { Entry e = context.store.snapshot().library.get(fileId); if (e != null) { e.catalogStatus = result.path("status").asText(); context.store.save(); } }
    }
    Object upload(MultipartFile upload) throws Exception {
        context.peerSessionService.ready(); if (upload.isEmpty()) throw new IllegalArgumentException("Hãy chọn file có dữ liệu.");
        String name = Optional.ofNullable(upload.getOriginalFilename()).orElse("file.bin").replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        Path temporary = Files.createTempFile(context.store.root(), "upload-", ".tmp");
        try {
            upload.transferTo(temporary); Manifest manifest = Manifest.create(temporary, name);
            synchronized (context.store) {
                Entry previous = context.store.snapshot().library.get(manifest.id());
                if (previous == null) { previous = new Entry(); previous.manifest = manifest; }
                if (context.store.snapshot().jobs.values().stream().anyMatch(j -> j.fileId.equals(manifest.id()) && context.running.contains(j.id)))
                    throw new IllegalArgumentException("File đang tải. Hãy tạm dừng trước khi thêm file này.");
                PeerStore.atomicMove(temporary, context.store.blob(manifest.id())); previous.complete = true; previous.sharing = true;
                previous.chunks.clear(); for (int i = 0; i < manifest.hashes().size(); i++) previous.chunks.add(i);
                context.store.snapshot().library.put(manifest.id(), previous);
                context.store.event("Chia sẻ " + manifest.name()); context.store.save();
            }
            advertise(manifest.id()); return Map.of("message", "Đã chia sẻ. File mới cần quản trị viên duyệt.", "manifest", manifest);
        } finally { Files.deleteIfExists(temporary); }
    }
    void sharing(String fileId, boolean enabled) throws Exception {
        context.peerSessionService.validId(fileId); context.peerSessionService.ready(); String peerId;
        synchronized (context.store) {
            Entry entry = context.store.snapshot().library.get(fileId); if (entry == null) throw new IllegalArgumentException("File không tồn tại.");
            entry.sharing = enabled; peerId = context.store.snapshot().peerId; context.store.save();
        }
        if (enabled) advertise(fileId); else context.tracker.call("DELETE", "/api/files/" + fileId + "/holders/" + peerId, null);
    }
    byte[] readChunk(ChunkRequest request) throws Exception {
        context.peerSessionService.validId(request.fileId());
        String peerId;
        synchronized (context.store) { peerId = context.store.snapshot().peerId; }
        context.tracker.call("POST", "/api/tickets/authorize", Map.of(
                "ticket", request.ticket(), "peerId", peerId, "fileId", request.fileId()));
        synchronized (context.store) {
            Entry entry = context.store.snapshot().library.get(request.fileId());
            if (entry == null || !entry.sharing || !entry.chunks.contains(request.chunk()))
                throw new IOException("Peer không chia sẻ chunk này.");
            int length = entry.manifest.length(request.chunk());
            byte[] bytes;
            if (entry.complete) {
                bytes = new byte[length];
                try (var file = new RandomAccessFile(context.store.blob(request.fileId()).toFile(), "r")) {
                    file.seek((long) request.chunk() * entry.manifest.chunkSize());
                    file.readFully(bytes);
                }
            } else bytes = Files.readAllBytes(context.store.chunk(request.fileId(), request.chunk()));
            if (bytes.length != length || !Manifest.hash(bytes).equals(entry.manifest.hashes().get(request.chunk())))
                throw new IOException("Chunk nguồn bị hỏng.");
            return bytes;
        }
    }
    LocalFile localFile(String id) {
        context.peerSessionService.validId(id); synchronized (context.store) {
            Entry entry = context.store.snapshot().library.get(id);
            if (entry == null || !entry.complete) throw new IllegalArgumentException("File chưa hoàn tất.");
            return new LocalFile(context.store.blob(id), entry.manifest.name());
        }
    }
}
