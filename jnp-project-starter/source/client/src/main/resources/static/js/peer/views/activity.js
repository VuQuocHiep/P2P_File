import { UI, esc } from '/assets/js/ui.js';

export function activity(state) {
  return /* HTML */ `<section class="panel">
      <div class="panel-head">
        <h2>Luồng & kết nối TCP trực tiếp</h2>
        <span class="badge ONLINE">Thread pools</span>
      </div>
      <div class="stats">
        ${UI.stat(
          'WORKER GỬI TCP',
          state.network.upload.activeWorkers + ' / ' + state.network.upload.workerLimit,
          'Hàng đợi ' + state.network.upload.queued + ' / ' + state.network.upload.queueLimit,
        )}${UI.stat(
          'WORKER TẢI CHUNK',
          state.network.downloadWorkers,
          'Tối đa 4 worker / file',
          true,
        )}${UI.stat(
          'KẾT NỐI ĐANG MỞ',
          state.network.upload.connections + state.network.download.connections,
          'Gửi + nhận TCP',
        )}${UI.stat(
          'YÊU CẦU TẢI CHUNK',
          state.network.download.requests,
          state.network.download.failures + ' yêu cầu lỗi / thử nguồn khác',
        )}
      </div>
      <p class="section-note">
        tcp-accept nhận kết nối; tcp-upload xử lý gửi; download-coordinator phân lịch;
        chunk-download tải song song; peer-heartbeat kiểm tra nguồn. WebSocket đẩy các số liệu này
        lên trình duyệt mỗi giây.
      </p>
    </section>
    <div class="two-col">
      <section class="panel">
        <div class="panel-head">
          <h2>Thiết bị của bạn</h2>
          ${UI.badge(state.peerStatus)}
        </div>
        <div class="network">
          <div class="network-node">
            <div class="network-circle">⌘</div>
            <strong>${esc(state.name)}</strong><br /><small
              >${esc(state.host)}:${state.tcpPort}</small
            >
          </div>
          <div class="network-line"></div>
          <div class="network-node">
            <div class="network-circle">⋈</div>
            <strong>Tracker</strong><br /><small>Điều phối mạng</small>
          </div>
        </div>
        <p class="section-note">
          Tracker: <span class="mono">${esc(state.tracker)}</span><br />Peer ID:
          <span class="mono">${esc(state.peerId || 'Chưa được cấp')}</span><br />Tài khoản:
          ${esc(state.user.username)} · ${UI.badge(state.user.status)}
        </p>
      </section>
      <section class="panel">
        <h2>Cách phiên tải được khôi phục</h2>
        <p class="section-note">
          Mỗi phần 256 KiB được kiểm tra SHA-256 trước khi lưu. Tiến độ ghi xuống ổ đĩa sau mỗi
          phần. Khi mở lại chương trình, các phần được kiểm tra và phần còn thiếu được xếp lịch tải
          tiếp.
        </p>
        <p class="section-note">
          Mạng LAN: dùng IP thật của máy cho TCP. Giao diện web mặc định chỉ mở trên máy chạy peer.
        </p>
      </section>
    </div>
    <section class="panel">
      <h2>Nhật ký cục bộ</h2>
      ${state.events.length
        ? /* HTML */ `<div class="activity">
            ${state.events
              .slice()
              .reverse()
              .map(
                (line) =>
                  /* HTML */ `<div class="activity-row">
                    <span class="activity-mark">↗</span>
                    <div>${esc(line)}</div>
                  </div>`,
              )
              .join('')}
          </div>`
        : UI.empty('Chưa có hoạt động.')}
    </section>`;
}
