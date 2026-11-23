import WorkspaceNav from '../../shared/WorkspaceNav.jsx';
import { navigation } from '../router/tabs.js';

export default function WorkspaceLayout() {
  return (
    <div id="app" className="shell hidden">
      <aside className="sidebar">
        <div className="brand"><span className="brand-mark">⋈</span>PeerMesh</div>
        <div className="brand-sub">NETWORK ADMINISTRATION</div>
        <div className="nav-title">ĐIỀU KHIỂN MẠNG</div>
        <WorkspaceNav label="Chức năng quản trị" tabs={navigation} />
        <div className="sidebar-bottom">
          <div className="identity">
            <span className="avatar">A</span>
            <div><strong id="identity">admin</strong><small>Quản trị viên hệ thống</small></div>
          </div>
          <button id="logout" data-request-key="auth" className="ghost small">Đăng xuất ↗</button>
        </div>
      </aside>
      <main className="main">
        <header className="topbar">
          <div className="breadcrumb">Không gian quản trị / <span id="breadcrumb">Tổng quan</span></div>
          <div className="live">
            <span id="realtime" className="badge WAITING">Đang nối WebSocket</span
            ><span id="live-dot" className="dot"></span><span id="live-text">Đang kết nối</span
            ><button id="refresh" className="ghost small" aria-label="Làm mới">↻</button>
          </div>
        </header>
        <div id="global-error" className="notice error hidden" role="alert"></div>
        <div id="content"></div>
        <footer className="footer">
          <span>PeerMesh · Java / Spring Boot / TCP</span
          ><span>Registry tập trung · Truyền file trực tiếp giữa các peer</span>
        </footer>
      </main>
    </div>
  );
}
