import { UI, esc, time } from '../../shared/ui.js';
import { controls } from '../components/moderationControls.js';

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
