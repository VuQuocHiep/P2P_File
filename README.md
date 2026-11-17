# PeerMesh — Chia sẻ file P2P có quản trị

Đọc [ARCHITECTURE.md](ARCHITECTURE.md) để xem luồng controller → service → repository/mapper và vị trí từng phần giao diện.

Bài tập lớn Lập trình mạng. Java 17 + Spring Boot, giao diện web tiếng Việt, truyền file trực tiếp giữa các peer qua TCP socket.

## Thông tin nhóm

**Chủ đề:** Chia sẻ file ngang hàng P2P có tracker và cơ chế phê duyệt.

Tên nhóm, thành viên, MSSV, email, đóng góp và hạn nộp: **chưa được cung cấp — cần điền trước khi nộp**. Lịch sử Git cần thể hiện đóng góp thực tế.

## Mô tả hệ thống

![Kiến trúc PeerMesh](./statics/peermesh-diagram.svg)

- **Server / tracker:** xác thực, duyệt tài khoản / thiết bị / file, tìm nguồn, cấp vé tải, nhận báo cáo và lưu nhật ký trong H2.
- **Client / peer:** ứng dụng Java vừa tải vừa gửi các phần file qua TCP. Spring Boot cung cấp giao diện web cục bộ để người dùng điều khiển peer.
- **Common:** định dạng manifest, giao thức TCP, CSS và tiện ích web dùng chung.

Tracker lưu metadata. Nội dung file truyền trực tiếp giữa các peer. Trình duyệt đưa file vào peer cục bộ và hiển thị tiến độ; phần P2P do Java thực hiện.

## Chức năng

| Server / quản trị viên | Client / peer |
|---|---|
| Đăng nhập, phân quyền ADMIN | Đăng ký, đăng nhập, xem trạng thái duyệt |
| Duyệt / khóa / mở tài khoản | Đăng ký thiết bị bằng IP và cổng TCP |
| Duyệt / khóa / mở peer | Thêm file vào thư viện và chia sẻ |
| Duyệt / ẩn / mở file | Tìm file được duyệt, xem nguồn trực tuyến |
| Heartbeat, phát hiện peer offline | Chunk 256 KiB, tải từ nhiều peer |
| Vé tải 60 giây theo nguồn và file | Tối đa 4 kết nối / file, 2 file tải đồng thời |
| Kiểm tra lại quyền khi nguồn xác thực vé | Ưu tiên phần hiếm có nguồn; tự đổi nguồn khi lỗi |
| Dashboard và báo cáo phiên tải | Tạm dừng, tiếp tục và khôi phục sau restart |
| Nhật ký phê duyệt và đăng nhập | SHA-256 từng phần và toàn file |
| H2 lưu bền vững registry | Chia sẻ lại phần đã xác minh / file tải xong |
| BCrypt, session / bearer hết hạn sau 12 giờ | Bật / dừng chia sẻ, lưu file về máy |

Web sử dụng WebSocket để nhận snapshot trực tiếp mỗi giây. Trình duyệt tự nối lại theo exponential backoff khi mất kết nối; HTTP dự phòng mỗi 10 giây chỉ hoạt động khi WebSocket chưa kết nối. Heartbeat peer tới tracker mỗi 5 giây; sau 20 giây không có heartbeat nguồn được coi là offline. Bộ tải có timeout TCP để phát hiện lỗi kết nối.

## Công nghệ

| Thành phần | Công nghệ |
|---|---|
| Runtime | Java 17 |
| Framework | Spring Boot 3.5.16 |
| Điều phối | HTTP / JSON |
| Cập nhật thời gian thực | Spring WebSocket / RFC 6455 |
| Dữ liệu P2P | Java TCP Socket, giao thức PM01 |
| Lưu trữ server | H2 + Spring Data JPA, bảng riêng cho account, peer, file, transfer và audit |
| Lưu trữ peer | File content-addressed, chunk và checkpoint JSON |
| Toàn vẹn / mật khẩu | SHA-256 / BCrypt |
| Giao diện | HTML, CSS, JavaScript thuần, không cần CDN / Node.js |
| Build / test | Maven, JUnit 5; Python 3 cho E2E tùy chọn |

## Cấu trúc repository

```text
jnp-project-starter/
├── README.md
├── INSTRUCTION.md                 # Giữ nguyên hướng dẫn giảng viên
├── statics/
│   ├── peermesh-diagram.svg
│   └── ...                        # Tài nguyên mẫu ban đầu
└── source/
    ├── .gitignore
    ├── pom.xml                    # Maven parent / reactor
    ├── build.ps1                  # Build Windows
    ├── run.ps1                    # Chạy server / peer Windows
    ├── common/
    │   ├── pom.xml
    │   └── src/main/, src/test/    # model, protocol, concurrent, realtime, tài nguyên web chung
    ├── server/
    │   ├── README.md
    │   ├── pom.xml
    │   └── src/main/, src/test/    # controller, service, repository, model, dto, config, websocket, exception
    └── client/
        ├── README.md
        ├── pom.xml
        └── src/main/, src/test/    # controller, service, repository, network, model, dto, config, websocket, exception
```

Toàn bộ mã nguồn ở `source/`. `target/` chứa kết quả build / test; `data/` chứa dữ liệu runtime, đều được Git bỏ qua. `common/` là module bổ sung được phép trong INSTRUCTION.md.

## Kiến thức Lập trình mạng được áp dụng

| Kiến thức | Áp dụng thực tế | Cách kiểm tra / demo |
|---|---|---|
| TCP Socket, ServerSocket | Peer nhận kết nối và truyền chunk trực tiếp | Tải file từ A và B sang C |
| Giao thức tầng ứng dụng | PM01: magic, độ dài header, JSON, status và dữ liệu nhị phân | Đọc Wire và gửi frame sai trong test |
| TCP là byte stream | readNBytes / readFully, kiểm tra độ dài và EOF | Test frame thiếu dữ liệu / payload sai hash |
| HTTP client–server | Đăng ký, xác thực, tìm nguồn, vé tải | TrackerGateway dùng Java HttpClient |
| WebSocket hai chiều | Server đẩy snapshot; trình duyệt gửi yêu cầu refresh | Nhãn WebSocket trực tiếp, không polling HTTP khi kênh ổn định |
| WebSocket handshake và bảo mật | HTTP Upgrade, session cookie, same-origin, kiểm tra quyền mỗi lần đẩy | Test anonymous / foreign Origin / logout |
| Ping / Pong | Server ping mỗi 15 giây, cắt kết nối không pong trong 45 giây | RFC 6455 test client xử lý ping / pong |
| Multithreading và thread pool | Luồng accept, 4 upload worker, 2 coordinator, 4 download worker / file | Trang Hoạt động peer hiển thị worker và kết nối thật |
| Đồng bộ đa luồng | synchronized bảo vệ state; AtomicLong / AtomicInteger cho bộ đếm; concurrent collections | 4 client cùng vào provider trong TcpTransportTest |
| Backpressure | Pool gửi 4 worker + hàng đợi tối đa 16, quá tải đóng kết nối mới | Test mở nhiều socket chưa gửi header |
| Heartbeat / failure detection | HTTP heartbeat 5 giây, TTL online 20 giây | Tắt peer, xem offline trên dashboard |
| Timeout, retry và failover | Connect 2 giây, read 8 giây, tìm nguồn lại sau 2 giây | Tắt A trong lúc C đang tải |
| Truyền file phân mảnh | Manifest, chọn phần còn thiếu, ưu tiên phần hiếm có nguồn | Lưới chunk và số byte từng nguồn |
| Toàn vẹn và phục hồi | SHA-256, checkpoint, ghi file atomic, kiểm tra lại lúc khởi động | E2E làm hỏng chunk và restart peer |
| Quản lý vòng đời kết nối | Đóng socket khi shutdown, cancel worker, reconnect WebSocket | Restart peer / tracker mà không mất dữ liệu đã lưu |

### Tổ chức package

Trong `source/server/src/main/java/vn/edu/p2p/server/`:

- `controller/`: HTTP endpoint, chuyển request tới service.
- `service/`: nghiệp vụ điều phối, xác thực và phê duyệt.
- `migration/`: chuyển dữ liệu registry cũ; tách khỏi các service nghiệp vụ.
- `repository/` (server): `AccountRepository`, `PeerRepository`, `FileRepository`, `TransferRepository`, `AuditRepository` kế thừa `JpaRepository`; Spring Data JPA cung cấp phần triển khai. Client có `PeerProfileRepository`, `LibraryRepository`, `DownloadJobRepository`, `PeerEventRepository`; chúng dùng chung `storage/PeerStorage` để lưu profile và checkpoint trên ổ đĩa.
- `model/`: dữ liệu runtime; `migration/legacy/` chứa các model JSON cũ.
- `entity/` (server): entity cho các bảng nghiệp vụ riêng; `RegistryEntity` giữ bản snapshot cũ để migration, `MigrationEntity` đánh dấu lần chuyển dữ liệu.

- `dto/request/`: DTO nhận dữ liệu request HTTP.
- `dto/response/`: DTO trả dữ liệu HTTP, giữ nguyên tên trường và cấu trúc JSON của API.
- `mapper/`: chuyển model hoặc JSON từ tracker sang response DTO; service giữ kiểm tra quyền, tính toán nghiệp vụ và khóa đồng bộ.
- `config/`: cấu hình WebSocket, header bảo vệ HTTP.
- `websocket/`: xác thực và đẩy dashboard cho quản trị viên.
- `exception/`: chuyển lỗi thành phản hồi HTTP.

Trong `source/client/src/main/java/vn/edu/p2p/client/` có các package tương tự; `network/` chứa `TcpChunkServer`, `TcpChunkClient`, `TrackerGateway`, còn `websocket/` đẩy trạng thái peer lên web.

Mỗi model và DTO nằm trong một file Java riêng, ví dụ `entity/AccountEntity.java`, `entity/FileEntity.java`, `dto/request/Login.java`, `dto/request/Advertisement.java` ở server; `model/Entry.java`, `model/Job.java`, `dto/request/Download.java` ở client.

Controller được chia theo chức năng và gọi trực tiếp service tương ứng do Spring quản lý bằng `@Service`, ví dụ `TrackerPeerController → TrackerPeerService`, `PeerLibraryController → PeerLibraryService`. Service nhận request DTO và trả response DTO; controller quản lý HTTP/session. `TrackerContext` giữ session/ticket trong RAM và một khóa dùng chung cho tracker service; khóa chỉ được nhả sau khi transaction JPA commit hoặc rollback. `PeerContext` giữ trạng thái peer; service truy cập dữ liệu qua interface repository, còn `PeerRuntime` quản lý TCP, lịch heartbeat, telemetry và shutdown; các giới hạn worker và cơ chế đồng bộ được giữ nguyên.

Trong `common/`: `model/Manifest`, `protocol/Wire` và `protocol/ChunkRequest`, `concurrent/NamedThreads`, `realtime/SnapshotSocket` và `realtime/SnapshotConnection`. Hai lớp Application ở package gốc để Spring quét các package con.

### Các luồng chính

| Tên luồng | Nhiệm vụ | Giới hạn |
|---|---|---|
| tcp-accept-* | accept socket, đưa vào hàng đợi gửi | 1 / peer |
| tcp-upload-* | Kiểm tra quyền và gửi chunk | 4 worker, queue 16 |
| download-coordinator-* | Phân lịch tải, ghép file và phục hồi | 2 phiên / peer |
| chunk-download-<job>-* | Mở socket, nhận và xác minh chunk | 4 / file, lô tối đa 16 |
| peer-heartbeat-* | Kiểm tra tracker, quảng bá phần và báo cáo | 1 / peer |
| peer-websocket-* / tracker-websocket-* | Đẩy snapshot, xử lý refresh, ping/pong | 2 / ứng dụng, tối đa 32 phiên web |
| Các luồng HTTP của Tomcat / Java HttpClient | Xử lý API và WebSocket handshake | Do framework quản lý |

Khi chạy bằng JDK có thể dùng `jcmd <PID> Thread.print` để xem tên các pool. Dữ liệu trạng thái dùng chung chỉ được đọc / sửa trong vùng synchronized tương ứng; không giữ khóa repository trong khi chờ kết nối mạng.

## Build

Cần **JDK 17+**, **Maven 3.6.3+**. Python 3 chỉ cần để chạy E2E tùy chọn. Lần build đầu cần mạng để tải dependency. Chạy ở thư mục chứa README này:

```powershell
java -version
mvn -version
mvn -f source/pom.xml package
```

Windows có thể dùng script, tự tìm Maven trong PATH hoặc Maven wrapper đã tải ở `~/.m2/wrapper/dists`:

```powershell
powershell -ExecutionPolicy Bypass -File source/build.ps1
```

ExecutionPolicy chỉ áp dụng cho tiến trình này. Có thể thêm `-Offline` nếu đã có dependency và `-Repository "duong-dan-cache"` để chọn cache Maven. Build cả reactor vì server và client phụ thuộc common.

Kết quả: `source/server/target/server-1.0.0.jar` và `source/client/target/client-1.0.0.jar`.

## Chạy demo Windows trên một máy

Mở **4 terminal**, đều ở thư mục repository.

### Terminal 1 — tracker

Lần đầu cần ADMIN_PASSWORD riêng dài 12–72 ký tự (nên dùng ASCII). Thay mật khẩu ví dụ bên dưới:

```powershell
$env:ADMIN_PASSWORD = 'ThayBangMatKhauRieng!2026'
powershell -ExecutionPolicy Bypass -File source/run.ps1 -Role server
```

Mở [quản trị](http://127.0.0.1:8080), đăng nhập `admin` và mật khẩu vừa đặt. ADMIN chỉ được bootstrap khi registry chưa có quản trị viên. Đổi biến môi trường sau đó không thay mật khẩu đã lưu.

### Terminal 2, 3, 4 — peer A, B, C

```powershell
# Terminal 2
powershell -ExecutionPolicy Bypass -File source/run.ps1 -Role peer -WebPort 8081 -TcpPort 9001 -UploadDelayMs 200

# Terminal 3
powershell -ExecutionPolicy Bypass -File source/run.ps1 -Role peer -WebPort 8082 -TcpPort 9002 -UploadDelayMs 200

# Terminal 4
powershell -ExecutionPolicy Bypass -File source/run.ps1 -Role peer -WebPort 8083 -TcpPort 9003
```

Mở [peer A](http://127.0.0.1:8081), [peer B](http://127.0.0.1:8082), [peer C](http://127.0.0.1:8083). Script tạo profile riêng theo cổng web. Mỗi profile gắn với một tài khoản. Một tài khoản có thể đăng nhập trên nhiều profile / thiết bị.

UploadDelayMs tạo độ trễ demo mỗi chunk để quan sát quá trình tải. Khi đo tốc độ thực tế, bỏ tham số hoặc đặt 0.

### Kịch bản demo

1. Đăng ký tài khoản tại peer; admin duyệt ở **Tài khoản**.
2. Đăng nhập peer, nhập tên thiết bị và IP `127.0.0.1` khi demo cùng máy.
3. Admin duyệt tại **Thiết bị peer**. Trang người dùng tự cập nhật.
4. A chọn **Chia sẻ file**, dùng file 32–100 MiB để dễ quan sát.
5. Admin duyệt tại **Danh mục file**.
6. B chia sẻ file **cùng nội dung**, tạo nguồn thứ hai với cùng SHA-256.
7. C tìm file → **Xem nguồn** → **Tải nhiều nguồn**.
8. Xem lưới chunk và lượng dữ liệu nhận từ từng nguồn.
9. Tắt A giữa chừng: C thử nguồn còn lại để hoàn tất.
10. Có thể tạm dừng C, đóng tiến trình, mở lại **cùng profile**, đăng nhập và bấm **Tiếp tục tải**.
11. Khi xong, kiểm tra SHA-256 và **Lưu file về máy**. C cũng trở thành nguồn.
12. Trên server xem trạng thái peer, phiên tải và nhật ký quản trị.

Nếu tải quá nhanh, dùng file lớn hơn hoặc tăng độ trễ demo. Giao diện dùng tiến độ thực tế.

## Phòng thí nghiệm P2P

Sau khi đăng nhập, mở **Phòng thí nghiệm** trên thanh bên của peer.

![Phòng thí nghiệm P2P: sơ đồ và tốc độ thực](./statics/lab-preview.png)

Ảnh bảng so sánh và kết quả kiểm thử: [xác minh phòng thí nghiệm](./statics/lab-validation.md).

- **Sơ đồ trực tiếp:** hiện nguồn có yêu cầu TCP đang mở đến peer này, tên / địa chỉ nguồn, số kết nối, chunk đang yêu cầu, tốc độ và tổng chunk đã nhận trong lần chạy. Nguồn vừa gửi được giữ 30 giây bằng đường xám; đường chuyển động biểu thị yêu cầu đang mở. Các kết nối gửi ra được liệt kê bằng địa chỉ TCP đích. Sơ đồ là góc nhìn cục bộ, không suy đoán toàn bộ mạng. Đường tracker thể hiện điều phối metadata / vé, không phải đường truyền nội dung file.
- **Biểu đồ tốc độ:** nhận đã xác minh và gửi, tính từ chênh lệch byte / thời gian thực, tối đa 120 mẫu trong RAM. Bộ lấy mẫu chạy trên luồng peer-metrics riêng mỗi giây, dùng đồng hồ đơn điệu để tính thời gian và không chờ heartbeat. Bao gồm lưu lượng phép đo. Tốc độ trên thẻ phiên tải vẫn là trung bình phiên đang chạy.
- **So sánh một nguồn / nhiều nguồn:** chọn file có ít nhất 2 chunk và 2 nguồn khác peer nhận có đủ tối đa 64 chunk đầu. Hai lượt đọc cùng tập chunk (tối đa 16 MiB / lượt), đều dùng 4 worker, qua TCP thật và xác minh SHA-256. Lượt đầu dùng một nguồn; lượt sau chia chunk cho các nguồn. Vé được lấy mới trước mỗi lượt. Không ghi dữ liệu phép đo vào thư viện hoặc checkpoint; có thể đo lại cả file đã tải. Hiện thời gian, tốc độ, byte từng nguồn và tỷ số tốc độ. Nếu một lượt lỗi thì ghi lỗi, không kết luận tỷ số. Chỉ chạy một phép đo tại peer này và cần dừng các phiên tải để giảm nhiễu.
- **Mô phỏng lỗi nguồn:** bật tại peer A đang gửi; quan sát tại peer C đang nhận. Có thêm độ trễ 1500 ms / yêu cầu, đóng kết nối trước payload, hoặc lật một byte trên bản gửi để gây sai SHA-256. Mọi lỗi tự hết sau 30 giây trên giao diện; **Bình thường** khôi phục ngay cho các yêu cầu tiếp theo. File gốc không thay đổi; đăng xuất / khởi động lại cũng đưa mô phỏng về bình thường. Bộ đếm hiển thị số yêu cầu chịu mô phỏng.

### Kịch bản trình bày

1. Cho A và B chia sẻ cùng file 32 MiB trở lên; duyệt tài khoản, peer và file như phần demo.
2. Tại C mở Phòng thí nghiệm, chọn file, bấm **Đo hai lượt**; xem sơ đồ khi đo và so sánh kết quả. Tải lại mạng LAN để thu số liệu cho báo cáo.
3. Tại C bắt đầu tải file trong Khám phá, trở lại Phòng thí nghiệm để xem sơ đồ và đồ thị.
4. Tại A bật **Gửi chunk sai SHA-256** hoặc **Ngắt kết nối**; tại C xem nhật ký lỗi và chuyển sang B. Bấm **Bình thường** tại A hoặc đợi hết hạn để phục hồi.
5. Khi tải xong, xem nhãn SHA-256 toàn file hợp lệ và lưu file về máy.

Nhiều nguồn không đảm bảo nhanh hơn, nhất là các peer chạy chung máy. Hai lượt chạy lần lượt (một nguồn trước), có ảnh hưởng cache, tải nền, HTTP xác thực vé, ổ đĩa và mạng. Chế độ chậm mô phỏng độ trễ từng yêu cầu, không giới hạn tổng băng thông mỗi nguồn. Báo cáo phải ghi điều kiện đo và kết quả thật.

API mới (cần session đăng nhập peer và tài khoản / peer được duyệt):

- `POST /api/lab/benchmark` với `{ "fileId": "<sha256>" }`; kết quả / trạng thái ở `GET /api/status` → `lab.benchmark`.
- `POST /api/lab/fault` với `{ "mode": "NORMAL|SLOW|DISCONNECT|CORRUPT", "delayMs": 1500, "seconds": 30 }`. API giới hạn thời hạn 1–120 giây, độ trễ 0–5000 ms. Mô phỏng áp dụng cho mọi yêu cầu chunk được nguồn xác thực trong thời hạn.
- `lab.links`, `lab.recentLinks`, `lab.sourceStats`, `lab.samples`, `lab.fault` được đưa vào snapshot WebSocket hiện có. Dữ liệu lab chỉ giữ trong RAM, không chứa vé tải.

## Quét mã độc bằng VirusTotal (tùy chọn)

Admin có thể **duyệt file mà không quét**, hoặc bấm **Quét file** trong Danh mục file. Quét không tự chạy khi chia sẻ hay khi duyệt. Không cần admin chọn lại bản file: server yêu cầu peer đang giữ nội dung tự gửi lên khi cần.

### Cấu hình và quyền gửi file

Đặt key trên máy tracker trước khi khởi động, trong cùng cửa sổ terminal. Không đưa key vào Git, JavaScript, ảnh báo cáo hoặc chat.

```powershell
$env:VIRUSTOTAL_API_KEY = 'API_KEY_CUA_BAN'
powershell -ExecutionPolicy Bypass -File source/run.ps1 -Role server
```

Nếu dùng CMD:

```cmd
set "VIRUSTOTAL_API_KEY=API_KEY_CUA_BAN"
powershell -ExecutionPolicy Bypass -File source/run.ps1 -Role server
```

Key lấy từ menu tài khoản [VirusTotal](https://www.virustotal.com/). Cần chạy cả server và peer bằng bản mới sau khi build. Thiết lập quản trị viên lần đầu vẫn theo phần khởi động server ở trên.

Phía peer, khi thêm file, tích **Cho phép gửi file lên VirusTotal khi admin yêu cầu** nếu đồng ý. Với file đã có, vào **Thư viện chia sẻ → Cho phép VirusTotal**; có thể tắt lại ở cùng chỗ. Mặc định không cho phép, bao gồm các file từ phiên bản cũ và file tải từ peer khác. Quyền được lưu theo file trên từng peer và đồng bộ cùng manifest. Chỉ một nguồn có đủ file, đang chia sẻ và đã cho phép mới được giao yêu cầu gửi. Nội dung tải lên VirusTotal có thể được chia sẻ với đối tác/khách hàng của dịch vụ; không bật cho file riêng tư hoặc file không có quyền chia sẻ.

### Luồng xử lý tự động

1. Admin bấm **Quét file**. Server lưu yêu cầu trong H2 rồi trả về ngay, không chờ peer hay VirusTotal trong HTTP request này.
2. Worker tra báo cáo theo SHA-256. Nếu đã có kết quả thì hiển thị luôn, không cần gửi nội dung file.
3. Nếu chưa có báo cáo, trạng thái chuyển thành **Đang chờ nguồn cho phép quét**. Nguồn offline, chưa đủ file hoặc chưa đồng ý thì yêu cầu tiếp tục chờ; không hiện trình chọn file ở máy admin.
4. Peer nhận việc qua kết nối HTTP chủ động khi heartbeat, dùng worker riêng để không chặn heartbeat hoặc truyền P2P. Server chỉ cấp một lease có thời hạn cho một nguồn đủ điều kiện. Peer kiểm tra lại quyền cục bộ trước khi gửi bản file do ứng dụng quản lý.
5. Server kiểm tra tài khoản/peer, lease, quyền đã đồng bộ, kích thước và SHA-256 nội dung. Chỉ file khớp manifest và tối đa **32 MiB** mới được gửi sang VirusTotal. File không được thực thi hay lưu vào thư viện tracker.
6. Server tự theo dõi phiên quét và tự lấy báo cáo theo hash. Trang quản trị cập nhật qua WebSocket/polling, không cần admin bấm lấy kết quả từng bước. Worker dùng khoảng cách 16 giây giữa các lượt; chốt quota dùng chung chặn gọi cách nhau dưới 15 giây.

Mất kết nối khi nguồn đang gửi: lease hết hạn sau khoảng 120 giây, peer online khác có quyền có thể nhận lại. Peer cũng trả việc khi không đọc/gửi được file; retry có khoảng chờ 30–60 giây. Lỗi key, mạng hoặc quota được hiển thị, tự thử lại tối đa 5 lần rồi dừng để admin sửa cấu hình và bấm quét lại. Yêu cầu chưa hoàn tất, lease, mã phiên VirusTotal và kết quả đều lưu trong H2. Sau khi server khởi động lại, peer cần đăng nhập lại như cơ chế session hiện có; công việc đang chờ sẽ tiếp tục. Admin có thể **Hủy quét**; nội dung đã gửi ra VirusTotal trước đó không thể thu hồi.

| Kết quả | Ý nghĩa / thao tác |
|---|---|
| Chưa kiểm tra / chưa có dữ liệu | Không kết luận an toàn; vẫn duyệt thủ công được |
| Đang chờ nguồn / peer đang gửi / VirusTotal đang quét | Theo dõi tự động; vẫn có thể duyệt thủ công khi chưa có cảnh báo |
| Xanh — Chưa phát hiện mã độc | Báo cáo chưa có malicious/suspicious; không đảm bảo an toàn tuyệt đối |
| Đỏ — Phát hiện mã độc | Tự chuyển file sang BLOCKED và không cho duyệt |
| Cam — Có dấu hiệu đáng ngờ | Tự chặn duyệt để kiểm tra thêm |
| Quét gặp lỗi | Hiện lý do, có thể hủy hoặc thử lại; lỗi không được đổi thành kết quả sạch |

Tên engine, tên phát hiện, số lượng engine, thời điểm tra và thời điểm báo cáo VT được lưu để trình bày. Báo cáo cũ có cảnh báo được giữ khi lần kiểm tra lại chưa kết luận được; chỉ báo cáo mới không có phát hiện mới có thể gỡ điều kiện chặn duyệt. File vẫn cần admin mở lại sau khi đã bị BLOCKED. Link **Mở báo cáo VirusTotal** cho phép đối chiếu trực tiếp.

Public API có hạn mức 4 request/phút và 500 request/ngày, không dùng cho sản phẩm/dịch vụ thương mại: [điều khoản API](https://docs.virustotal.com/reference/public-vs-premium-api). Chỉ tự gửi nội dung tối đa 32 MiB trong phiên bản này; file lớn hơn vẫn tra hash được, nếu chưa có dữ liệu thì thông báo giới hạn và admin có thể duyệt thủ công.

### Demo cho giảng viên

1. Thêm file thường trên peer và bật quyền VirusTotal. Duyệt một file khác chưa quét để cho thấy quét là tùy chọn.
2. Admin bấm **Quét file**. Khi cần nội dung, quan sát trạng thái chờ nguồn; peer tự gửi mà admin không chọn lại file.
3. Có thể thử nguồn offline hoặc tắt quyền gửi trước; bật peer/cho phép lại để yêu cầu tiếp tục. Chỉ có nguồn online nhưng chưa cho phép thì vẫn chờ.
4. Khi hoàn tất, xem nhãn, số công cụ, danh sách engine và mở báo cáo. `UNKNOWN` không phải file sạch. Kết quả API thật phụ thuộc file và báo cáo tại VirusTotal.
5. Dùng mẫu vô hại [EICAR chính thức](https://www.eicar.org/download-anti-malware-testfile/) nếu hệ thống cho phép giữ mẫu để kiểm tra cảnh báo. Defender có thể cách ly mẫu; không cần tắt bảo vệ. Không lấy malware thật để demo.

### API và kiểm thử

- ADMIN: `POST /api/admin/files/{sha256}/scan-request` tạo yêu cầu; `DELETE` cùng URL để hủy.
- Peer đã đăng nhập và được duyệt: `POST /api/peers/{peerId}/scan-jobs/claim` nhận tối đa một việc. Lease chỉ trả cho peer sở hữu, không đưa vào dashboard.
- `POST /api/peers/{peerId}/scan-jobs/{sha256}/content`: multipart `file`, header `X-Scan-Lease`; server xác minh trước khi gửi ra ngoài.
- `POST /api/peers/{peerId}/scan-jobs/{sha256}/release` trả việc, cùng header lease.
- Peer cục bộ: `PATCH /api/library/{sha256}/scan-consent`, JSON `{"enabled":true|false}`; multipart thêm file hỗ trợ `scanConsent=true`.
- Tất cả mutation giữ xác thực hiện có và header `X-PeerMesh: 1`. API key chỉ tồn tại trên server.

Kiểm thử dùng phản hồi VirusTotal giả lập, không gửi tài liệu thật ra dịch vụ. Sau khi package, có thể chạy `python source/server/src/test/resources/scan-workflow.py` để thử hai tiến trình thật, chờ quyền gửi, khởi động lại tracker, tự upload và tự lấy báo cáo với API giả lập cục bộ. `ScanJobIntegrationTest` kiểm tra quyền, hàng chờ, lease, sai hash, hủy, tự lấy kết quả và chặn file; `PeerMalwareServiceTest` kiểm tra peer không tiết lộ file khi chưa cho phép. Tài liệu [tra hash](https://docs.virustotal.com/reference/file-info), [upload](https://docs.virustotal.com/reference/files-scan), [chia sẻ dữ liệu](https://docs.virustotal.com/docs/how-it-works).

## Chạy trên nhiều máy LAN

1. Chạy tracker trên máy có IP LAN, ví dụ `192.168.1.10`.
2. Trên các máy khác:

```powershell
powershell -ExecutionPolicy Bypass -File source/run.ps1 -Role peer -WebPort 8081 -TcpPort 9001 -TrackerUrl http://192.168.1.10:8080
```

3. Mở web peer **trên chính máy chạy peer**, nhập IPv4 LAN của máy đó khi đăng nhập. Xem IP bằng `ipconfig`.
4. Cho phép TCP 8080 vào máy tracker và cổng TCP đã chọn trên các máy peer trong firewall của mạng demo. Chương trình không tự thay đổi firewall.
5. Admin duyệt rồi thực hiện kịch bản trên.

Web peer mặc định bind 127.0.0.1, TCP chia sẻ lắng nghe trên các interface. Mỗi máy có thể dùng cùng TCP port vì IP khác nhau. Đổi IP / cổng đã đăng ký đưa thiết bị về chờ duyệt.

## Chạy trực tiếp JAR / Linux

```bash
ADMIN_PASSWORD='ThayBangMatKhauRieng!2026' java -jar source/server/target/server-1.0.0.jar

java -jar source/client/target/client-1.0.0.jar \
  --server.port=8081 --peer.tcp-port=9001 \
  --peer.data=./source/data/peer-a --peer.tracker=http://127.0.0.1:8080
```

Mỗi instance peer phải có thư mục dữ liệu riêng; khóa profile ngăn hai tiến trình cùng ghi một profile.

### Cấu hình

| Biến môi trường / tham số | Mặc định | Ý nghĩa |
|---|---|---|
| TRACKER_PORT / server --server.port | 8080 | HTTP tracker |
| ADMIN_USERNAME | admin | ADMIN bootstrap |
| ADMIN_PASSWORD | Không có | Bắt buộc khởi tạo registry lần đầu |
| TRACKER_DATA | ./data/tracker | H2 khi chạy JAR trực tiếp |
| DB_PASSWORD | Rỗng | Mật khẩu H2, giữ ổn định giữa các lần chạy |
| TRACKER_URL / --peer.tracker | http://127.0.0.1:8080 | Địa chỉ tracker |
| PEER_WEB_PORT / --server.port | 8081 | Web cục bộ peer |
| PEER_TCP_PORT / --peer.tcp-port | 9001 | TCP peer |
| PEER_DATA / --peer.data | ./data/peer-8081 | Profile, phải riêng từng instance |
| PEER_UPLOAD_DELAY_MS / --peer.upload-delay-ms | 0 | Độ trễ demo, 0–5000 ms / chunk |

Script run.ps1 mặc định dùng `source/data/server-PORT` và `source/data/peer-PORT`. Có thể chỉ định `-DataDir`. Khi chạy trực tiếp JAR, cấu hình mặc định theo bảng.

## Giao thức / API

Mutation HTTP cần header `X-PeerMesh: 1`. Web dùng session cookie HttpOnly, SameSite Strict; peer Java dùng `Authorization: Bearer <token>` với tracker. Token đăng nhập không lưu xuống profile. Không bật CORS; không cho phép iframe. Khởi động lại tracker làm mất session / vé trong bộ nhớ nên cần đăng nhập lại, metadata và tiến độ vẫn được giữ.

### API tracker

| Endpoint | Method | Input / chức năng |
|---|---|---|
| /api/health | GET | Tình trạng server |
| /api/auth/register | POST | {username,password} → PENDING |
| /api/auth/login | POST | {username,password} → token và user |
| /api/auth/me | GET | Tài khoản hiện tại |
| /api/auth/logout | POST | Hủy token / session |
| /api/peers | POST | {id,name,host,port} |
| /api/peers/heartbeat | POST | {peerId} |
| /api/files | POST | {peerId,manifest,chunks} |
| /api/files?q=... | GET | Tìm file đã duyệt |
| /api/files/{id}/sources | GET | Nguồn online và các chunk đang giữ |
| /api/files/{id}/holders/{peerId} | DELETE | Dừng quảng bá nguồn của mình |
| /api/tickets | POST | {fileId,sourceId} → vé 60 giây |
| /api/tickets/authorize | POST | Nguồn gửi {ticket,peerId,fileId} |
| /api/transfers | POST | Báo cáo id, peerId, fileId, status, bytes |
| /api/admin/dashboard | GET | ADMIN: registry, báo cáo và audit |
| /api/admin/{kind}/{id} | PATCH | ADMIN: {status}; kind = accounts / peers / files |

Trạng thái: PENDING, APPROVED, BLOCKED. ADMIN không tự khóa qua API này. File chỉ xuất hiện trong tìm kiếm khi được duyệt. Nguồn kiểm tra lại quyền mỗi lần nhận yêu cầu chunk; các phần đã gửi trước khi khóa vẫn ở máy nhận.

### API peer cục bộ

| Endpoint | Method | Chức năng |
|---|---|---|
| /api/auth/register | POST | Đăng ký tài khoản tại tracker |
| /api/auth/login | POST | {username,password,name,host} |
| /api/auth/logout | POST | Tạm dừng tải và đăng xuất |
| /api/status | GET | Trạng thái, thư viện, tải và log sau đăng nhập |
| /api/catalog?q=... | GET | Tìm file |
| /api/files/{id}/sources | GET | Xem nguồn |
| /api/library | POST multipart | Trường file, tối đa 2 GiB, không nhận file rỗng |
| /api/library/{id}/sharing | PATCH | {enabled:true/false} |
| /api/library/{id}/content | GET | Lấy file hoàn chỉnh về trình duyệt |
| /api/downloads | POST | {fileId} |
| /api/downloads/{id}/pause | POST | Tạm dừng |
| /api/downloads/{id}/resume | POST | Tiếp tục |

### WebSocket

| Endpoint | Dành cho | Xác thực |
|---|---|---|
| `/ws/admin` | Dashboard và registry quản trị | HTTP session hợp lệ, role ADMIN |
| `/ws/peer` | Tiến độ, nguồn tải, thư viện và số liệu TCP / worker | HTTP session đăng nhập tại peer |

Ví dụ server đẩy:

```json
{"type":"snapshot","sequence":12,"time":1790000000000,"data":{"...":"trạng thái đầy đủ"}}
```

Trình duyệt có thể gửi `{"type":"refresh"}` để yêu cầu snapshot mới; giới hạn refresh 250 ms / phiên. Snapshot đầy đủ sau reconnect khôi phục giao diện mà không cần lưu lịch sử sự kiện trong trình duyệt. Mỗi lần đẩy đều kiểm tra lại phiên; đăng xuất hoặc phiên hết hạn làm kênh bị đóng với code 1008. Không truyền token trong URL WebSocket. Same-origin được kiểm tra trong handshake; không cấu hình wildcard Origin.

Ping/Pong dùng control frame RFC 6455. Bộ đệm gửi WebSocket giới hạn 1 MiB và dùng ConcurrentWebSocketSessionDecorator để đồng bộ gửi từ nhiều luồng. Browser WebSocket không dùng để chuyển nội dung file; nội dung vẫn đi bằng TCP giữa các peer Java.

### TCP PM01

Một kết nối phục vụ một chunk. Số nguyên big-endian:

```text
Request:
  magic: int32 = 0x504D3031 (PM01)
  headerLength: int32, 1..4096
  header: UTF-8 JSON {ticket,fileId,chunk}

Response OK:
  status: byte = 0
  length: int32, đúng độ dài chunk trong manifest
  payload: byte[length]

Response lỗi:
  status: byte = 1
  message: Java DataOutputStream.writeUTF (modified UTF-8)
```

Manifest: id = SHA-256 toàn file, name, size, chunkSize, hashes = SHA-256 từng phần. Giới hạn 2 GiB, phần cố định 256 KiB. Cùng id phải có cùng kích thước và hash phần; tên đầu tiên trong danh mục được giữ.

Nguồn xác thực vé với tracker, kiểm tra phần dữ liệu trước khi gửi. Máy nhận kiểm tra độ dài và SHA-256 trước khi ghi .part / checkpoint. Kết nối timeout 2 giây, đọc 8 giây, tìm nguồn lại sau 2 giây khi chưa tải được. Tối đa 4 worker / file, xử lý theo lô 16 phần.

## Kiểm thử và kết quả thực nghiệm

Frontend dùng ES Modules và JavaScript thuần; xem [kiến trúc giao diện](ARCHITECTURE.md#giao-diện-tìm-đúng-file-cần-đọc) để tìm entry point, state, API, action và view. Kiểm thử frontend riêng bằng Node.js 22+ tại thư mục chứa README:

```powershell
npm test
# Công cụ định dạng và kiểm thử trình duyệt (tùy chọn, cần Chrome):
npm ci
npm run format:check
npm run test:browser
```

Build/chạy ứng dụng Java vẫn dùng Maven như dưới đây, không yêu cầu Node.js.

```powershell
# JUnit và đóng gói
mvn -f source/pom.xml package

# Tùy chọn: 1 tracker + 3 peer thật; cần Python 3
python source/client/src/test/resources/e2e.py
```

10 kiểm thử JUnit đã chạy thành công: hash / ranh giới chunk, frame thiếu / quá lớn, phê duyệt / quyền ADMIN / vé tải / thu hồi quyền, header bảo vệ mutation, checkpoint, bốn client TCP đồng thời, từ chối chunk sai hash, giới hạn hàng đợi, WebSocket push / Origin / đăng xuất.

E2E sử dụng HTTP 18080–18083, TCP 19001–19003, dữ liệu riêng ở `source/target/e2e-*/`. Kịch bản: tải file 32 MiB từ hai nguồn → tạm dừng → tắt cưỡng bức máy nhận → cố ý làm hỏng một phần → restart → tiếp tục → tắt một nguồn giữa chừng → kiểm tra SHA-256 → kiểm tra chia sẻ lại, báo cáo và registry sau restart tracker. Kịch bản cũng thực hiện HTTP Upgrade / xác thực cookie và đọc tiến độ qua WebSocket thật. Các tiến trình do script mở được dừng khi kết thúc.

Đổi cổng bằng `--port 28080 --tcp-port 29001`. Log và result.json nằm trong thư mục test. `--hold 120` giữ các tiến trình thử thêm 120 giây để xem UI; thông tin tài khoản test nằm trong script và chỉ dùng với dữ liệu test riêng.

**Kết quả đã xác minh ngày 16/10/2026:** build thành công, 10/10 JUnit và kịch bản E2E 32 MiB đều PASS. Xem [biên bản kiểm thử](./statics/validation.md) và [kết quả E2E](./statics/e2e-result.json). Đã kiểm tra WebSocket và số liệu luồng / TCP trên trình duyệt, lưu ảnh minh họa bên dưới. Demo trên các máy LAN thật còn cần thực hiện.

![Số liệu TCP và thread pool sau khi hoàn tất demo](./statics/peer-network.png)

## Phạm vi và hướng phát triển

- Localhost / LAN có thể kết nối TCP trực tiếp; chưa có NAT traversal / relay qua Internet.
- TCP / HTTP mặc định chưa mã hóa; TLS, HTTPS, phân quyền nhóm file và giới hạn băng thông toàn cục là hướng mở rộng.
- Khi tracker không hoạt động, việc cấp / xác thực vé dừng; các phần đã tải vẫn giữ. Sau restart tracker cần đăng nhập lại.
- Tốc độ là trung bình từ lần bắt đầu / tiếp tục phiên tải; dữ liệu gửi tính trong lần chạy peer hiện tại. Server nhận tiến độ do peer báo cáo.
- H2 lưu account, peer, file, transfer và audit ở các bảng riêng qua JPA. Manifest và danh sách chunk theo holder nằm trong các cột JSON của từng file; có thể chuẩn hóa tiếp khi cần truy vấn từng chunk bằng SQL.
- Chưa có đổi / quên mật khẩu, tự phát hiện IP LAN hoặc xóa dữ liệu trên giao diện. Khóa / ẩn / dừng chia sẻ có thể khôi phục.
- Trước khi nộp: điền thông tin nhóm, thêm ảnh demo thực tế, thử trên nhiều máy LAN, phân chia công việc và giải thích phần code phụ trách.

## Tài liệu tham khảo

- [Spring Boot 3.5 — System requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [Java 17 Socket API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/Socket.html)
- [Java 17 MessageDigest API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/MessageDigest.html)
- [Spring Framework 6.2 — WebSocket server](https://docs.spring.io/spring-framework/reference/6.2/web/websocket/server.html)
- [H2 Database](https://h2database.com/html/main.html)

Khi nâng cấp từ snapshot registry cũ, `migration/LegacyRegistryMigration` chuyển dữ liệu sang các bảng `tracker_accounts`, `tracker_peers`, `tracker_files`, `tracker_transfers`, `tracker_audit` trong một transaction. Bản JSON trong `registry` được giữ nguyên; bảng `tracker_migrations` đánh dấu để không nhập lại sau restart. Migration lỗi sẽ rollback toàn bộ. Session và vé tải vẫn chỉ nằm trong RAM, peer được đặt offline khi tracker khởi động lại.

### Xóa file toàn mạng

Trong danh mục file của quản trị viên, nút **Xóa file** nằm cạnh **Chờ duyệt**. Sau xác nhận, `DELETE /api/admin/files/{id}` xóa file khỏi danh mục, thu hồi vé tải và lưu dấu xóa bền vững trong `tracker_file_deletions`. Peer không thể tự quảng bá lại nội dung đã bị xóa.

Peer nhận danh sách ID qua `GET /api/files/deletions` khi đăng nhập và theo heartbeat. Peer đang online dừng các worker của file đó, bỏ phiên tải/thư viện và xóa bản `blobs/`, `chunks/`, file lắp ghép do ứng dụng quản lý. Peer offline xử lý khi kết nối và đăng nhập lại; phiên bản peer cũ cần được cập nhật để thực hiện lệnh này. Những file đang bị hệ điều hành giữ mở được đưa vào trạng thái chờ và thử xóa lại. Dấu xóa trong checkpoint ngăn file/phiên tải xuất hiện lại sau restart.

File gốc ở đường dẫn người dùng chọn upload, hoặc bản tải riêng vào thư mục bên ngoài dữ liệu peer, không thuộc phạm vi xóa. Lệnh xóa không thể hoàn tác trong giao diện; ID nội dung đã bị xóa tiếp tục bị tracker từ chối quảng bá.
