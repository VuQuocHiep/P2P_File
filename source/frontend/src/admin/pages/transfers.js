import { UI, esc, bytes, time } from '../../shared/ui.js';

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
