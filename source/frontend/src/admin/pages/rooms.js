import { UI, esc, time } from '../../shared/ui.js';

export function roomTable(list = []) {
  if (!list.length) return UI.empty('Chưa có phòng chia sẻ nào trong mạng.');
  return UI.table(
    ['Phòng', 'Chủ phòng', 'Thành viên', 'Chờ duyệt', 'File', 'Mật khẩu', 'Tạo lúc', 'Trạng thái'],
    list.map(
      (room) =>
        /* HTML */ `<tr data-key="${esc(room.id)}">
          <td>
            <strong>${esc(room.name)}</strong><small class="mono">${esc(room.id)}</small>
          </td>
          <td class="mono">${esc(room.owner)}</td>
          <td>${room.members}</td>
          <td>${room.pendingRequests}</td>
          <td>${room.files}</td>
          <td>${room.hasPassword ? 'Có' : 'Không'}</td>
          <td>${time(room.created)}</td>
          <td>${room.status === 'ACTIVE' ? '<span class="badge">Đang mở</span>' : UI.badge(room.status)}</td>
        </tr>`,
    ),
  );
}
