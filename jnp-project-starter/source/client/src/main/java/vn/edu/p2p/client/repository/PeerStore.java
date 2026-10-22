package vn.edu.p2p.client.repository;

import vn.edu.p2p.client.model.PeerModels.*;
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
public class PeerStore implements AutoCloseable {
    final Path root;
    final State state;
    public State snapshot() { return state; }
    public Path root() { return root; }
    private final ObjectMapper json;
    private final Path stateFile;
    private final FileChannel lockChannel;
    private final FileLock profileLock;
    public PeerStore(@Value("${peer.data}") String path, ObjectMapper json) throws IOException {
        this.json = json; root = Path.of(path).toAbsolutePath().normalize();
        Files.createDirectories(root.resolve("blobs")); Files.createDirectories(root.resolve("chunks"));
        lockChannel = FileChannel.open(root.resolve("profile.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try { profileLock = lockChannel.tryLock(); }
        catch (OverlappingFileLockException e) { lockChannel.close(); throw new IOException("PEER_DATA đang được một peer khác sử dụng.", e); }
        if (profileLock == null) { lockChannel.close(); throw new IOException("PEER_DATA đang được một peer khác sử dụng."); }
        stateFile = root.resolve("profile.json");
        state = Files.exists(stateFile) ? json.readValue(stateFile.toFile(), State.class) : new State();
        // Never trust a checkpoint bit alone: verify all stored chunks and completed files on startup.
        for (Entry e : state.library.values()) {
            e.manifest.validate();
            if (e.complete && (!Files.isRegularFile(blob(e.manifest.id()))
                    || !Manifest.hash(blob(e.manifest.id())).equals(e.manifest.id()))) e.complete = false;
            if (!e.complete) {
                var verified = new TreeSet<Integer>();
                for (int i = 0; i < e.manifest.hashes().size(); i++) {
                    Path part = chunk(e.manifest.id(), i);
                    if (Files.isRegularFile(part) && Files.size(part) == e.manifest.length(i)
                            && Manifest.hash(part).equals(e.manifest.hashes().get(i))) verified.add(i);
                }
                e.chunks = verified;
            } else {
                e.chunks.clear(); for (int i = 0; i < e.manifest.hashes().size(); i++) e.chunks.add(i);
            }
        }
        for (Job j : state.jobs.values()) {
            Entry e = state.library.get(j.fileId);
            if (j.chunkSources == null) j.chunkSources = new TreeMap<>();
            j.chunkSources.keySet().removeIf(index -> e == null || !e.chunks.contains(index));
            if (e != null && e.complete) j.status = "COMPLETED";
            else { j.status = "PAUSED"; j.error = "Đã khôi phục tiến độ. Đăng nhập và bấm tiếp tục."; }
        }
        save();
    }
    public Path blob(String id) { return root.resolve("blobs").resolve(id); }
    public Path chunk(String id, int index) { return root.resolve("chunks").resolve(id).resolve(index + ".part"); }
    public synchronized void event(String text) {
        state.events.add(java.time.Instant.now() + " · " + text);
        if (state.events.size() > 100) state.events.remove(0);
    }
    public synchronized void save() {
        try {
            Path temporary = root.resolve("profile.json.tmp");
            json.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), state);
            atomicMove(temporary, stateFile);
        } catch (IOException e) { throw new IllegalStateException("Không lưu được tiến độ tải.", e); }
    }
    public static void atomicMove(Path from, Path to) throws IOException {
        try { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING); }
    }
    @Override @PreDestroy public void close() throws IOException { profileLock.release(); lockChannel.close(); }
}
