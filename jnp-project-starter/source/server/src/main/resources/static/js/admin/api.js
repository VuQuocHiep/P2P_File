'use strict';

async function refresh() {
  if (!signedIn || refreshing) return;
  refreshing = true;
  try {
    data = await UI.api('/api/admin/dashboard');
    document.querySelector('#live-dot').classList.remove('off');
    document.querySelector('#live-text').textContent =
      'Cập nhật ' + new Date().toLocaleTimeString('vi-VN');
    document.querySelector('#global-error').classList.add('hidden');
    render();
  } catch (e) {
    if (e.status === 401) showLogin();
    else {
      document.querySelector('#live-dot').classList.add('off');
      document.querySelector('#global-error').textContent = e.message;
      document.querySelector('#global-error').classList.remove('hidden');
    }
  } finally {
    refreshing = false;
  }
}
