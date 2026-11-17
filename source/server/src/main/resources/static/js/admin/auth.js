import { $ } from '/assets/js/dom.js';
import { adminApi } from './api.js';

export function createAdminAuth({ model, live, data, requests, client = adminApi }) {
  function showApp(user) {
    model.signedIn = true;
    $('#login').classList.add('hidden');
    $('#app').classList.remove('hidden');
    $('#identity').textContent = user.username;
    window.scrollTo(0, 0);
    live.start();
    data.refresh();
  }

  function showLogin() {
    live.stop();
    data.invalidate();
    model.signedIn = false;
    model.data = null;
    model.malware.scanErrors.clear();
    $('#login').classList.remove('hidden');
    $('#app').classList.add('hidden');
  }

  async function login(form) {
    await requests.run('auth', async () => {
      data.invalidate();
      try {
        const result = await client.login({
          username: form.username.value,
          password: form.password.value,
        });
        if (result.user.role !== 'ADMIN') {
          await client.logout();
          throw new Error('Tài khoản này không có quyền quản trị.');
        }
        form.password.value = '';
        $('#login-error').classList.add('hidden');
        showApp(result.user);
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

  async function restore() {
    const session = data.session;
    try {
      const user = await client.me();
      if (session === data.session && user.role === 'ADMIN') showApp(user);
    } catch (_) {
      // The existing login screen remains available when no session can be restored.
    }
  }

  return { showApp, showLogin, login, logout, restore };
}
