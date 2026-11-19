# PeerMesh Client (Peer)

Module nút mạng ngang hàng của hệ thống PeerMesh. Mỗi tiến trình hoạt động độc lập, vừa đóng vai trò chia sẻ file (Seeder) vừa tải file về máy (Leecher) trực tiếp qua kết nối Socket TCP với các peer khác.

---

## 🛠️ Công nghệ cốt lõi

* **Runtime & Framework:** Java 17, Spring Boot (chạy web server cục bộ và điều phối tiến trình)
* **Truyền tải dữ liệu P2P:** Java Socket TCP đa luồng, giao thức nhị phân `PM01`
* **Lưu trữ & Toàn vẹn:** Content-addressed storage, chunk $256\text{ KiB}$, mã băm SHA-256, Checkpoint JSON
* **Giao tiếp thời gian thực:** WebSocket `/ws/peer` cập nhật dữ liệu trực tiếp lên trình duyệt

---

## ⚡ Chức năng chính

* **Tải file đa luồng & đa nguồn:** Chia tải song song từ nhiều peer seeder (4 download workers / file, tối đa 2 file đồng thời), ưu tiên phần hiếm (rarest-first).
* **TCP Server chia sẻ chunk:** Lắng nghe kết nối TCP với 4 upload workers; tự động đối soát vé tải bảo mật ($60\text{s}$) với Tracker trước khi gửi dữ liệu.
* **Toàn vẹn & Phục hồi:** Kiểm tra mã băm SHA-256 cho từng chunk và toàn file; tự động lưu checkpoint để tạm dừng hoặc tiếp tục tải (resume) sau khi khởi động lại.
* **Điều tiết băng thông (Rate Limiter):** Cài đặt giới hạn tốc độ Upload / Download theo KiB/s áp dụng ngay lập tức cho các luồng TCP.
* **Phòng chia sẻ riêng tư (Shared Rooms):** Tạo nhóm chia sẻ có mật khẩu, duyệt yêu cầu tham gia và tải file nội bộ nhóm.
* **Phòng thí nghiệm P2P (Lab):** Biểu đồ tốc độ Live (120 mẫu), đo đạc so sánh hiệu năng tải 1 nguồn vs đa nguồn, giả lập lỗi mạng (nguồn chậm, ngắt socket, chunk sai băm) và tự động chuyển nguồn.
* **Tích hợp kiểm tra VirusTotal:** Seeder chủ động bật quyền cho phép gửi file mẫu ($\le 32\text{ MiB}$) lên VirusTotal khi admin yêu cầu.
* **Đồng bộ lệnh xóa tệp:** Nhận danh sách file bị thu hồi từ Tracker, tự động dừng phiên tải và dọn dẹp các bản chunk/blob cục bộ.

---

## 🚀 Khởi động nhanh

Từ thư mục gốc dự án:

```powershell
# Chạy Peer 1 (Web UI 8081, TCP Port 9001)
powershell -ExecutionPolicy Bypass -File source/run.ps1 -Role peer -WebPort 8081 -TcpPort 9001

# Chạy thêm Peer 2 trên cùng máy (Web UI 8082, TCP Port 9002)
powershell -ExecutionPolicy Bypass -File source/run.ps1 -Role peer -WebPort 8082 -TcpPort 9002
```

* **Giao diện Web:** Mở trình duyệt tại [http://127.0.0.1:8081](http://127.0.0.1:8081).
* **Đăng ký thiết bị:** Đăng nhập tài khoản, khai báo IP/Cổng TCP và chờ quản trị viên duyệt thiết bị trên Tracker.
* **Dữ liệu cục bộ:** Mỗi instance phân biệt theo cổng web và lưu profile, file blobs, checkpoint độc lập tại thư mục `source/data/peer-<PORT>`.

---

## 🧩 Cấu trúc mã nguồn

```text
source/client/
├── src/main/java/vn/edu/p2p/client/
│   ├── controller/      # REST API phục vụ giao diện web cục bộ
│   ├── service/         # Nghiệp vụ tải/gửi chunk, phòng chia sẻ, lab, quản lý file
│   ├── network/         # TCP Server (upload), TCP Client (download) và HTTP Gateway
│   ├── storage/         # Quản lý đọc/ghi disk, profile, checkpoint JSON và khóa file
│   ├── repository/      # Quản lý dữ liệu bộ nhớ (Profile, Library, DownloadJob, Events)
│   ├── websocket/       # Đẩy snapshot hoạt động peer qua /ws/peer
│   └── dto/             # Đối tượng dữ liệu vào/ra API
└── src/main/resources/
    ├── application.properties
    └── static/          # Giao diện người dùng Web (6 tab: Khám phá, Phiên tải, Thư viện, Phòng, Lab, Hoạt động)
```

---

## 🔗 Liên kết liên quan

* Hướng dẫn tổng thể dự án: [README.md](../../README.md)
* Thiết kế luồng xử lý chi tiết: [ARCHITECTURE.md](../../ARCHITECTURE.md)
