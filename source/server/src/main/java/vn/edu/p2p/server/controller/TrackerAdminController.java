package vn.edu.p2p.server.controller;

import vn.edu.p2p.server.service.TrackerAdminService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import vn.edu.p2p.server.dto.response.*;
import java.util.*;
import vn.edu.p2p.server.dto.request.*;

import static vn.edu.p2p.server.controller.TrackerRequestAuth.token;

@RestController
@RequestMapping("/api")
public class TrackerAdminController {
    private final TrackerAdminService service;

    public TrackerAdminController(TrackerAdminService service) {
        this.service = service;
    }

    @GetMapping("/admin/dashboard")
    public DashboardResponse dashboard(HttpServletRequest r) {
        return service.dashboard(token(r));
    }

    @DeleteMapping("/admin/files/{id}")
    public MessageResponse deleteFile(@PathVariable String id, HttpServletRequest r) {
        service.deleteFile(token(r), id);
        return new MessageResponse(
                "Đã xóa khỏi tracker. Các peer sẽ xóa bản lưu của ứng dụng khi đồng bộ; peer offline xử lý khi kết nối lại.");
    }

    @PatchMapping("/admin/{kind}/{id}")
    public MessageResponse moderate(
            @PathVariable String kind,
            @PathVariable String id,
            @RequestBody Moderation b,
            HttpServletRequest r) {
        service.moderate(token(r), kind, id, b.status());
        return new MessageResponse("Đã cập nhật trạng thái.");
    }
}
