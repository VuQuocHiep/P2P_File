import { LiveChannel } from '../../shared/realtime.js';
import { $ } from '../../shared/dom.js';

export function createAdminChannel(onSnapshot, onUnauthorized) {
  return new LiveChannel(
    '/ws/admin',
    onSnapshot,
    (connected) => {
      const badge = $('#realtime');
      badge.textContent = connected ? 'WebSocket trực tiếp' : 'Đang nối lại · HTTP dự phòng';
      badge.className = 'badge ' + (connected ? 'ONLINE' : 'WAITING');
    },
    onUnauthorized,
  );
}
