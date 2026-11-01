package vn.edu.p2p.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/** Maps the existing registry row without changing its JSON payload format. */
@Entity
@Table(name = "registry")
public class RegistryEntity {
    @Id private Integer id;

    @Lob
    @Column(nullable = false, columnDefinition = "CLOB")
    private String payload;

    protected RegistryEntity() {}

    public RegistryEntity(Integer id, String payload) {
        this.id = id;
        this.payload = payload;
    }

    public Integer getId() {
        return id;
    }

    public String getPayload() {
        return payload;
    }
}
