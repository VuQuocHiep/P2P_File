'use strict';

function overview() {
  const pending = [
    ...data.accounts
      .filter((a) => a.status === 'PENDING')
      .map((a) => ({ kind: 'accounts', id: a.username, label: a.username, note: 'Tài khoản mới' })),
    ...data.peers
      .filter((p) => p.status === 'PENDING')
      .map((p) => ({ kind: 'peers', id: p.id, label: p.name, note: 'Thiết bị · ' + p.owner })),
    ...data.files
      .filter((f) => f.status === 'PENDING')
      .map((f) => ({
        kind: 'files',
        id: f.manifest.id,
        label: f.manifest.name,
        note: 'File · ' + bytes(f.manifest.size),
      })),
  ];
  return `<div class="stats">${UI.stat('THÀNH VIÊN', data.accounts.filter((a) => a.role !== 'ADMIN').length, 'Tài khoản trong hệ thống')}${UI.stat('PEER TRỰC TUYẾN', data.peers.filter((p) => p.online).length, 'Heartbeat trong 20 giây', true)}${UI.stat('FILE ĐƯỢC DUYỆT', data.files.filter((f) => f.status === 'APPROVED').length, 'Sẵn sàng để tìm kiếm')}${UI.stat('ĐANG CHỜ DUYỆT', pending.length, 'Tài khoản, peer và file')}</div><div class="two-col"><section class="panel"><div class="panel-head"><h2>Luồng chia sẻ trong mạng</h2><span class="badge">P2P / TCP</span></div><div class="network"><div class="network-node"><div class="network-circle">⌘</div><strong>${data.peers.filter((p) => p.online).length} peer</strong><br><small>Trực tuyến</small></div><div class="network-line"></div><div class="network-node"><div class="network-circle">⋈</div><strong>Tracker</strong><br><small>Điều phối & phê duyệt</small></div><div class="network-line"></div><div class="network-node"><div class="network-circle">▤</div><strong>${data.files.length} file</strong><br><small>Trong danh mục</small></div></div><p class="section-note">Tracker cấp danh sách nguồn và vé tải. Các phần file đi trực tiếp giữa các peer bằng TCP; server lưu thông tin điều phối.</p></section><section class="panel"><div class="panel-head"><h2>Cần phê duyệt</h2><span class="badge PENDING">${pending.length} yêu cầu</span></div>${
    pending.length
      ? pending
          .slice(0, 5)
          .map(
            (p) =>
              `<div class="approval-card"><div><strong>${esc(p.label)}</strong><small>${esc(p.note)}</small></div>${p.kind === 'files' ? '<button class="small ghost" data-goto="files">Duyệt / quét tùy chọn →</button>' : `<button class="small primary" data-action="moderate" data-kind="${p.kind}" data-id="${esc(p.id)}" data-status="APPROVED">Duyệt</button>`}</div>`,
          )
          .join('')
      : UI.empty('Tất cả yêu cầu đã được xử lý.')
  }</section></div><section class="panel"><div class="panel-head"><h2>Thiết bị trong mạng</h2><button class="small ghost" data-goto="peers">Xem tất cả →</button></div>${peerTable(data.peers.slice(0, 5))}</section><section class="panel"><div class="panel-head"><h2>Hoạt động gần đây</h2><button class="small ghost" data-goto="audit">Nhật ký →</button></div>${auditRows(5)}</section>`;
}
