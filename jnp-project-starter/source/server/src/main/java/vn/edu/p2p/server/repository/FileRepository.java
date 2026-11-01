package vn.edu.p2p.server.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.p2p.server.entity.FileEntity;

public interface FileRepository extends JpaRepository<FileEntity, String> {
    Optional<FileEntity> findFileById(String id);

    List<FileEntity> findAllByOrderByPositionAsc();

    Optional<FileEntity> findFirstByOrderByPositionDesc();
}
