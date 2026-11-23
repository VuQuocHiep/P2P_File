import { LiveChannel } from '../../shared/realtime.js';
import { $ } from '../../shared/dom.js';

export function createPeerChannel(onSnapshot, onUnauthorized) {
  return new LiveChannel(
    '/ws/peer',
    onSnapshot,
    (connected) => {
      const badge = $('#realtime');
      badge.textContent = connected ? 'WebSocket trực tiếp' : 'Đang nối lại · HTTP dự phòng';
      badge.className = 'badge ' + (connected ? 'ONLINE' : 'WAITING');
    },
    onUnauthorized,
  );
}
