'use strict';

function switchMode(value) {
  mode = value;
  document
    .querySelectorAll('[data-mode]')
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
  state = null;
  $('#login').classList.remove('hidden');
  $('#app').classList.add('hidden');
}

function showApp(next) {
  applyState(next);
  $('#login').classList.add('hidden');
  $('#app').classList.remove('hidden');
  render();
  window.scrollTo(0, 0);
  live.start();
  loadCatalog();
}
