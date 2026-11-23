# Kiến trúc Hệ thống & Hướng dẫn Mã nguồn PeerMesh

Tài liệu mô tả kiến trúc phân tầng, luồng xử lý nghiệp vụ và cấu trúc tổ chức mã nguồn của hệ thống chia sẻ tệp ngang hàng **PeerMesh**.

---

## 🏛️ Tổng quan Kiến trúc 3 Module

Dự án được tổ chức theo mô hình đa module (Maven Multi-module):

| Module | Đường dẫn | Trách nhiệm chính |
|--------|-----------|-------------------|
| **Server** | `source/server/` | Tracker trung tâm: Quản lý tài khoản, thiết bị peer, danh mục file, cấp vé tải (Ticket $60\text{s}$), quản lý phòng chia sẻ và tích hợp quét mã độc VirusTotal qua MySQL 8. |
| **Client** | `source/client/` | Nút mạng Peer: Quản lý kho tệp cục bộ, truyền nhận chunk qua Socket TCP đa luồng, lưu Checkpoint JSON, quản lý phòng và phòng thí nghiệm (P2P Lab). |
| **Common** | `source/common/` | Thư viện dùng chung: Định nghĩa cấu trúc Manifest, giao thức nhị phân Socket TCP (`PM01`), tiện ích thread/realtime và giao diện web chung. |

---

## 🔄 Luồng Xử lý Nghiệp vụ (Data Flows)

### 1. Luồng Xác thực & Quản trị (Tracker Server)
Mọi thao tác quản trị và yêu cầu điều phối đều tuân thủ kiến trúc phân lớp chuẩn của Spring Boot:
```text
HTTP Request ──► Controller ──► DTO Request ──► Service ──► Repository (JPA) ──► MySQL 8
                      │                                        │
                      ▼                                        ▼
               HTTP Response ◄── DTO Response ◄── Mapper ◄── Entity
```
* **Quản trị phiên:** Sử dụng Session Cookie (SameSite Strict, HttpOnly) cho Web Admin và Bearer Token cho các cuộc gọi từ Peer Java.
* **Thời gian thực:** Cập nhật trạng thái mạng tức thời lên dashboard quản trị qua WebSocket `/ws/admin` ($1\text{s}/\text{lần}$).

### 2. Luồng Tải tệp Đa nguồn (Peer Client)
Quá trình truyền tải dữ liệu P2P phân tách hoàn toàn giữa luồng metadata và luồng nhị phân trực tiếp:
```text
1. Khám phá & Xin vé:
   Peer C ──(HTTP GET /catalog)──► Tracker (Tìm nguồn Seeder A, B)
   Peer C ──(HTTP POST /ticket)──► Tracker (Cấp vé tải bảo mật 60s)

2. Tải Chunk trực tiếp qua TCP (Giao thức PM01):
   Peer C (Coordinator + 4 Workers) ──(TCP Connect + Ticket)──► Peer A & B (TCP Server)
   Peer A & B ──(Kiểm tra vé tải)──► Truyền luồng nhị phân chunk 256 KiB
   Peer C ──(Đối soát SHA-256)──► Ghi vào Blobs ──► Cập nhật Checkpoint JSON
```

### 3. Luồng Kiểm duyệt & Quét Mã độc (VirusTotal)
```text
Peer chia sẻ file ──► Tracker tự tạo ScanJob ──► Báo Seeder qua Heartbeat/Lease
Chưa có báo cáo SHA-256 ──► Seeder tự gửi file ≤ 32 MiB qua Tracker tới VirusTotal
VirusTotal ──► Không có cảnh báo: tự duyệt / Có cảnh báo: chặn
```

---

## 📦 Phân tầng Gói Mã nguồn Java (Backend)

| Package | Trách nhiệm & Thành phần |
|---------|--------------------------|
| `controller/` | Tiếp nhận HTTP Request, xác thực phiên và điều hướng nghiệp vụ. |
| `dto/` | Cấu trúc dữ liệu đầu vào (`dto/request/`) và đầu ra (`dto/response/`). |
| `service/` | Xử lý logic nghiệp vụ, điều phối đa luồng và đồng bộ dữ liệu. |
| `repository/` | Tương tác dữ liệu: Server kế thừa `JpaRepository`; Client lưu trữ qua `storage/PeerStorage`. |
| `entity/` *(Server)* | Các thực thể ánh xạ bảng MySQL 8 (`accounts`, `peers`, `files`, `rooms`, `transfers`, `audit`). |
| `network/` *(Client)* | Động cơ mạng: `TcpChunkServer` (4 upload workers) và `TcpChunkClient` (4 download workers). |
| `storage/` *(Client)* | Quản lý disk: thư mục `blobs/`, file ghép, mã băm SHA-256 và `checkpoint.json`. |
| `websocket/` | Quản lý kết nối WebSocket RFC 6455 và đẩy snapshot dữ liệu theo chu kỳ. |

---

## 🌐 Kiến trúc Giao diện Web (Frontend)

Toàn bộ frontend được xây dựng bằng **Vanilla JavaScript** theo chuẩn **Native ES Modules** (`import/export`), không yêu cầu Node.js hay bundler khi chạy ứng dụng:

```text
source/
├── common/.../assets/                 # Tài nguyên chia sẻ chung
│   ├── css/theme.css                  # Toàn bộ quy chuẩn giao diện Dark Theme
│   └── js/                            # Tiện ích: http.js, dom.js, requests.js, ui.js
├── server/.../static/js/admin/        # Giao diện Quản trị mạng (Tracker)
│   ├── app.js                         # Điểm khởi tạo ứng dụng
│   ├── pages/                         # Màn hình: overview.js
│   ├── components/                    # Thành phần: tables.js, malware.js
│   ├── actions/                       # Hành động: files.js
│   └── store/                         # Quản lý trạng thái: state.js, data.js
└── client/.../static/js/peer/         # Giao diện Nút mạng (Peer Client)
    ├── app.js                         # Điểm khởi tạo ứng dụng
    ├── pages/                         # Màn hình: explore, downloads, library, rooms, lab, activity
    ├── actions/                       # Hành động: bandwidth, downloads, library, rooms, lab
    ├── components/dialogs/            # Hộp thoại: sources.js, chunk-history.js
    └── store/                         # Quản lý trạng thái: state.js, data.js
```

---

## 🛡️ Cơ chế Toàn vẹn & Chịu lỗi

1. **Toàn vẹn tệp tin:** Mỗi tệp được chia nhỏ thành các chunk $256\text{ KiB}$. Mỗi chunk và toàn file đều có mã băm SHA-256 đối soát ngay khi nhận. Chunk sai băm sẽ bị hủy và tải lại từ nguồn khác.
2. **Tự lưu tiến độ (Checkpoint):** Tiến trình tải được cập nhật liên tục vào file `checkpoint.json`. Khi peer khởi động lại, hệ thống đọc lại checkpoint và tiếp tục tải từ các chunk còn thiếu mà không phải tải lại từ đầu.
3. **Phát hiện sự cố & Chuyển nguồn:** Nếu seeder gặp sự cố ngắt kết nối hoặc phản hồi chậm quá thời gian timeout, coordinator tải sẽ tự động chuyển phần chunk đó sang một seeder trực tuyến khác.
4. **Kiểm soát đồng thời:** Cơ chế khóa `JobMutationLock` và `PeerStorage.monitor()` ngăn chặn việc ghi đè trạng thái khi có nhiều luồng cùng thao tác trên một tệp tin.

---

## 🧪 Quy chuẩn Kiểm thử & Chất lượng Mã nguồn

* **Backend Unit & Integration Tests:** Chạy qua Maven (`mvn test`). Kiểm tra tính toán SHA-256, giao thức khung tin TCP, cấp/xác thực vé tải, đa luồng 4 client đồng thời và kiểm thử toàn vẹn Checkpoint.
* **Frontend Tests:** Sử dụng Native Test Runner của Node.js (`npm test`), chạy trực tiếp các module ES của trình duyệt mà không cần cài đặt thư viện bên ngoài.
* **Quy chuẩn mã nguồn:** Định dạng thống nhất qua `.editorconfig` (UTF-8, LF) và `.prettierrc.json`.
