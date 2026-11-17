package vn.edu.p2p.server.mapper;

import org.springframework.stereotype.Component;
import vn.edu.p2p.server.entity.AuditEntity;
import vn.edu.p2p.server.model.Audit;

@Component
public class AuditMapper {
    public Audit toResponse(AuditEntity entity) {
        return new Audit(entity.time, entity.actor, entity.action, entity.target);
    }
}
