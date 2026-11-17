package vn.edu.p2p.client.network;

import vn.edu.p2p.client.exception.TrackerFailure;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;

@Component
public class TrackerGateway {
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper json;
    private final String base;

    public String base() {
        return base;
    }

    public String token() {
        return token;
    }

    public void token(String value) {
        token = value;
    }

    volatile String token = "";

    public TrackerGateway(@Value("${peer.tracker}") String base, ObjectMapper json) {
        this.base = base.replaceAll("/$", "");
        this.json = json;
        URI uri = URI.create(this.base);
        if (!java.util.Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null)
            throw new IllegalArgumentException("TRACKER_URL không hợp lệ.");
    }

    public JsonNode call(String method, String path, Object body) throws Exception {
        var request =
                HttpRequest.newBuilder(URI.create(base + path))
                        .timeout(Duration.ofSeconds(6))
                        .header("X-PeerMesh", "1")
                        .header("Content-Type", "application/json");
        String current = token;
        if (!current.isBlank()) request.header("Authorization", "Bearer " + current);
        request.method(
                method,
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode result = json.readTree(response.body());
        if (response.statusCode() >= 400)
            throw new TrackerFailure(
                    response.statusCode(),
                    result.path("message")
                            .asText(result.path("detail").asText("Tracker từ chối yêu cầu.")));
        return result;
    }

    public JsonNode uploadScan(java.nio.file.Path file, String peerId, String fileId, String lease) throws Exception {
        String boundary = "PeerMesh" + java.util.UUID.randomUUID().toString().replace("-", "");
        var body = HttpRequest.BodyPublishers.concat(
                HttpRequest.BodyPublishers.ofString("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"sample.bin\"\r\nContent-Type: application/octet-stream\r\n\r\n"),
                HttpRequest.BodyPublishers.ofFile(file),
                HttpRequest.BodyPublishers.ofString("\r\n--" + boundary + "--\r\n"));
        var request = HttpRequest.newBuilder(URI.create(base + "/api/peers/" + peerId + "/scan-jobs/" + fileId + "/content"))
                .timeout(Duration.ofSeconds(90)).header("X-PeerMesh", "1")
                .header("Authorization", "Bearer " + token).header("X-Scan-Lease", lease)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary).POST(body).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        var result = json.readTree(response.body());
        if (response.statusCode() >= 400) throw new TrackerFailure(response.statusCode(),
                result.path("message").asText(result.path("detail").asText("Không gửi được file để quét.")));
        return result;
    }

    public void releaseScan(String peerId, String fileId, String lease) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + "/api/peers/" + peerId + "/scan-jobs/" + fileId + "/release"))
                .timeout(Duration.ofSeconds(6)).header("X-PeerMesh", "1")
                .header("Authorization", "Bearer " + token).header("X-Scan-Lease", lease)
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        http.send(request, HttpResponse.BodyHandlers.discarding());
    }
}
