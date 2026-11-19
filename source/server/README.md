# PeerMesh Server (Tracker)

Module máy chủ điều phối trung tâm của hệ thống PeerMesh. Quản lý toàn bộ định danh, quyền hạn, metadata tệp tin và điều phối kết nối giữa các peer. Server chỉ lưu trữ metadata, không lưu hay truyền nội dung file.

---

## 🛠️ Công nghệ cốt lõi

* **Runtime & Framework:** Java 17, Spring Boot 3.5.16
* **Cơ sở dữ liệu:** MySQL 8 qua Spring Data JPA / Hibernate
* **Giao tiếp thời gian thực:** Spring WebSocket (RFC 6455)
* **Bảo mật:** BCrypt (mật khẩu), Token / Session 12 giờ, Tích hợp VirusTotal API

---

## ⚡ Chức năng chính

* **Quản trị người dùng & thiết bị:** Phê duyệt / khóa tài khoản, thiết bị peer tham gia mạng.
* **Kiểm duyệt danh mục file:** Quản lý metadata, mã băm SHA-256 từng chunk và toàn file.
* **Cấp vé tải an toàn (Ticket):** Sinh vé tải có thời hạn $60\text{s}$ gắn với từng nguồn; seeder đối soát vé trước khi truyền chunk.
* **Quản lý phòng chia sẻ (Shared Rooms):** Hỗ trợ tạo nhóm chia sẻ kín kèm mã phòng và mật khẩu bảo vệ.
* **Quét mã độc (VirusTotal):** Tra cứu tự động mã băm SHA-256 và nhận file mẫu ($\le 32\text{ MiB}$) do seeder gửi lên khi admin yêu cầu.
* **Giám sát mạng & Heartbeat:** Nhận tín hiệu sống của peer mỗi $5\text{s}$, tự động đánh dấu offline sau $20\text{s}$ không nhận được heartbeat.
* **Đồng bộ lệnh xóa toàn mạng:** Thu hồi vé tải và lưu vết xóa (tombstone) bền vững để các peer tự dọn dẹp file.

---

## 🚀 Khởi động nhanh

Từ thư mục gốc dự án:

```powershell
# Thiết lập mật khẩu quản trị và khởi động tracker (cổng 8080)
$env:ADMIN_PASSWORD = 'ThayBangMatKhauRieng!2026'
powershell -ExecutionPolicy Bypass -File source/run.ps1 -Role server
```

* **Giao diện Quản trị:** Truy cập [http://127.0.0.1:8080](http://127.0.0.1:8080) (tài khoản: `admin`).
* **Cấu hình MySQL:** Mặc định kết nối `localhost:3306/peermesh` (user: `root`). Có thể tùy biến qua biến môi trường: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`.
* **Khóa quét virus (tùy chọn):** Thiết lập `$env:VIRUSTOTAL_API_KEY = 'your_api_key'` trước khi chạy server.

---

## 🧩 Cấu trúc mã nguồn

```text
source/server/
├── src/main/java/vn/edu/p2p/server/
│   ├── controller/      # REST API endpoints (quản trị, peer, phòng, file)
│   ├── service/         # Nghiệp vụ điều phối, duyệt tài khoản/file, cấp vé, quét virus
│   ├── repository/      # Spring Data JPA repositories thao tác MySQL 8
│   ├── entity/          # JPA entities (Account, Peer, File, Room, Transfer, Audit...)
│   ├── dto/             # Đối tượng truyền nhận dữ liệu (request / response)
│   ├── websocket/       # Push snapshot trạng thái mạng qua /ws/admin (1s/lần)
│   └── migration/       # Tự động đồng bộ và chuyển đổi dữ liệu từ snapshot cũ
└── src/main/resources/
    ├── application.properties
    ├── schema-mysql.sql # Kịch bản khởi tạo database MySQL
    └── static/          # Giao diện Admin Console (HTML5, CSS, Vanilla JS)
```

---

## 🔗 Liên kết liên quan

* Hướng dẫn tổng thể dự án: [README.md](../../README.md)
* Thiết kế luồng xử lý chi tiết: [ARCHITECTURE.md](../../ARCHITECTURE.md)
