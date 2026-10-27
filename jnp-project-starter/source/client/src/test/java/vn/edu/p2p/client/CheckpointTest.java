package vn.edu.p2p.client;

import vn.edu.p2p.client.repository.PeerStore;
import vn.edu.p2p.client.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.p2p.common.model.Manifest;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class CheckpointTest {
    @TempDir Path temp;
    @Test void completedHistorySurvivesRestartAndLegacyProfilesRemainReadable() throws Exception {
        var mapper = new ObjectMapper();
        Path source = temp.resolve("original"); Files.write(source, new byte[]{1,2,3});
        var manifest = Manifest.create(source, "small.bin");
        var origin = new vn.edu.p2p.client.model.ChunkSource("peer-b", "Máy B", "bob", "127.0.0.1", 9002, 123L);
        try (var store = new PeerStore(temp.toString(), mapper)) {
            var entry = new vn.edu.p2p.client.model.Entry();
            entry.manifest = manifest; entry.complete = true; entry.chunks.add(0);
            Files.copy(source, store.blob(manifest.id()));
            store.snapshot().library.put(manifest.id(), entry);
            var job = new vn.edu.p2p.client.model.Job();
            job.id = "completed"; job.fileId = manifest.id(); job.status = "COMPLETED";
            job.chunkSources.put(0, origin); store.snapshot().jobs.put(job.id, job); store.save();
        }
        try (var store = new PeerStore(temp.toString(), mapper)) {
            assertEquals("COMPLETED", store.snapshot().jobs.get("completed").status);
            assertEquals(java.util.Map.of(0, origin), store.snapshot().jobs.get("completed").chunkSources);
        }
        Path profile = temp.resolve("profile.json");
        var legacy = mapper.readTree(profile.toFile());
        ((com.fasterxml.jackson.databind.node.ObjectNode) legacy.path("jobs").path("completed")).remove("chunkSources");
        mapper.writeValue(profile.toFile(), legacy);
        try (var store = new PeerStore(temp.toString(), mapper)) {
            assertTrue(store.snapshot().jobs.get("completed").chunkSources.isEmpty());
            assertTrue(store.snapshot().library.get(manifest.id()).complete);
        }
    }
    @Test void restartRehashesChunksAndRejectsCorruptCheckpoint() throws Exception {
        var mapper = new ObjectMapper(); var store = new PeerStore(temp.toString(), mapper);
        byte[] bytes = new byte[Manifest.CHUNK_SIZE + 20]; new java.util.Random(7).nextBytes(bytes);
        Path source = temp.resolve("original"); Files.write(source,bytes); var manifest = Manifest.create(source,"sample.bin");
        var entry = new vn.edu.p2p.client.model.Entry(); entry.manifest=manifest; entry.chunks.add(0); entry.chunks.add(1);
        Files.createDirectories(store.chunk(manifest.id(),0).getParent());
        Files.write(store.chunk(manifest.id(),0),java.util.Arrays.copyOf(bytes,Manifest.CHUNK_SIZE));
        Files.write(store.chunk(manifest.id(),1),new byte[20]); // Deliberately corrupt second chunk.
        store.snapshot().library.put(manifest.id(),entry);
        var job=new vn.edu.p2p.client.model.Job();job.id="resume-test";job.fileId=manifest.id();job.status="DOWNLOADING";
        var origin = new vn.edu.p2p.client.model.ChunkSource("peer-a", "Máy A", "alice", "127.0.0.1", 9001, 123456L);
        job.chunkSources.put(0, origin); job.chunkSources.put(1, origin);
        store.snapshot().jobs.put(job.id,job);store.save();store.close();
        var restored=new PeerStore(temp.toString(),mapper);
        assertEquals(java.util.Set.of(0),restored.snapshot().library.get(manifest.id()).chunks);
        assertEquals("PAUSED",restored.snapshot().jobs.get(job.id).status);
        assertEquals(java.util.Map.of(0, origin), restored.snapshot().jobs.get(job.id).chunkSources);
        // Completed marker alone must never make a missing blob downloadable.
        restored.snapshot().library.get(manifest.id()).complete=true;restored.save();restored.close();
        try (var checked = new PeerStore(temp.toString(),mapper)) {
            assertFalse(checked.snapshot().library.get(manifest.id()).complete);
        }
    }
}
