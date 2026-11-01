package vn.edu.p2p.server.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import vn.edu.p2p.server.entity.PeerEntity;

public interface PeerRepository extends JpaRepository<PeerEntity, String> {
    Optional<PeerEntity> findPeerById(String id);

    List<PeerEntity> findAllByOrderByPositionAsc();

    Optional<PeerEntity> findFirstByOrderByPositionDesc();

    @Modifying
    @Query("update PeerEntity p set p.lastSeen = 0")
    void resetLastSeen();
}
