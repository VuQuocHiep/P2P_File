package vn.edu.p2p.client.service;

import org.springframework.stereotype.Service;
import vn.edu.p2p.client.dto.request.Bandwidth;
import vn.edu.p2p.client.dto.response.BandwidthResponse;
import vn.edu.p2p.client.network.BandwidthLimiter;

@Service
public class PeerBandwidthService {
    private final PeerContext context;

    public PeerBandwidthService(PeerContext context) {
        this.context = context;
    }

    public BandwidthResponse settings() {
        synchronized (context.storage.monitor()) {
            return new BandwidthResponse(
                    context.profile.getUploadLimitKiB(),
                    context.profile.getDownloadLimitKiB(),
                    context.uploadBytesPerSecond,
                    context.downloadBytesPerSecond);
        }
    }

    public BandwidthResponse update(Bandwidth input) {
        if (input == null || !valid(input.uploadLimitKiB()) || !valid(input.downloadLimitKiB()))
            throw new IllegalArgumentException(
                    "Điền đủ giới hạn upload và download: số nguyên 0–1048576 KiB/s; 0 là không giới hạn.");
        synchronized (context.storage.monitor()) {
            long oldUpload = context.profile.getUploadLimitKiB();
            long oldDownload = context.profile.getDownloadLimitKiB();
            context.profile.updateBandwidth(input.uploadLimitKiB(), input.downloadLimitKiB());
            try {
                context.storage.saveCheckpoint();
            } catch (RuntimeException failure) {
                context.profile.updateBandwidth(oldUpload, oldDownload);
                throw failure;
            }
            context.uploadBandwidth.setLimit(input.uploadLimitKiB());
            context.downloadBandwidth.setLimit(input.downloadLimitKiB());
            return settings();
        }
    }

    private static boolean valid(Long value) {
        return value != null && value >= 0 && value <= BandwidthLimiter.MAX_LIMIT_KIB;
    }
}
