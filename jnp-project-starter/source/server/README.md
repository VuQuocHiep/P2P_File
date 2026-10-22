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

Package Java: `controller/`, `service/`, `repository/`, `model/`, `dto/`, `config/`, `websocket/`, `exception/`.

Giao diện nhận snapshot trực tiếp qua `/ws/admin`; HTTP polling chỉ dùng dự phòng khi WebSocket mất kết nối.
