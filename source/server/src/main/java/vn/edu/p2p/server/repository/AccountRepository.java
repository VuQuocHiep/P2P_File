package vn.edu.p2p.server.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.p2p.server.entity.AccountEntity;

public interface AccountRepository extends JpaRepository<AccountEntity, String> {
    List<AccountEntity> findAllByOrderByPositionAsc();

    Optional<AccountEntity> findFirstByOrderByPositionDesc();

    Optional<AccountEntity> findByUsername(String username);

    boolean existsByUsername(String username);
}
