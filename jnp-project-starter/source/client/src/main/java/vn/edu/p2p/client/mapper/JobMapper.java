package vn.edu.p2p.client.mapper;

import java.util.LinkedHashMap;
import org.springframework.stereotype.Component;
import vn.edu.p2p.client.dto.response.DownloadResponse;
import vn.edu.p2p.client.dto.response.PeerStatusResponse.JobResponse;
import vn.edu.p2p.client.model.Entry;
import vn.edu.p2p.client.model.Job;

@Component
public class JobMapper {
    public JobResponse toResponse(Job job, Entry entry, long completedBytes, long speed) {
        return new JobResponse(
                job.id,
                job.fileId,
                entry.manifest.name(),
                job.status,
                completedBytes,
                entry.manifest.size(),
                speed,
                job.error,
                new LinkedHashMap<>(job.sourceBytes),
                entry.chunks.size());
    }

    public DownloadResponse toDownloadResponse(Job job) {
        return new DownloadResponse(job.id);
    }
}
