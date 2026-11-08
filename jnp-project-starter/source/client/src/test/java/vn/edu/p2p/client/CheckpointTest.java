package vn.edu.p2p.client;

import vn.edu.p2p.client.repository.*;
import vn.edu.p2p.client.storage.PeerStorage;
import vn.edu.p2p.client.dto.request.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.p2p.common.model.Manifest;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class CheckpointTest {
    private static final class Repositories implements AutoCloseable {
        final PeerStorage storage;
        final PeerProfileRepository profile;
        final LibraryRepository library;
        final DownloadJobRepository jobs;
        final PeerEventRepository events;

        Repositories(String path, ObjectMapper json) throws java.io.IOException {
            storage = new PeerStorage(path, json);
            profile = new PeerProfileRepository(storage);
            library = new LibraryRepository(storage);
            jobs = new DownloadJobRepository(storage);
            events = new PeerEventRepository(storage);
        }

        @Override
        public void close() throws java.io.IOException {
            storage.close();
        }
    }

    @TempDir Path temp;

    @Test
    void pendingNetworkDeletionSurvivesRestartAndOnlyDeletesManagedCopies() throws Exception {
        var mapper = new ObjectMapper();
        Path original = temp.resolve("original.bin");
        Files.write(original, new byte[] {1, 2, 3});
        var manifest = Manifest.create(original, "original.bin");
        String id = manifest.id();
        Path profile = temp.resolve("peer");
        try (var data = new Repositories(profile.toString(), mapper)) {
            var entry = new vn.edu.p2p.client.model.Entry();
            entry.manifest = manifest;
            entry.complete = true;
            entry.chunks.add(0);
            data.library.save(id, entry);
            var job = new vn.edu.p2p.client.model.Job();
            job.id = "deleted-job";
            job.fileId = id;
            job.status = "COMPLETED";
            data.jobs.save(job);
            Files.copy(original, data.storage.blob(id));
            Files.createDirectories(data.storage.chunk(id, 0).getParent());
            Files.copy(original, data.storage.chunk(id, 0));
            Files.copy(original, data.storage.root().resolve(id + ".assembling"));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> data.storage.markDeleted("../../original.bin"));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> data.storage.deleteManagedFile("../../original.bin"));
            data.storage.markDeleted(id);
            data.storage.saveCheckpoint();
        }
        try (var restored = new Repositories(profile.toString(), mapper)) {
            assertNull(restored.library.findByFileId(id));
            assertNull(restored.jobs.findById("deleted-job"));
            assertFalse(Files.exists(restored.storage.blob(id)));
            assertFalse(Files.exists(restored.storage.chunk(id, 0).getParent()));
            assertFalse(Files.exists(restored.storage.root().resolve(id + ".assembling")));
            assertTrue(restored.storage.isDeleted(id));
            restored.storage.deleteManagedFile(id);
        }
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(original));
    }

    @Test
    void separateRepositoriesPersistOneProfileAndKeepItsFileLock() throws Exception {
        var mapper = new ObjectMapper();
        Path source = temp.resolve("original");
        Files.write(source, new byte[] {1, 2, 3});
        var manifest = Manifest.create(source, "sample.bin");
        try (var data = new Repositories(temp.toString(), mapper)) {
            assertThrows(java.io.IOException.class, () -> new PeerStorage(temp.toString(), mapper));
            data.profile.update("alice", "Laptop", "127.0.0.1");
            data.profile.updatePeerId("peer-a");
            var entry = new vn.edu.p2p.client.model.Entry();
            entry.manifest = manifest;
            data.library.save(manifest.id(), entry);
            var job = new vn.edu.p2p.client.model.Job();
            job.id = "job-a";
            job.fileId = manifest.id();
            job.status = "PAUSED";
            data.jobs.save(job);
            for (int i = 0; i <= 100; i++) data.events.append("event-" + i);
            data.storage.saveCheckpoint();
        }
        try (var restored = new Repositories(temp.toString(), mapper)) {
            assertEquals("alice", restored.profile.getOwner());
            assertEquals("Laptop", restored.profile.getName());
            assertEquals("peer-a", restored.profile.getPeerId());
            assertEquals(manifest, restored.library.findByFileId(manifest.id()).manifest);
            assertEquals("job-a", restored.jobs.findByFileId(manifest.id()).id);
            assertEquals(100, restored.events.findAll().size());
            assertTrue(restored.events.findAll().iterator().next().endsWith("event-1"));
        }
        var keys = new java.util.HashSet<String>();
        mapper.readTree(temp.resolve("profile.json").toFile())
                .fieldNames()
                .forEachRemaining(keys::add);
        assertEquals(
                java.util.Set.of(
                        "peerId",
                        "owner",
                        "name",
                        "host",
                        "uploadLimitKiB",
                        "downloadLimitKiB",
                        "library",
                        "jobs",
                        "events",
                        "deletedFiles"),
                keys);
    }

    @Test
    void completedHistorySurvivesRestartAndLegacyProfilesRemainReadable() throws Exception {
        var mapper = new ObjectMapper();
        Path source = temp.resolve("original");
        Files.write(source, new byte[] {1, 2, 3});
        var manifest = Manifest.create(source, "small.bin");
        var origin =
                new vn.edu.p2p.client.model.ChunkSource(
                        "peer-b", "Máy B", "bob", "127.0.0.1", 9002, 123L);
        try (var store = new Repositories(temp.toString(), mapper)) {
            var entry = new vn.edu.p2p.client.model.Entry();
            entry.manifest = manifest;
            entry.complete = true;
            entry.chunks.add(0);
            Files.copy(source, store.storage.blob(manifest.id()));
            store.library.save(manifest.id(), entry);
            var job = new vn.edu.p2p.client.model.Job();
            job.id = "completed";
            job.fileId = manifest.id();
            job.status = "COMPLETED";
            job.chunkSources.put(0, origin);
            store.jobs.save(job);
            store.storage.saveCheckpoint();
        }
        try (var store = new Repositories(temp.toString(), mapper)) {
            assertEquals("COMPLETED", store.jobs.findById("completed").status);
            assertEquals(
                    java.util.Map.of(0, origin), store.jobs.findById("completed").chunkSources);
        }
        Path profile = temp.resolve("profile.json");
        var legacy = mapper.readTree(profile.toFile());
        ((com.fasterxml.jackson.databind.node.ObjectNode) legacy.path("jobs").path("completed"))
                .remove("chunkSources");
        mapper.writeValue(profile.toFile(), legacy);
        try (var store = new Repositories(temp.toString(), mapper)) {
            assertTrue(store.jobs.findById("completed").chunkSources.isEmpty());
            assertTrue(store.library.findByFileId(manifest.id()).complete);
        }
    }

    @Test
    void restartRehashesChunksAndRejectsCorruptCheckpoint() throws Exception {
        var mapper = new ObjectMapper();
        var store = new Repositories(temp.toString(), mapper);
        byte[] bytes = new byte[Manifest.CHUNK_SIZE + 20];
        new java.util.Random(7).nextBytes(bytes);
        Path source = temp.resolve("original");
        Files.write(source, bytes);
        var manifest = Manifest.create(source, "sample.bin");
        var entry = new vn.edu.p2p.client.model.Entry();
        entry.manifest = manifest;
        entry.chunks.add(0);
        entry.chunks.add(1);
        Files.createDirectories(store.storage.chunk(manifest.id(), 0).getParent());
        Files.write(
                store.storage.chunk(manifest.id(), 0),
                java.util.Arrays.copyOf(bytes, Manifest.CHUNK_SIZE));
        Files.write(
                store.storage.chunk(manifest.id(), 1),
                new byte[20]); // Deliberately corrupt second chunk.
        store.library.save(manifest.id(), entry);
        var job = new vn.edu.p2p.client.model.Job();
        job.id = "resume-test";
        job.fileId = manifest.id();
        job.status = "DOWNLOADING";
        var origin =
                new vn.edu.p2p.client.model.ChunkSource(
                        "peer-a", "Máy A", "alice", "127.0.0.1", 9001, 123456L);
        job.chunkSources.put(0, origin);
        job.chunkSources.put(1, origin);
        store.jobs.save(job);
        store.storage.saveCheckpoint();
        store.close();
        var restored = new Repositories(temp.toString(), mapper);
        assertEquals(java.util.Set.of(0), restored.library.findByFileId(manifest.id()).chunks);
        assertEquals("PAUSED", restored.jobs.findById(job.id).status);
        assertEquals(java.util.Map.of(0, origin), restored.jobs.findById(job.id).chunkSources);
        // Completed marker alone must never make a missing blob downloadable.
        restored.library.findByFileId(manifest.id()).complete = true;
        restored.storage.saveCheckpoint();
        restored.close();
        try (var checked = new Repositories(temp.toString(), mapper)) {
            assertFalse(checked.library.findByFileId(manifest.id()).complete);
        }
    }
}
