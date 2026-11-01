package vn.edu.p2p.server.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.p2p.server.entity.FileDeletionEntity;

public interface FileDeletionRepository extends JpaRepository<FileDeletionEntity, String> {
    List<FileDeletionEntity> findAllByOrderByDeletedAtAsc();
}
