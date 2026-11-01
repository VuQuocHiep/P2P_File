# Hướng dẫn đọc mã nguồn PeerMesh

## Đọc theo một chức năng

Ví dụ đăng nhập tracker:

1. `source/server/src/main/java/vn/edu/p2p/server/controller/TrackerAuthController.java`: nhận HTTP request, quản lý HTTP session.
2. `dto/request/Login.java`: dữ liệu nhận từ giao diện.
3. `service/TrackerAuthService.java`: kiểm tra tài khoản, mật khẩu và tạo phiên đăng nhập.
4. `repository/AccountRepository.java` → `entity/AccountEntity.java`: truy vấn và lưu tài khoản bằng JPA.
5. `mapper/AccountMapper.java` → `dto/response/LoginResponse.java`: chuyển dữ liệu thành JSON trả về.

Ví dụ tải file tại peer: `PeerDownloadController` → `PeerDownloadService` → `PeerSourceService` → `TcpChunkClient`. Tiến độ được truy vấn/cập nhật qua `DownloadJobRepository`, file qua `LibraryRepository`; `PeerStorage` lưu chung checkpoint xuống ổ đĩa.

## Ba module

| Module | Trách nhiệm |
| --- | --- |
| `source/server/` | Tracker: tài khoản, phê duyệt peer/file, cấp vé tải, dashboard quản trị. |
| `source/client/` | Peer: thư viện cục bộ, tải/gửi chunk bằng TCP, checkpoint, phòng thí nghiệm. |
| `source/common/` | Manifest, giao thức TCP, hỗ trợ WebSocket/thread và tài nguyên giao diện dùng chung. |

## Package Java

| Package | Nơi đặt mã |
| --- | --- |
| `controller/` | HTTP endpoint và HTTP session. |
| `dto/request/`, `dto/response/` | Cấu trúc dữ liệu vào/ra API. |
| `service/` | Nghiệp vụ, điều phối và trạng thái runtime. |
| `repository/` | Truy vấn/lưu dữ liệu; server dùng interface JPA, client chia thành `PeerProfileRepository`, `LibraryRepository`, `DownloadJobRepository`, `PeerEventRepository`. |
| `entity/`, `entity/converter/` | Entity JPA và chuyển đổi các cột JSON của file. |
| `mapper/` | Chuyển entity/model sang response DTO. |
| `model/` | Dữ liệu runtime, phiên đăng nhập, vé tải hoặc trạng thái cục bộ. |
| `migration/`, `migration/legacy/` (server) | Nhập snapshot registry cũ; model cũ chỉ phục vụ bước chuyển dữ liệu này. |
| `network/` (client) | TCP server/client và HTTP gateway tới tracker. |
| `websocket/` | Xác thực kết nối và đẩy snapshot cho trình duyệt. |
| `config/`, `exception/` | Cấu hình HTTP/WebSocket và xử lý lỗi API. |
| `storage/` (client) | Đọc/ghi profile, khóa thư mục dữ liệu và khôi phục checkpoint; được dùng chung bởi các repository. |
| `util/` (client) | Tiện ích ghi/chuyển file an toàn. |

`TrackerContext` giữ khóa chung và transaction JPA: khóa chỉ được nhả sau commit/rollback. Session và vé tải vẫn ở RAM. `PeerContext` giữ trạng thái dùng chung; `PeerRuntime` quản lý TCP, heartbeat, telemetry và shutdown. Các thao tác profile/checkpoint giữ cùng khóa `PeerStorage.monitor()`. Xem [repository phía client](source/client/src/main/java/vn/edu/p2p/client/repository/README.md) để chọn lớp theo dữ liệu cần xử lý.

## Giao diện: tìm đúng file cần đọc

HTML của mỗi ứng dụng vẫn ở `src/main/resources/static/index.html`. Bố cục HTML, CSS và mẫu nội dung hiển thị được giữ nguyên khi tổ chức lại mã.

```text
common/src/main/resources/META-INF/resources/assets/
├── css/theme.css          # Toàn bộ giao diện dùng chung
└── js/
    ├── ui.js              # Định dạng, API HTTP và thành phần HTML dùng chung
    └── realtime.js        # WebSocket, reconnect và refresh

server/src/main/resources/static/js/admin/
├── state.js               # Trạng thái, tiêu đề và mô tả các tab
├── auth.js                # Chuyển màn hình đăng nhập/ứng dụng
├── api.js                 # Lấy dashboard và xử lý lỗi kết nối
├── realtime.js            # Nhận snapshot quản trị
├── views/
│   ├── tables.js          # Bảng tài khoản, peer, file, tiến độ và nhật ký
│   ├── overview.js        # Màn hình tổng quan
│   └── page.js            # Chọn tab và render khung nội dung
└── app.js                 # Gắn sự kiện và khởi động trang

client/src/main/resources/static/
├── css/lab.css
└── js/
    ├── lab/lab.js         # Biểu đồ, topology, benchmark và mô phỏng lỗi
    └── peer/
        ├── state.js       # Trạng thái, tab và điều kiện sẵn sàng
        ├── auth.js        # Đăng nhập/đăng ký và chuyển màn hình
        ├── api.js         # Catalog và trạng thái peer
        ├── realtime.js    # Nhận snapshot peer
        ├── views/         # stats, explore, downloads, library, activity, page
        ├── dialogs/       # Lịch sử nguồn từng chunk
        └── app.js         # Sự kiện, lưu thông tin form và khởi động trang
```

Các script vẫn dùng cơ chế classic script như trước. `index.html` ghi rõ thứ tự nạp bằng `defer`: tiện ích dùng chung → trạng thái và hàm hiển thị → API/realtime → `app.js`. `app.js` chạy cuối để mọi hàm đã sẵn sàng trước khi gắn sự kiện và gọi API khởi tạo.

## Kiểm tra sau khi sửa

- `source/build.ps1`: build các module và chạy test Java.
- Test server: API, JSON, quyền truy cập, WebSocket, migration, restart và request đồng thời.
- Test client: checkpoint, TCP, đăng nhập, tải file và các tài nguyên JavaScript/CSS được HTML tham chiếu.
- `.editorconfig`: thống nhất UTF-8, xuống dòng LF, Java thụt 4 khoảng trắng và JavaScript thụt 2 khoảng trắng.
