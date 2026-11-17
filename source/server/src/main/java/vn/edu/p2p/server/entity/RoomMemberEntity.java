package vn.edu.p2p.server.entity;

import jakarta.persistence.*;

/** Join request / membership row: PENDING until the room owner decides. */
@Entity
@Table(
        name = "tracker_room_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "username"}))
public class RoomMemberEntity {
    @Id public String id;

    @Column(name = "room_id", nullable = false)
    public String roomId;

    @Column(nullable = false)
    public String username;

    @Column(nullable = false)
    public String status = "PENDING";

    public long requestedAt, decidedAt;
}
