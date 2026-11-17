# PeerMesh Server / Tracker

Java 17 + Spring Boot 3.5.16 + H2. Quản lý tài khoản, duyệt peer / file, tìm nguồn, cấp vé tải, dashboard và nhật ký.

Hướng dẫn đầy đủ về build, cấu hình, chạy, API, giao thức và demo nằm ở [README chính](../../README.md).

Từ thư mục repository:

```powershell
mvn -f source/pom.xml package
$env:ADMIN_PASSWORD = 'ThayBangMatKhauRieng!2026'
powershell -ExecutionPolicy Bypass -File source/run.ps1 -Role server
```

Mở http://127.0.0.1:8080. Tài khoản bootstrap mặc định admin, mật khẩu cấu hình lần đầu. Tracker lưu metadata, không lưu nội dung file.

Mã nguồn: src/main/java/vn/edu/p2p/server/; web: src/main/resources/static/; cấu hình: application.properties; kiểm thử: src/test/java/.

Package Java: `controller/`, `service/`, `repository/`, `model/`, `entity/`, `dto/request/`, `dto/response/`, `mapper/`, `config/`, `websocket/`, `exception/`.

Giao diện nhận snapshot trực tiếp qua `/ws/admin`; HTTP polling chỉ dùng dự phòng khi WebSocket mất kết nối.

Controller gọi trực tiếp service chức năng được Spring quản lý bằng `@Service`, nhận DTO trong `dto/request/` và trả DTO trong `dto/response/`. Các tên trường JSON và endpoint được giữ nguyên.

Các repository nghiệp vụ `AccountRepository`, `PeerRepository`, `FileRepository`, `TransferRepository`, `AuditRepository` kế thừa `JpaRepository<Entity, Id>` và không cần `repository/impl/`. Service gọi repository trong transaction, giữ khóa tracker chung đến khi commit/rollback xong. Mapper chuyển entity thành response DTO, giữ tên trường JSON của API.

`migration/LegacyRegistryMigration` nhập snapshot JSON cũ sang các bảng nghiệp vụ trong một transaction. Giữ nguyên dòng `registry.id=1` làm bản lưu dữ liệu cũ; `tracker_migrations` đánh dấu hoàn thành để các thay đổi mới không bị ghi đè khi restart. Manifest và danh sách chunk của từng file dùng converter JSON. `spring.jpa.hibernate.ddl-auto=update` giữ dữ liệu hiện có và tạo các bảng mới.

Xem [hướng dẫn cấu trúc dự án](../../ARCHITECTURE.md). Giao diện được chia theo trạng thái, xác thực, API, realtime, view và sự kiện trong `src/main/resources/static/js/`; `app.js` là file khởi động được nạp cuối.

`DELETE /api/admin/files/{id}` chỉ cho ADMIN: xóa file, báo cáo liên quan và thu hồi vé, đồng thời lưu `FileDeletionEntity`. `GET /api/files/deletions` cho phiên đăng nhập còn hạn nhận các ID cần dọn, kể cả phiên bị khóa để vẫn xóa được các bản lưu. Dấu xóa được giữ qua restart để peer offline không làm file xuất hiện lại.
