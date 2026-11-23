import WorkspaceNav from '../../shared/WorkspaceNav.jsx';
import { navigation } from '../router/tabs.js';

export default function WorkspaceLayout() {
  return (
    <div id="app" className="shell hidden">
      <aside className="sidebar">
        <div className="brand"><span className="brand-mark">⋈</span>PeerMesh</div>
        <div className="brand-sub">YOUR PEER WORKSPACE</div>
        <div className="nav-title">KHÔNG GIAN CHIA SẺ</div>
        <WorkspaceNav label="Chức năng peer" tabs={navigation} />
        <div className="sidebar-bottom">
          <div className="identity">
            <span className="avatar">P</span>
            <div>
              <strong id="identity">Peer</strong><small id="identity-sub">Người dùng</small>
            </div>
          </div>
          <button id="logout" data-request-key="auth" className="ghost small">Đăng xuất ↗</button>
        </div>
      </aside>
      <main className="main">
        <header className="topbar">
          <div className="breadcrumb">Không gian peer / <span id="breadcrumb">Khám phá file</span></div>
          <div className="live">
            <span id="realtime" className="badge WAITING">Đang nối WebSocket</span
            ><span id="live-dot" className="dot"></span
            ><span id="connection" className="connection-text">Đang kết nối</span>
          </div>
        </header>
        <div id="notice" className="notice warn hidden" role="status"></div>
        <div className="page-heading">
          <div>
            <div className="eyebrow">CONNECTED. DISTRIBUTED. VERIFIED.</div>
            <h1 id="page-title">Khám phá file</h1>
            <p id="page-note">Tìm file trong mạng và kết nối trực tiếp đến các nguồn.</p>
          </div>
          <button id="open-upload" className="primary">＋ Chia sẻ file</button>
        </div>
        <div id="stats" className="stats"></div>
        <div id="toolbar"></div>
        <div id="content"></div>
        <footer className="footer">
          <span id="peer-address">PeerMesh · TCP</span
          ><span>256 KiB / phần · SHA-256 · Tự lưu tiến độ</span>
        </footer>
      </main>
    </div>
  );
}
