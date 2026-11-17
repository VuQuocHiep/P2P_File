package vn.edu.p2p.server.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "tracker_audit")
public class AuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "event_time")
    public long time;

    @Lob public String actor;
    public String action;
    @Lob public String target;

    public AuditEntity() {}

    public AuditEntity(long time, String actor, String action, String target) {
        this.time = time;
        this.actor = actor;
        this.action = action;
        this.target = target;
    }
}
