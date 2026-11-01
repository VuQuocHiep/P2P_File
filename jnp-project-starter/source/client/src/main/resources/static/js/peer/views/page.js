'use strict';

function render() {
  if (!state?.user) return;
  $('#identity').textContent = state.name || state.user.username;
  $('#identity-sub').textContent = state.user.username;
  $('#connection').textContent = state.connection;
  $('#live-dot').classList.toggle('off', !ready() || state.connection !== 'Đã kết nối tracker');
  $('#peer-address').textContent = `${state.name} · TCP ${state.host}:${state.tcpPort}`;
  $('#stats').innerHTML = statCards();
  $('#page-title').textContent = titles[tab];
  $('#breadcrumb').textContent = titles[tab];
  $('#page-note').textContent = notes[tab];
  $('#open-upload').disabled = !ready();
  const notice = $('#notice');
  if (state.user.status !== 'APPROVED') {
    notice.textContent = 'Tài khoản đang chờ quản trị viên duyệt. Trạng thái sẽ tự cập nhật.';
    notice.classList.remove('hidden');
  } else if (state.peerStatus !== 'APPROVED') {
    notice.textContent =
      'Thiết bị peer đang chờ duyệt hoặc đã bị khóa. Quản trị viên xử lý tại mục Thiết bị peer.';
    notice.classList.remove('hidden');
  } else if (state.connection !== 'Đã kết nối tracker') {
    notice.textContent = 'Kết nối tracker: ' + state.connection;
    notice.classList.remove('hidden');
  } else notice.classList.add('hidden');
  document
    .querySelectorAll('[data-tab]')
    .forEach((b) => b.classList.toggle('active', b.dataset.tab === tab));
  if (lastTab !== tab) {
    $('#toolbar').innerHTML =
      tab === 'explore'
        ? '<div class="toolbar"><input id="search" type="search" placeholder="Tìm tên file trong mạng…" aria-label="Tìm kiếm file"><button id="refresh-catalog" class="ghost">↻ Làm mới</button></div>'
        : '';
    if (tab === 'explore') $('#search').value = query;
    lastTab = tab;
  }
  if (document.activeElement?.id !== 'lab-file')
    $('#content').innerHTML = {
      explore,
      downloads,
      library,
      activity,
      lab: () => PeerLab.render(state),
    }[tab]();
}
