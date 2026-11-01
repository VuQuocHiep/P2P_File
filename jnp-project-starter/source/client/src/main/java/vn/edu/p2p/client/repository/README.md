# Repository của peer

| Repository | Dữ liệu | Các hàm chính |
| --- | --- | --- |
| `PeerProfileRepository` | Tài khoản sở hữu profile, tên thiết bị, host, peer ID | `getOwner`, `getName`, `getHost`, `getPeerId`, `update`, `updatePeerId` |
| `LibraryRepository` | File và các chunk đang có tại peer | `findByFileId`, `findAll`, `findFileIds`, `save` |
| `DownloadJobRepository` | Tiến độ và lịch sử phiên tải | `findById`, `findByFileId`, `findAll`, `findIds`, `save` |
| `PeerEventRepository` | 100 sự kiện cục bộ gần nhất | `findAll`, `append` |

Các repository dùng cùng một `storage/PeerStorage`. Chúng truy vấn và cập nhật dữ liệu trong bộ nhớ; các hàm tìm một bản ghi trả `null` khi không tồn tại, như hành vi cũ.

Service giữ `synchronized (context.storage.monitor())` trong suốt thao tác đọc/cập nhật. Gọi `context.storage.saveCheckpoint()` tại điểm cần ghi xuống ổ đĩa. Mọi repository cùng được lưu vào `profile.json`, giữ nguyên định dạng checkpoint và cách ghi file nguyên tử.

`PeerStorage` quản lý đường dẫn file/chunk, khóa `profile.lock`, xác minh chunk khi khởi động, đọc/ghi checkpoint và đóng tài nguyên. Logic đăng nhập, tải file, chia sẻ và mô phỏng lỗi vẫn nằm trong `service/`.
