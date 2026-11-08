import { UI, esc, bytes, time } from '/assets/js/ui.js';
import { malwareCell, scanAllowsApproval } from '../malware.js';

export function controls(kind, id, status, admin = false, allowApproval = true) {
  if (admin) return '<span class="muted">Quản trị viên</span>';
  return /* HTML */ `<div class="actions">
    ${status !== 'APPROVED'
      ? /* HTML */ `<button
          class="small primary"
          data-action="moderate"
          data-request-key="${kind}:${esc(id)}"
          data-kind="${kind}"
          data-id="${esc(id)}"
          data-status="APPROVED"
          ${allowApproval ? '' : 'disabled title="File có cảnh báo VirusTotal, không thể duyệt"'}
        >
          Duyệt / mở
        </button>`
      : ''}${status !== 'BLOCKED'
      ? /* HTML */ `<button
          class="small danger"
          data-action="moderate"
          data-request-key="${kind}:${esc(id)}"
          data-kind="${kind}"
          data-id="${esc(id)}"
          data-status="BLOCKED"
        >
          ${kind === 'files' ? 'Ẩn file' : 'Khóa'}
        </button>`
      : ''}${status === 'APPROVED'
      ? /* HTML */ `<button
          class="small ghost"
          data-action="moderate"
          data-request-key="${kind}:${esc(id)}"
          data-kind="${kind}"
          data-id="${esc(id)}"
          data-status="PENDING"
        >
          Chờ duyệt
        </button>`
      : ''}${kind === 'files'
      ? /* HTML */ `<button
          class="small danger"
          data-action="delete-file"
          data-request-key="files:${esc(id)}"
          data-id="${esc(id)}"
        >
          Xóa file
        </button>`
      : ''}
  </div>`;
}

export function accountTable(data) {
  return UI.table(
    ['Tài khoản', 'Vai trò', 'Ngày đăng ký', 'Trạng thái', 'Thao tác'],
    data.accounts.map(
      (a) =>
        /* HTML */ `<tr data-key="${esc(a.username)}">
          <td><strong>${esc(a.username)}</strong></td>
          <td>${a.role === 'ADMIN' ? 'Quản trị viên' : 'Người dùng'}</td>
          <td>${time(a.created)}</td>
          <td>${UI.badge(a.status)}</td>
          <td>${controls('accounts', a.username, a.status, a.role === 'ADMIN')}</td>
        </tr>`,
    ),
  );
}

export function peerTable(list) {
  return list.length
    ? UI.table(
        ['Thiết bị', 'Địa chỉ TCP', 'Kết nối', 'Phê duyệt', 'Thao tác'],
        list.map(
          (p) =>
            /* HTML */ `<tr data-key="${esc(p.id)}">
              <td>
                <strong>${esc(p.name)}</strong
                ><small>${esc(p.owner)} · ${esc(p.id.slice(0, 8))}</small>
              </td>
              <td class="mono">${esc(p.host)}:${p.port}</td>
              <td>
                ${UI.badge(p.online ? 'ONLINE' : 'OFFLINE')}<small>${time(p.lastSeen)}</small>
              </td>
              <td>${UI.badge(p.status)}</td>
              <td>${controls('peers', p.id, p.status)}</td>
            </tr>`,
        ),
      )
    : UI.empty('Chưa có thiết bị đăng ký. Đăng nhập tại giao diện peer để thêm thiết bị.');
}

export function fileTable(data, scanState) {
  return data.files.length
    ? /* HTML */ `<div class="notice scan-guide"><strong>Trạng thái kiểm tra VirusTotal</strong><div class="scan-legend"><span>🔴 Phát hiện mã độc</span><span>🟠 Có dấu hiệu đáng ngờ</span><span>🟢 Chưa phát hiện mã độc</span><span>⚪ Chưa kiểm tra hoặc chưa có dữ liệu</span></div></div>` +
        UI.table(
          ['File', 'Kích thước / phần', 'Nguồn online', 'VirusTotal', 'Phê duyệt', 'Thao tác'],
          data.files.map(
            (f) =>
              /* HTML */ `<tr data-key="${esc(f.manifest.id)}">
                <td>
                  <strong>${esc(f.manifest.name)}</strong
                  ><small class="mono" title="${esc(f.manifest.id)}"
                    >${esc(f.manifest.id.slice(0, 16))}… · ${esc(f.submittedBy)}</small
                  >
                </td>
                <td>
                  ${bytes(f.manifest.size)}<small
                    >${f.manifest.hashes.length} phần × ${bytes(f.manifest.chunkSize)}</small
                  >
                </td>
                <td>${f.sources} / ${f.holders}</td>
                <td>${malwareCell(f, scanState)}</td>
                <td>${UI.badge(f.status)}</td>
                <td>${controls('files', f.manifest.id, f.status, false, scanAllowsApproval(f))}</td>
              </tr>`,
          ),
        )
    : UI.empty('Chưa có file được chia sẻ.');
}

export function transferTable(data) {
  return data.transfers.length
    ? UI.table(
        ['File / người tải', 'Trạng thái', 'Đã nhận', 'Cập nhật cuối'],
        data.transfers
          .slice()
          .reverse()
          .map(
            (t) =>
              /* HTML */ `<tr>
                <td>
                  <strong>${esc(t.name)}</strong
                  ><small>${esc(t.owner)} · ${esc(t.peerId.slice(0, 8))}</small>
                </td>
                <td>${UI.badge(t.status)}</td>
                <td>${bytes(t.bytes)}</td>
                <td>${time(t.time)}</td>
              </tr>`,
          ),
      )
    : UI.empty('Phiên tải sẽ xuất hiện khi một peer bắt đầu tải file.');
}

export function auditRows(data, limit = 100) {
  return data.audit.length
    ? /* HTML */ `<div class="activity">
        ${data.audit
          .slice(0, limit)
          .map(
            (a) =>
              /* HTML */ `<div class="activity-row">
                <span class="activity-mark">↗</span>
                <div>
                  <strong>${esc(a.actor)}</strong> · ${esc(a.action)}<small class="mono"
                    >${esc(a.target)} · ${time(a.time)}</small
                  >
                </div>
              </div>`,
          )
          .join('')}
      </div>`
    : UI.empty('Chưa có hoạt động.');
}
