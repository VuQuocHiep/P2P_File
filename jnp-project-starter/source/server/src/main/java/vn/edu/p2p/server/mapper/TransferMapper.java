package vn.edu.p2p.server.mapper;

import org.springframework.stereotype.Component;
import vn.edu.p2p.server.entity.TransferEntity;
import vn.edu.p2p.server.model.Transfer;

@Component
public class TransferMapper {
    public Transfer toResponse(TransferEntity entity) {
        return new Transfer(
                entity.id,
                entity.owner,
                entity.peerId,
                entity.fileId,
                entity.name,
                entity.status,
                entity.bytes,
                entity.time);
    }
}
