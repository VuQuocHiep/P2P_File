import { UI, esc, time } from '../../shared/ui.js';
import { controls } from '../components/moderationControls.js';

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
