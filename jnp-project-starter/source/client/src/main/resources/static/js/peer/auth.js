import { $ } from '/assets/js/dom.js';
import { UI } from '/assets/js/ui.js';
import { applyState } from './state.js';
import { peerApi } from './api.js';

export function createPeerAuth({ model, live, data, render, requests, dialogs, client = peerApi }) {
  function switchMode(value) {
    model.mode = value;
    const mode = model.mode;
    document
      .querySelectorAll('#login-form [data-mode]')
      .forEach((b) => b.classList.toggle('active', b.dataset.mode === mode));
    $('#form-title').textContent =
      mode === 'login' ? 'Kết nối peer của bạn' : 'Tham gia mạng PeerMesh';
    $('#form-note').textContent =
      mode === 'login'
        ? 'Mỗi thiết bị là một điểm tải và chia sẻ trong mạng.'
        : 'Tạo tài khoản, sau đó chờ quản trị viên phê duyệt.';
    $('#peer-fields').classList.toggle('hidden', mode === 'register');
    $('#ip-help').classList.toggle('hidden', mode === 'register');
    $('#name').required = mode === 'login';
    $('#host').required = mode === 'login';
    $('#submit-login').textContent =
      mode === 'login' ? 'Kết nối vào mạng →' : 'Gửi yêu cầu đăng ký →';
    $('#password').autocomplete = mode === 'login' ? 'current-password' : 'new-password';
    $('#login-error').classList.add('hidden');
  }

  function showLogin() {
    live.stop();
    data.invalidate();
    dialogs.invalidate();
    model.snapshot = null;
    model.catalog = [];
    for (const dialog of document.querySelectorAll('dialog[open]')) dialog.close();
    $('#login').classList.remove('hidden');
    $('#app').classList.add('hidden');
  }

  function showApp(next) {
    applyState(model, next);
    $('#login').classList.add('hidden');
    $('#app').classList.remove('hidden');
    render();
    window.scrollTo(0, 0);
    live.start();
    data.loadCatalog();
  }

  async function login(form) {
    await requests.run('auth', async () => {
      data.invalidate();
      try {
        const body = { username: form.username.value, password: form.password.value };
        if (model.mode === 'register') {
          const result = await client.register(body);
          UI.toast(result.message);
          switchMode('login');
        } else {
          body.name = form.name.value;
          body.host = form.host.value;
          const next = await client.login(body);
          for (const key of ['username', 'name', 'host']) {
            localStorage.setItem('peermesh-' + key, body[key]);
          }
          form.password.value = '';
          showApp(next);
        }
      } catch (error) {
        $('#login-error').textContent = error.message;
        $('#login-error').classList.remove('hidden');
      }
    });
  }

  async function logout() {
    await requests.run('auth', async () => {
      await client.logout();
      showLogin();
    });
  }

  return { switchMode, showLogin, showApp, login, logout };
}
