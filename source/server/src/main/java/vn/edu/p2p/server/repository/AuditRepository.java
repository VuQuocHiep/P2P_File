package vn.edu.p2p.server.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.p2p.server.entity.AuditEntity;

public interface AuditRepository extends JpaRepository<AuditEntity, Long> {
    List<AuditEntity> findAllByOrderByIdAsc();

    Optional<AuditEntity> findFirstByOrderByIdAsc();
}
