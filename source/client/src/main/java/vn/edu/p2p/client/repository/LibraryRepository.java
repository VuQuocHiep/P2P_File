package vn.edu.p2p.client.repository;

import java.util.Collection;
import java.util.Set;
import org.springframework.stereotype.Repository;
import vn.edu.p2p.client.model.Entry;
import vn.edu.p2p.client.storage.PeerStorage;

/**
 * Shared-file entries. Callers hold the storage monitor while reading or updating managed models.
 */
@Repository
public class LibraryRepository {
    private final PeerStorage storage;

    public LibraryRepository(PeerStorage storage) {
        this.storage = storage;
    }

    public Entry findByFileId(String fileId) {
        return storage.state().library.get(fileId);
    }

    public Collection<Entry> findAll() {
        return storage.state().library.values();
    }

    public Set<String> findFileIds() {
        return storage.state().library.keySet();
    }

    public void save(String fileId, Entry entry) {
        storage.state().library.put(fileId, entry);
    }

    public void deleteByFileId(String fileId) {
        storage.state().library.remove(fileId);
    }
}
