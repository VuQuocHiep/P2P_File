package vn.edu.p2p.client.repository;

import java.util.Collection;
import java.util.Set;
import org.springframework.stereotype.Repository;
import vn.edu.p2p.client.model.Job;
import vn.edu.p2p.client.storage.PeerStorage;

/** Download progress and history. Changes are staged until the shared checkpoint is saved. */
@Repository
public class DownloadJobRepository {
    private final PeerStorage storage;

    public DownloadJobRepository(PeerStorage storage) {
        this.storage = storage;
    }

    public Job findById(String id) {
        return storage.state().jobs.get(id);
    }

    public Job findByFileId(String fileId) {
        return storage.state().jobs.values().stream()
                .filter(job -> job.fileId.equals(fileId))
                .findFirst()
                .orElse(null);
    }

    public Collection<Job> findAll() {
        return storage.state().jobs.values();
    }

    public Set<String> findIds() {
        return storage.state().jobs.keySet();
    }

    public void save(Job job) {
        storage.state().jobs.put(job.id, job);
    }

    public void deleteByFileId(String fileId) {
        storage.state().jobs.values().removeIf(job -> fileId.equals(job.fileId));
    }
}
