package vn.edu.p2p.server.entity;

import jakarta.persistence.*;

/** A share room: a named, optionally password-protected group of accounts. */
@Entity
@Table(name = "tracker_rooms")
public class RoomEntity extends OrderedEntity {
    @Id public String id;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public String owner;

    /** BCrypt hash; null means the room has no password. */
    public String passwordHash;

    @Column(nullable = false)
    public String status = "ACTIVE";

    public long created;
}
