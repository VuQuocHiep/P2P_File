export default function LoginLayout() {
  return (
    <div id="login" className="login-page">
      <section className="login-story">
        <div className="brand"><span className="brand-mark">⋈</span>PeerMesh</div>
        <div className="eyebrow">CHIA SẺ FILE NGANG HÀNG</div>
        <h1>Nhiều nguồn kết nối.<br /><span>Một file toàn vẹn.</span></h1>
        <p>
          Chia sẻ trực tiếp giữa các máy. Tải song song từng phần và tiếp tục hành trình khi kết nối
          trở lại.
        </p>
        <div className="feature-list">
          <div><span>↗</span> Nhiều peer cùng gửi một file</div>
          <div><span>↻</span> Lưu tiến độ, khôi phục sau gián đoạn</div>
          <div><span>✓</span> Kiểm tra toàn vẹn bằng SHA-256</div>
        </div>
      </section>
      <section className="login-area">
        <form id="login-form" className="login-box">
          <div className="tabs">
            <button type="button" className="active" data-mode="login">Đăng nhập</button
            ><button type="button" data-mode="register">Đăng ký</button>
          </div>
          <h2 id="form-title">Kết nối peer của bạn</h2>
          <p id="form-note">Mỗi thiết bị là một điểm tải và chia sẻ trong mạng.</p>
          <div className="field">
            <label htmlFor="username">Tên tài khoản</label
            ><input
              id="username"
              name="username"
              required
              minLength="3"
              maxLength="40"
              autoComplete="username"
              placeholder="vd: thanhvien01"
            />
          </div>
          <div className="field">
            <label htmlFor="password">Mật khẩu</label
            ><input
              id="password"
              name="password"
              type="password"
              required
              minLength="8"
              autoComplete="current-password"
            />
          </div>
          <div id="peer-fields" className="field-row">
            <div className="field">
              <label htmlFor="name">Tên thiết bị</label
              ><input id="name" name="name" required maxLength="60" defaultValue="Peer của tôi" />
            </div>
            <div className="field">
              <label htmlFor="host">IP của máy này</label
              ><input id="host" name="host" required defaultValue="127.0.0.1" aria-describedby="ip-help" />
            </div>
          </div>
          <p id="ip-help" className="section-note">
            Demo cùng máy: 127.0.0.1. Demo LAN: nhập IPv4 của máy chạy peer (xem bằng ipconfig).
          </p>
          <div id="login-error" className="notice error hidden" role="alert"></div>
          <button id="submit-login" data-request-key="auth" className="primary" type="submit">
            Kết nối vào mạng →
          </button>
          <div className="login-foot">
            Tài khoản và thiết bị cần được quản trị viên duyệt. File mới được quét tự động. Dữ liệu của peer được lưu
            trên máy này.
          </div>
        </form>
      </section>
    </div>
  );
}
