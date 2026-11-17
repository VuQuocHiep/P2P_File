package vn.edu.p2p.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.edu.p2p.server.dto.response.MalwareReport;

@Component
public class VirusTotalClient {
    public static final int MAX_UPLOAD = 32 * 1024 * 1024;
    private final String key;
    private final String base;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private long nextRequestAt;

    public VirusTotalClient(@Value("${virustotal.api-key:}") String key,
            @Value("${virustotal.base-url:https://www.virustotal.com/api/v3}") String base,
            ObjectMapper json) {
        this.key = key; this.base = base; this.json = json;
    }

    private synchronized void reserveRequest() {
        if (key.isBlank()) throw new IllegalArgumentException("Server chưa cấu hình VIRUSTOTAL_API_KEY.");
        long now = System.currentTimeMillis();
        if (now < nextRequestAt) throw new IllegalArgumentException(
                "Giới hạn VirusTotal: thử lại sau " + ((nextRequestAt - now + 999) / 1000) + " giây.");
        nextRequestAt = now + 15_000;
    }

    private JsonNode request(String path, HttpRequest.BodyPublisher body, String contentType) throws Exception {
        reserveRequest();
        var builder = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(25)).header("x-apikey", key).header("Accept", "application/json");
        if (body != null) builder.header("Content-Type", contentType).POST(body);
        var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        switch (response.statusCode()) {
            case 200 -> { return json.readTree(response.body()); }
            case 404 -> { return null; }
            case 401, 403 -> throw new IllegalArgumentException("API key VirusTotal không hợp lệ hoặc không đủ quyền.");
            case 429 -> throw new IllegalArgumentException("VirusTotal đã hết hạn mức. Hãy chờ rồi thử lại.");
            default -> throw new IllegalArgumentException("VirusTotal trả lỗi HTTP " + response.statusCode() + ".");
        }
    }

    public MalwareReport lookup(String hash) throws Exception {
        var root = request("/files/" + hash, null, null);
        if (root == null) return MalwareReport.state("UNKNOWN", "VirusTotal chưa có dữ liệu cho SHA-256 này. Có thể gửi đúng file để quét.", null);
        var data = root.path("data");
        if (!hash.equals(data.path("id").asText()) || !data.path("attributes").isObject())
            throw new IllegalArgumentException("Báo cáo VirusTotal không khớp SHA-256 của file.");
        var attrs = data.path("attributes");
        var stats = attrs.path("last_analysis_stats");
        int malicious = count(stats, "malicious"), suspicious = count(stats, "suspicious");
        int undetected = count(stats, "undetected"), harmless = count(stats, "harmless");
        int total = 0;
        for (var it = stats.elements(); it.hasNext();) total += Math.max(0, it.next().asInt());
        var engines = new ArrayList<MalwareReport.Engine>();
        attrs.path("last_analysis_results").fields().forEachRemaining(entry -> {
            var item = entry.getValue();
            String category = item.path("category").asText();
            if (Set.of("malicious", "suspicious").contains(category))
                engines.add(new MalwareReport.Engine(entry.getKey(), category, item.path("result").asText("")));
        });
        String status = malicious > 0 ? "MALICIOUS" : suspicious > 0 ? "SUSPICIOUS"
                : undetected + harmless > 0 ? "UNDETECTED" : "UNKNOWN";
        return new MalwareReport(status, System.currentTimeMillis(), attrs.path("last_analysis_date").asLong() * 1000,
                malicious, suspicious, undetected, harmless, total,
                "UNDETECTED".equals(status) ? "Chưa phát hiện mã độc trong báo cáo hiện có; không đảm bảo an toàn tuyệt đối."
                : "UNKNOWN".equals(status) ? "Chưa có công cụ nào hoàn thành đánh giá file."
                : "Có công cụ cảnh báo. File bị chặn duyệt; xem chi tiết và báo cáo VirusTotal.", null, engines);
    }

    private int count(JsonNode stats, String field) { return Math.max(0, stats.path(field).asInt()); }

    public MalwareReport upload(byte[] bytes) throws Exception {
        String boundary = "PeerMesh" + UUID.randomUUID().toString().replace("-", "");
        var body = HttpRequest.BodyPublishers.concat(
                HttpRequest.BodyPublishers.ofString("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"sample.bin\"\r\nContent-Type: application/octet-stream\r\n\r\n", StandardCharsets.UTF_8),
                HttpRequest.BodyPublishers.ofByteArray(bytes),
                HttpRequest.BodyPublishers.ofString("\r\n--" + boundary + "--\r\n"));
        var root = request("/files", body, "multipart/form-data; boundary=" + boundary);
        String id = root == null ? "" : root.path("data").path("id").asText();
        if (!id.matches("[a-zA-Z0-9_=-]{1,512}")) throw new IllegalArgumentException("VirusTotal chưa trả mã phiên quét hợp lệ.");
        return MalwareReport.state("QUEUED", "Đã gửi file. Chờ ít nhất 15 giây rồi bấm Cập nhật kết quả; VirusTotal có thể cần vài phút.", id);
    }

    public MalwareReport poll(String hash, String analysisId) throws Exception {
        var root = request("/analyses/" + analysisId, null, null);
        if (root == null) throw new IllegalArgumentException("Không tìm thấy phiên quét VirusTotal.");
        String status = root.path("data").path("attributes").path("status").asText();
        if (!Set.of("queued", "in-progress", "completed").contains(status))
            throw new IllegalArgumentException("Trạng thái phiên quét VirusTotal không hợp lệ.");
        String returnedHash = root.path("meta").path("file_info").path("sha256").asText();
        if (!returnedHash.isBlank() && !hash.equals(returnedHash))
            throw new IllegalArgumentException("Phiên quét không khớp SHA-256 của file.");
        if (!"completed".equals(status))
            return MalwareReport.state("QUEUED", "VirusTotal đang xử lý. Chờ ít nhất 15 giây rồi cập nhật lại.", analysisId);
        // A separate hash lookup binds the eventual report to the registered file.
        return MalwareReport.state("READY", "Quét hoàn tất. Chờ ít nhất 15 giây rồi bấm Lấy báo cáo theo SHA-256.", analysisId);
    }
}
