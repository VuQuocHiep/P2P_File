package vn.edu.p2p.server.mapper;

import org.springframework.stereotype.Component;
import vn.edu.p2p.server.dto.response.FileResponse;
import vn.edu.p2p.server.entity.FileEntity;

@Component
public class FileMapper {
    public FileResponse toResponse(FileEntity file, long onlineSources) {
        return new FileResponse(
                file.manifest, file.status, file.submittedBy, onlineSources, file.holders.size(), file.malware, file.scanStage);
    }
}
