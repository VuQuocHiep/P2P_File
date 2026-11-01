'use strict';

function controls(kind, id, status, admin = false, allowApproval = true) {
  if (admin) return '<span class="muted">Quản trị viên</span>';
  return `<div class="actions">${status !== 'APPROVED' ? `<button class="small primary" data-action="moderate" data-kind="${kind}" data-id="${esc(id)}" data-status="APPROVED" ${allowApproval ? '' : 'disabled title="File có cảnh báo VirusTotal, không thể duyệt"'}>Duyệt / mở</button>` : ''}${status !== 'BLOCKED' ? `<button class="small danger" data-action="moderate" data-kind="${kind}" data-id="${esc(id)}" data-status="BLOCKED">${kind === 'files' ? 'Ẩn file' : 'Khóa'}</button>` : ''}${status === 'APPROVED' ? `<button class="small ghost" data-action="moderate" data-kind="${kind}" data-id="${esc(id)}" data-status="PENDING">Chờ duyệt</button>` : ''}${kind === 'files' ? `<button class="small danger" data-action="delete-file" data-id="${esc(id)}">Xóa file</button>` : ''}</div>`;
}

function accountTable() {
  return UI.table(
    ['Tài khoản', 'Vai trò', 'Ngày đăng ký', 'Trạng thái', 'Thao tác'],
    data.accounts.map(
      (a) =>
        `<tr><td><strong>${esc(a.username)}</strong></td><td>${a.role === 'ADMIN' ? 'Quản trị viên' : 'Người dùng'}</td><td>${time(a.created)}</td><td>${UI.badge(a.status)}</td><td>${controls('accounts', a.username, a.status, a.role === 'ADMIN')}</td></tr>`,
    ),
  );
}

function peerTable(list = data.peers) {
  return list.length
    ? UI.table(
        ['Thiết bị', 'Địa chỉ TCP', 'Kết nối', 'Phê duyệt', 'Thao tác'],
        list.map(
          (p) =>
            `<tr><td><strong>${esc(p.name)}</strong><small>${esc(p.owner)} · ${esc(p.id.slice(0, 8))}</small></td><td class="mono">${esc(p.host)}:${p.port}</td><td>${UI.badge(p.online ? 'ONLINE' : 'OFFLINE')}<small>${time(p.lastSeen)}</small></td><td>${UI.badge(p.status)}</td><td>${controls('peers', p.id, p.status)}</td></tr>`,
        ),
      )
    : UI.empty('Chưa có thiết bị đăng ký. Đăng nhập tại giao diện peer để thêm thiết bị.');
}

function fileTable() {
  return data.files.length
    ? `<div class="notice scan-guide"><strong>Trạng thái kiểm tra VirusTotal</strong><div class="scan-legend"><span>🔴 Phát hiện mã độc</span><span>🟠 Có dấu hiệu đáng ngờ</span><span>🟢 Chưa phát hiện mã độc</span><span>⚪ Chưa kiểm tra hoặc chưa có dữ liệu</span></div></div>` + UI.table(
        ['File', 'Kích thước / phần', 'Nguồn online', 'VirusTotal', 'Phê duyệt', 'Thao tác'],
        data.files.map(
          (f) =>
            `<tr><td><strong>${esc(f.manifest.name)}</strong><small class="mono" title="${esc(f.manifest.id)}">${esc(f.manifest.id.slice(0, 16))}… · ${esc(f.submittedBy)}</small></td><td>${bytes(f.manifest.size)}<small>${f.manifest.hashes.length} phần × ${bytes(f.manifest.chunkSize)}</small></td><td>${f.sources} / ${f.holders}</td><td>${malwareCell(f)}</td><td>${UI.badge(f.status)}</td><td>${controls('files', f.manifest.id, f.status, false, scanAllowsApproval(f))}</td></tr>`,
        ),
      )
    : UI.empty('Chưa có file được chia sẻ.');
}

function transferTable() {
  return data.transfers.length
    ? UI.table(
        ['File / người tải', 'Trạng thái', 'Đã nhận', 'Cập nhật cuối'],
        data.transfers
          .slice()
          .reverse()
          .map(
            (t) =>
              `<tr><td><strong>${esc(t.name)}</strong><small>${esc(t.owner)} · ${esc(t.peerId.slice(0, 8))}</small></td><td>${UI.badge(t.status)}</td><td>${bytes(t.bytes)}</td><td>${time(t.time)}</td></tr>`,
          ),
      )
    : UI.empty('Phiên tải sẽ xuất hiện khi một peer bắt đầu tải file.');
}

function auditRows(limit = 100) {
  return data.audit.length
    ? `<div class="activity">${data.audit
        .slice(0, limit)
        .map(
          (a) =>
            `<div class="activity-row"><span class="activity-mark">↗</span><div><strong>${esc(a.actor)}</strong> · ${esc(a.action)}<small class="mono">${esc(a.target)} · ${time(a.time)}</small></div></div>`,
        )
        .join('')}</div>`
    : UI.empty('Chưa có hoạt động.');
}
