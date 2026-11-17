package vn.edu.p2p.server.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.p2p.server.entity.RoomMemberEntity;

public interface RoomMemberRepository extends JpaRepository<RoomMemberEntity, String> {
    Optional<RoomMemberEntity> findByRoomIdAndUsername(String roomId, String username);

    List<RoomMemberEntity> findByRoomIdOrderByRequestedAtAsc(String roomId);

    List<RoomMemberEntity> findByRoomIdAndStatusOrderByRequestedAtAsc(
            String roomId, String status);

    List<RoomMemberEntity> findByUsername(String username);
}
