import { esc } from '../../shared/ui.js';

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
          ${kind === 'peers' ? (status === 'BLOCKED' ? 'Mở khóa' : 'Duyệt') : 'Duyệt / mở'}
        </button>`
      : ''}${status !== 'BLOCKED' && (kind !== 'peers' || status === 'APPROVED')
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
      : ''}${status === 'APPROVED' && kind !== 'peers'
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
