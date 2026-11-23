export default function LoginLayout() {
  return (
    <div id="login" className="login-page">
      <section className="login-story">
        <div className="brand"><span className="brand-mark">⋈</span>PeerMesh</div>
        <div className="eyebrow">TRUNG TÂM ĐIỀU PHỐI P2P</div>
        <h1>Kết nối từng peer.<br /><span>Kiểm soát toàn mạng.</span></h1>
        <p>
          Quản lý thành viên, phê duyệt thiết bị và theo dõi hoạt động chia sẻ file trong một không
          gian.
        </p>
        <div className="feature-list">
          <div><span>01</span> Phê duyệt tài khoản, thiết bị và file</div>
          <div><span>02</span> Theo dõi mạng và phiên tải trực tiếp</div>
          <div><span>03</span> Cấp quyền tải theo từng nguồn</div>
        </div>
      </section>
      <section className="login-area">
        <form id="login-form" className="login-box">
          <div className="eyebrow">ADMIN CONSOLE</div>
          <h2>Chào mừng trở lại</h2>
          <p>Đăng nhập để quản lý mạng chia sẻ của bạn.</p>
          <div className="field">
            <label htmlFor="username">Tài khoản quản trị</label
            ><input id="username" name="username" autoComplete="username" required defaultValue="admin" />
          </div>
          <div className="field">
            <label htmlFor="password">Mật khẩu</label
            ><input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              required
            />
          </div>
          <div id="login-error" className="notice error hidden" role="alert"></div>
          <button className="primary" type="submit" data-request-key="auth">
            Đăng nhập quản trị →
          </button>
          <div className="login-foot">
            Dùng tài khoản quản trị đã cấu hình khi khởi động server. Người dùng đăng ký và đăng
            nhập tại giao diện peer.
          </div>
        </form>
      </section>
    </div>
  );
}
