package vn.edu.p2p.server.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "tracker_transfers")
public class TransferEntity extends OrderedEntity {
    @Id
    @Column(name = "transfer_key")
    public String key;

    public String id, owner, peerId, fileId;
    @Lob public String name;
    public String status;

    @Column(name = "transferred_bytes")
    public long bytes;

    @Column(name = "updated_at")
    public long time;

    public TransferEntity() {}

    public TransferEntity(
            String key,
            String id,
            String owner,
            String peerId,
            String fileId,
            String name,
            String status,
            long bytes,
            long time) {
        this.key = key;
        this.id = id;
        this.owner = owner;
        this.peerId = peerId;
        this.fileId = fileId;
        this.name = name;
        this.status = status;
        this.bytes = bytes;
        this.time = time;
    }
}
