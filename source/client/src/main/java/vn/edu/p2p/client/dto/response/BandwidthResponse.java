package vn.edu.p2p.client.dto.response;

public record BandwidthResponse(
        long uploadLimitKiB,
        long downloadLimitKiB,
        long uploadBytesPerSecond,
        long downloadBytesPerSecond) {}
