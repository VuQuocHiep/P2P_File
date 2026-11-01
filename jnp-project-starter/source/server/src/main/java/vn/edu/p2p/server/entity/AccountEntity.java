package vn.edu.p2p.server.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "tracker_accounts")
public class AccountEntity extends OrderedEntity {
    @Id public String username;
    public String passwordHash, role, status;
    public long created;

    public AccountEntity() {}

    public AccountEntity(String username, String passwordHash, String role, String status) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = status;
        this.created = System.currentTimeMillis();
    }
}
