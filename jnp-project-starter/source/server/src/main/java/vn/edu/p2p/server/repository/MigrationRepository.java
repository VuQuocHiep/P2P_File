package vn.edu.p2p.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.p2p.server.entity.MigrationEntity;

public interface MigrationRepository extends JpaRepository<MigrationEntity, String> {}
