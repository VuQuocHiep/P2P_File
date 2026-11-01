'use strict';

document.querySelector('#login-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  let button = event.submitter;
  button.disabled = true;
  try {
    let result = await UI.api('/api/auth/login', {
      method: 'POST',
      body: { username: event.target.username.value, password: event.target.password.value },
    });
    if (result.user.role !== 'ADMIN') {
      await UI.api('/api/auth/logout', { method: 'POST', body: {} });
      throw new Error('Tài khoản này không có quyền quản trị.');
    }
    event.target.password.value = '';
    showApp(result.user);
  } catch (e) {
    let error = document.querySelector('#login-error');
    error.textContent = e.message;
    error.classList.remove('hidden');
  } finally {
    button.disabled = false;
  }
});

document.addEventListener('click', async (event) => {
  const button = event.target.closest('button');
  if (!button) return;
  if (button.dataset.tab || button.dataset.goto) {
    current = button.dataset.tab || button.dataset.goto;
    render();
  }
  if (button.dataset.action === 'moderate') {
    button.disabled = true;
    try {
      await UI.api(`/api/admin/${button.dataset.kind}/${encodeURIComponent(button.dataset.id)}`, {
        method: 'PATCH',
        body: { status: button.dataset.status },
      });
      UI.toast('Đã cập nhật phê duyệt.');
      await refresh();
    } catch (e) {
      UI.toast(e.message, true);
    } finally {
      button.disabled = false;
    }
  }
  if (button.dataset.action === 'delete-file') {
    const file = data?.files.find((f) => f.manifest.id === button.dataset.id);
    const name = file?.manifest.name || button.dataset.id;
    if (
      !window.confirm(
        `Xóa file “${name}” khỏi toàn mạng?\n\nCác bản file/chunk do ứng dụng quản lý trên peer sẽ bị xóa. Peer offline sẽ xử lý khi kết nối lại. File gốc bên ngoài thư mục dữ liệu peer và bản bạn đã lưu riêng không bị xóa.\n\nKhông thể hoàn tác thao tác này.`,
      )
    )
      return;
    button.disabled = true;
    try {
      const result = await UI.api(`/api/admin/files/${encodeURIComponent(button.dataset.id)}`, {
        method: 'DELETE',
      });
      UI.toast(result.message);
      await refresh();
    } catch (error) {
      UI.toast(error.message, true);
    } finally {
      button.disabled = false;
    }
  }
});

document.querySelector('#logout').addEventListener('click', async () => {
  try {
    await UI.api('/api/auth/logout', { method: 'POST', body: {} });
    showLogin();
  } catch (e) {
    UI.toast(e.message, true);
  }
});

document.querySelector('#refresh').addEventListener('click', () => {
  if (!live.refresh()) refresh();
});

UI.api('/api/auth/me')
  .then((user) => {
    if (user.role === 'ADMIN') showApp(user);
  })
  .catch(() => {});

setInterval(() => {
  if (signedIn && !live.connected) refresh();
}, 10000);
