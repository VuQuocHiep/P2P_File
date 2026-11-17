package vn.edu.p2p.server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Durable tombstone: offline peers must not re-advertise a deleted content hash. */
@Entity
@Table(name = "tracker_file_deletions")
public class FileDeletionEntity {
    @Id public String fileId;
    public long deletedAt;

    public FileDeletionEntity() {}

    public FileDeletionEntity(String fileId, long deletedAt) {
        this.fileId = fileId;
        this.deletedAt = deletedAt;
    }
}
