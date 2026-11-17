package vn.edu.p2p.server.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.p2p.server.entity.RoomEntity;

public interface RoomRepository extends JpaRepository<RoomEntity, String> {
    Optional<RoomEntity> findRoomById(String id);

    List<RoomEntity> findAllByOrderByPositionAsc();

    Optional<RoomEntity> findFirstByOrderByPositionDesc();
}
