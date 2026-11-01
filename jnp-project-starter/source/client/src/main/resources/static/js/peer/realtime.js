'use strict';

const live = new LiveChannel(
  '/ws/peer',
  (next) => {
    if (!next.user) {
      showLogin();
      return;
    }
    applyState(next);
    render();
  },
  (connected) => {
    const badge = document.querySelector('#realtime');
    badge.textContent = connected ? 'WebSocket trực tiếp' : 'Đang nối lại · HTTP dự phòng';
    badge.className = 'badge ' + (connected ? 'ONLINE' : 'WAITING');
  },
  showLogin,
);
