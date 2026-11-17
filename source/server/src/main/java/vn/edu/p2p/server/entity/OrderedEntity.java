package vn.edu.p2p.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/** Preserves the insertion order of the legacy registry maps. */
@MappedSuperclass
public abstract class OrderedEntity {
    @Column(name = "registry_position", nullable = false)
    public long position;
}
