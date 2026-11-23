package vn.edu.p2p.client.storage;

import vn.edu.p2p.client.util.AtomicFiles;

import vn.edu.p2p.client.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.edu.p2p.common.model.Manifest;
import java.io.*;
import java.nio.file.*;
import java.nio.channels.*;
import jakarta.annotation.PreDestroy;
import java.util.*;

/** Profile state and verified chunk checkpoints survive application restarts. */
@Component
public class PeerStorage implements AutoCloseable {
    private final Path baseRoot;
    private Path root;
    private State state;
    private String originalOwner;
    private final ObjectMapper json;
    private final FileChannel lockChannel;
    private final FileLock profileLock;

    public PeerStorage(@Value("${peer.data}") String path, ObjectMapper json) throws IOException {
        this.json = json;
        baseRoot = Path.of(path).toAbsolutePath().normalize();
        root = baseRoot;
        Files.createDirectories(root.resolve("blobs"));
        Files.createDirectories(root.resolve("chunks"));
        lockChannel =
                FileChannel.open(
                        root.resolve("profile.lock"),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE);
        try {
            profileLock = lockChannel.tryLock();
        } catch (OverlappingFileLockException e) {
            lockChannel.close();
            throw new IOException("PEER_DATA đang được một peer khác sử dụng.", e);
        }
        if (profileLock == null) {
            lockChannel.close();
            throw new IOException("PEER_DATA đang được một peer khác sử dụng.");
        }
        state = loadCheckpoint();
        restoreCheckpoint();
        saveCheckpoint();
    }

    private State loadCheckpoint() throws IOException {
        Path stateFile = root.resolve("profile.json");
        return Files.exists(stateFile)
                        ? json.readValue(stateFile.toFile(), State.class)
                        : new State();
    }

    /** Keep the original profile at its existing path and isolate additional accounts. */
    public synchronized void selectAccount(String username) throws IOException {
        if (username.equals(state.owner)) return;
        Path target = originalOwner.isBlank() || username.equals(originalOwner)
                ? baseRoot
                : baseRoot.resolve("accounts").resolve(
                        Manifest.hash(username.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        if (target.equals(root)) return;
        Path previousRoot = root;
        State previousState = state;
        try {
            root = target;
            Files.createDirectories(root.resolve("blobs"));
            Files.createDirectories(root.resolve("chunks"));
            state = loadCheckpoint();
            if (!state.owner.isBlank() && !username.equals(state.owner))
                throw new IOException("Hồ sơ dữ liệu thuộc tài khoản khác.");
            restoreCheckpoint();
        } catch (IOException | RuntimeException failure) {
            root = previousRoot;
            state = previousState;
            throw failure;
        }
    }

    private void restoreCheckpoint() throws IOException {
        if (state.deletedFiles == null) state.deletedFiles = new TreeSet<>();
        for (String id : state.deletedFiles) {
            validateFileId(id);
            state.library.remove(id);
            state.jobs.values().removeIf(job -> id.equals(job.fileId));
            try {
                deleteManagedFile(id);
            } catch (IOException ignored) {
                /* Retry after startup. */
            }
        }
        // Never trust a checkpoint bit alone: verify all stored chunks and completed files on
        // startup.
        for (Entry e : state.library.values()) {
            e.manifest.validate();
            if (e.complete
                    && (!Files.isRegularFile(blob(e.manifest.id()))
                            || !Manifest.hash(blob(e.manifest.id())).equals(e.manifest.id())))
                e.complete = false;
            if (!e.complete) {
                var verified = new TreeSet<Integer>();
                for (int i = 0; i < e.manifest.hashes().size(); i++) {
                    Path part = chunk(e.manifest.id(), i);
                    if (Files.isRegularFile(part)
                            && Files.size(part) == e.manifest.length(i)
                            && Manifest.hash(part).equals(e.manifest.hashes().get(i)))
                        verified.add(i);
                }
                e.chunks = verified;
            } else {
                e.chunks.clear();
                for (int i = 0; i < e.manifest.hashes().size(); i++) e.chunks.add(i);
            }
        }
        for (Job j : state.jobs.values()) {
            Entry e = state.library.get(j.fileId);
            if (j.priority == null) j.priority = DownloadPriority.NORMAL;
            if (j.chunkSources == null) j.chunkSources = new TreeMap<>();
            j.chunkSources.keySet().removeIf(index -> e == null || !e.chunks.contains(index));
            if (e != null && e.complete) j.status = "COMPLETED";
            else if (!"QUEUED".equals(j.status)) {
                j.status = "PAUSED";
                j.error = "Đã khôi phục tiến độ. Đăng nhập và bấm tiếp tục.";
            }
        }
    }

    /** Shared persistence state, accessed only by repositories under monitor(). */
    public State state() {
        return state;
    }

    public Object monitor() {
        return this;
    }

    public Path root() {
        return root;
    }

    public Path blob(String id) {
        return root.resolve("blobs").resolve(id);
    }

    public Path chunk(String id, int index) {
        return root.resolve("chunks").resolve(id).resolve(index + ".part");
    }

    public synchronized void saveCheckpoint() {
        try {
            Path temporary = root.resolve("profile.json.tmp");
            json.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), state);
            AtomicFiles.move(temporary, root.resolve("profile.json"));
            if (root.equals(baseRoot)) originalOwner = state.owner;
        } catch (IOException e) {
            throw new IllegalStateException("Không lưu được tiến độ tải.", e);
        }
    }

    public synchronized boolean markDeleted(String fileId) {
        validateFileId(fileId);
        return state.deletedFiles.add(fileId);
    }

    public synchronized boolean isDeleted(String fileId) {
        return state.deletedFiles.contains(fileId);
    }

    public synchronized List<String> pendingDeletions() {
        return List.copyOf(state.deletedFiles);
    }

    private static void validateFileId(String fileId) {
        if (fileId == null || !fileId.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("File ID không hợp lệ.");
    }

    /** Deletes only hash-addressed application copies, never the original uploaded path. */
    public synchronized void deleteManagedFile(String fileId) throws IOException {
        validateFileId(fileId);
        Path actualRoot = root.toRealPath();
        for (Path parent : List.of(root.resolve("blobs"), root.resolve("chunks"))) {
            if (!parent.toRealPath().startsWith(actualRoot))
                throw new IOException("Thư mục dữ liệu nằm ngoài profile.");
        }
        Files.deleteIfExists(blob(fileId));
        Path chunks = root.resolve("chunks").resolve(fileId);
        if (Files.exists(chunks, LinkOption.NOFOLLOW_LINKS)) {
            Files.walkFileTree(
                    chunks,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(
                                Path file, java.nio.file.attribute.BasicFileAttributes attrs)
                                throws IOException {
                            Files.delete(file);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path directory, IOException error)
                                throws IOException {
                            if (error != null) throw error;
                            Files.delete(directory);
                            return FileVisitResult.CONTINUE;
                        }
                    });
        }
        Files.deleteIfExists(root.resolve(fileId + ".assembling"));
    }

    @Override
    @PreDestroy
    public void close() throws IOException {
        profileLock.release();
        lockChannel.close();
    }
}
