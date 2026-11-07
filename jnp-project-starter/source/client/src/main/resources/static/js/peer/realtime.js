import { LiveChannel } from '/assets/js/realtime.js';
import { $ } from '/assets/js/dom.js';

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
