# Kết quả xác minh PeerMesh — 16/10/2026

Môi trường: Windows, Java 17.0.12, Spring Boot 3.5.16. Build Maven reactor thành công. Dữ liệu kiểm thử độc lập với các profile demo.

## JUnit

| Bộ kiểm thử | Số test | Kết quả |
|---|---:|---|
| ProtocolTest: hash, ranh giới chunk, frame lỗi | 2 | PASS |
| TrackerIntegrationTest: phê duyệt, phân quyền, vé tải, thu hồi quyền, bảo vệ API | 2 | PASS |
| CheckpointTest: khôi phục và phát hiện chunk hỏng | 1 | PASS |
| TcpTransportTest: 4 client đồng thời, hash sai, quá tải / đóng socket | 3 | PASS |
| WebSocketIntegrationTest: Origin / đăng nhập, push thay đổi, đóng sau logout | 2 | PASS |

Tổng: **10/10 PASS**.

## E2E: tracker và ba peer Java thật trên cùng máy

- File gốc: 33.554.432 byte (32 MiB), 128 phần × 256 KiB.
- SHA-256: `e09320c5b00b34bb704802136c599a95b3996332ba84d7c7f21112b6231b6bd0`.
- Nhận dữ liệu từ hai nguồn A và B: PASS.
- Peer đẩy tiến độ nhiều nguồn và số liệu worker / socket qua WebSocket có xác thực: PASS.
- Yêu cầu TCP có vé giả bị từ chối: PASS.
- Duyệt tài khoản, thiết bị và file; tự cập nhật tài khoản chờ duyệt: PASS.
- Tạm dừng ở 4 MiB, tắt cưỡng bức peer nhận, cố ý làm hỏng một phần: PASS.
- Sau restart: giữ 3,75 MiB đã xác minh, loại phần hỏng 256 KiB: PASS.
- Tiếp tục tải, tắt nguồn A giữa chừng, hoàn tất bằng B: PASS.
- So sánh từng byte file tải xong với file gốc và SHA-256: PASS.
- Máy nhận trở thành nguồn và server nhận báo cáo hoàn tất: PASS.
- Restart tracker và kiểm tra tài khoản, peer, file được giữ trong H2: PASS.

Kết quả máy đọc được nằm trong [e2e-result.json](./e2e-result.json). Tổng lượng nhận theo nguồn có thể lớn hơn dung lượng file vì kịch bản cố ý làm hỏng một phần đã nhận, buộc tải lại.

## Giao diện

Đã dùng trình duyệt để đăng nhập quản trị, mở danh sách tài khoản và dashboard; đăng nhập peer, mở danh mục và phiên tải hoàn tất. Trang tải hiển thị 128/128 phần, hai nguồn đã gửi và SHA-256 hợp lệ. Đã xác minh nhãn WebSocket trực tiếp, mở trang Hoạt động peer và lưu ảnh [peer-network.png](./peer-network.png). Ảnh chụp sau khi tải hoàn tất nên worker / kết nối đang mở bằng 0; bộ đếm yêu cầu TCP phản ánh dữ liệu thực tế.

## Cần xác minh bổ sung trước khi nộp

- Demo trên 2–3 máy LAN thật, cấu hình IP và firewall theo README.
- Bổ sung ảnh demo đang tải trên LAN và thông tin thành viên thật.
- Chưa kiểm thử kết nối qua Internet / NAT; phạm vi hiện tại là localhost và LAN.
