package vn.edu.p2p.server.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.p2p.server.entity.TransferEntity;

public interface TransferRepository extends JpaRepository<TransferEntity, String> {
    void deleteByFileId(String fileId);

    List<TransferEntity> findAllByOrderByPositionAsc();

    Optional<TransferEntity> findFirstByOrderByPositionDesc();

    Optional<TransferEntity> findFirstByOrderByPositionAsc();
}
