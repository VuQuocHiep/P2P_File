package vn.edu.p2p.client.exception;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.Map;
import vn.edu.p2p.client.network.TrackerGateway;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> invalid(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    @ExceptionHandler(TrackerGateway.TrackerFailure.class)
    ResponseEntity<?> tracker(TrackerGateway.TrackerFailure e) {
        return ResponseEntity.status(e.status).body(Map.of("message", e.getMessage()));
    }
    @ExceptionHandler(java.net.ConnectException.class)
    ResponseEntity<?> offline(java.net.ConnectException e) {
        return ResponseEntity.status(503).body(Map.of("message", "Không kết nối được tracker. Kiểm tra server và TRACKER_URL."));
    }
}
