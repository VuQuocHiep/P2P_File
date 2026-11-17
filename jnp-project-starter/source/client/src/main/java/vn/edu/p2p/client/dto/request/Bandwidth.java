package vn.edu.p2p.client.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public record Bandwidth(Long uploadLimitKiB, Long downloadLimitKiB) {
    @JsonCreator
    public static Bandwidth fromJson(
            @JsonProperty("uploadLimitKiB") JsonNode upload,
            @JsonProperty("downloadLimitKiB") JsonNode download) {
        return new Bandwidth(wholeNumber(upload), wholeNumber(download));
    }

    private static Long wholeNumber(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToLong())
            throw new IllegalArgumentException("Giới hạn cần là số nguyên KiB/s.");
        return value.longValue();
    }
}
