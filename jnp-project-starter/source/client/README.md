# PeerMesh Client / Peer

Mỗi instance vừa tải vừa gửi chunk qua TCP. Java 17 + Spring Boot cung cấp web cục bộ, gọi tracker, tải song song và lưu checkpoint.

Hướng dẫn đầy đủ về build, cấu hình, chạy, API, giao thức và demo nằm ở [README chính](../../README.md).

Từ thư mục repository, sau khi build và chạy tracker:

```powershell
powershell -ExecutionPolicy Bypass -File source/run.ps1 -Role peer -WebPort 8081 -TcpPort 9001
```

Mở http://127.0.0.1:8081. Đăng ký / đăng nhập, nhập IP máy chạy peer và chờ admin duyệt thiết bị. Mỗi instance cần cổng web, cổng TCP và profile riêng khi chạy cùng máy. Script cấp profile theo cổng web.

Mã nguồn: src/main/java/vn/edu/p2p/client/; web: src/main/resources/static/; checkpoint test: src/test/java/; E2E: src/test/resources/e2e.py.

Package Java: `controller/`, `service/`, `repository/`, `storage/`, `model/`, `dto/request/`, `dto/response/`, `mapper/`, `config/`, `websocket/`, `exception/`; thêm `network/` cho TCP và HTTP client.

Giao diện nhận snapshot trực tiếp qua `/ws/peer`; HTTP polling chỉ dùng dự phòng khi WebSocket mất kết nối.

Trong **Phiên tải của tôi**, bấm **Nguồn từng chunk** để xem phần nào nhận từ peer nào, tài khoản nguồn, địa chỉ TCP và thời điểm nhận. Lịch sử được lưu sau khi chunk đã qua kiểm tra SHA-256, giữ lại sau khi hoàn tất và khởi động lại peer. Bảng hiển thị 100 phần mỗi trang, đánh số từ 1. Các phiên tải cũ không có lịch sử sẽ ghi rõ thay vì suy đoán nguồn. API đọc lịch sử: `GET /api/downloads/{id}/chunks` (cần đăng nhập).

Mục **Phòng thí nghiệm** có sơ đồ kết nối TCP tại peer, biểu đồ tốc độ 120 mẫu, phép so sánh một nguồn / nhiều nguồn qua TCP và mô phỏng nguồn chậm / ngắt / sai hash có tự hết hạn. Cách đo, API và kịch bản demo nằm trong mục Phòng thí nghiệm P2P của README chính.

Controller gọi trực tiếp service chức năng được Spring quản lý bằng `@Service`, nhận DTO trong `dto/request/` và trả DTO trong `dto/response/`. Các tên trường JSON và endpoint được giữ nguyên.

Package `repository/` được chia theo dữ liệu: `PeerProfileRepository`, `LibraryRepository`, `DownloadJobRepository`, `PeerEventRepository`. `storage/PeerStorage` quản lý đọc/ghi file, khóa profile và khôi phục checkpoint. Service cập nhật qua repository và gọi `saveCheckpoint()` ở cùng các thời điểm như trước; giao diện và định dạng dữ liệu được giữ nguyên.

Xem [hướng dẫn cấu trúc dự án](../../ARCHITECTURE.md). Giao diện được chia theo trạng thái, xác thực, API, realtime, view và sự kiện trong `src/main/resources/static/js/`; `app.js` là file khởi động được nạp cuối.

`PeerDeletionService` nhận lệnh xóa toàn mạng từ tracker. `PeerStorage` lưu `deletedFiles` trong checkpoint, chỉ xóa đường dẫn được định danh bằng SHA-256 trong profile; file gốc bên ngoài profile được giữ nguyên. Peer offline xử lý khi kết nối và đăng nhập lại.
