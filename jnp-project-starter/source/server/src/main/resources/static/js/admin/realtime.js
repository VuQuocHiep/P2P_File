'use strict';

const live = new LiveChannel(
  '/ws/admin',
  (next) => {
    data = next;
    document.querySelector('#live-dot').classList.remove('off');
    document.querySelector('#live-text').textContent =
      'Cập nhật ' + new Date().toLocaleTimeString('vi-VN');
    document.querySelector('#global-error').classList.add('hidden');
    render();
  },
  (connected) => {
    const badge = document.querySelector('#realtime');
    badge.textContent = connected ? 'WebSocket trực tiếp' : 'Đang nối lại · HTTP dự phòng';
    badge.className = 'badge ' + (connected ? 'ONLINE' : 'WAITING');
  },
  showLogin,
);
