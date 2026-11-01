package vn.edu.p2p.server.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "tracker_peers")
public class PeerEntity extends OrderedEntity {
    @Id public String id;
    public String owner, name, host, status;
    public int port;
    public long lastSeen;
}
