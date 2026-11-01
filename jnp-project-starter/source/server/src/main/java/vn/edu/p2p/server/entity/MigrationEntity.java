package vn.edu.p2p.server.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "tracker_migrations")
public class MigrationEntity {
    @Id public String name;

    public MigrationEntity() {}

    public MigrationEntity(String name) {
        this.name = name;
    }
}
