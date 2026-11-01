'use strict';

for (const key of ['username', 'name', 'host']) {
  const value = localStorage.getItem('peermesh-' + key);
  if (value) $('#' + key).value = value;
}

$('#login-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.target,
    button = event.submitter;
  button.disabled = true;
  try {
    const body = { username: form.username.value, password: form.password.value };
    if (mode === 'register') {
      const result = await UI.api('/api/auth/register', { method: 'POST', body });
      UI.toast(result.message);
      switchMode('login');
    } else {
      body.name = form.name.value;
      body.host = form.host.value;
      const next = await UI.api('/api/auth/login', { method: 'POST', body });
      for (const key of ['username', 'name', 'host'])
        localStorage.setItem('peermesh-' + key, body[key]);
      form.password.value = '';
      showApp(next);
    }
  } catch (e) {
    $('#login-error').textContent = e.message;
    $('#login-error').classList.remove('hidden');
  } finally {
    button.disabled = false;
  }
});

$('#logout').addEventListener('click', async () => {
  try {
    await UI.api('/api/auth/logout', { method: 'POST', body: {} });
    showLogin();
  } catch (e) {
    UI.toast(e.message, true);
  }
});

$('#open-upload').addEventListener('click', () => $('#upload-dialog').showModal());

$('#upload-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const button = event.submitter;
  button.disabled = true;
  $('#upload-status').textContent = 'Đang sao chép file, chia phần và tính SHA-256…';
  try {
    const result = await UI.api('/api/library', {
      method: 'POST',
      body: new FormData(event.target),
    });
    UI.toast(result.message);
    event.target.reset();
    $('#upload-dialog').close();
    tab = 'library';
    await poll();
  } catch (e) {
    UI.toast(e.message, true);
  } finally {
    button.disabled = false;
    $('#upload-status').textContent = '';
  }
});

document.addEventListener('input', (event) => {
  if (event.target.id === 'search') {
    query = event.target.value;
    clearTimeout(UI.searchTimer);
    UI.searchTimer = setTimeout(loadCatalog, 300);
  }
});

document.addEventListener('click', async (event) => {
  const b = event.target.closest('button');
  if (!b) return;
  if (b.dataset.mode) {
    switchMode(b.dataset.mode);
    return;
  }
  if (b.dataset.close) {
    $('#' + b.dataset.close).close();
    return;
  }
  if (b.dataset.tab) {
    tab = b.dataset.tab;
    render();
    if (tab === 'explore') loadCatalog();
    return;
  }
  if (b.id === 'refresh-catalog') {
    loadCatalog();
    return;
  }
  const action = b.dataset.action,
    id = b.dataset.id;
  if (!action) return;
  b.disabled = true;
  try {
    if (action === 'chunk-history') {
      await showChunkHistory(id, Number(b.dataset.page || 0));
    } else if (action === 'download') {
      await UI.api('/api/downloads', { method: 'POST', body: { fileId: id } });
      tab = 'downloads';
      UI.toast('Đã bắt đầu phiên tải nhiều nguồn.');
    } else if (action === 'pause' || action === 'resume') {
      const result = await UI.api(`/api/downloads/${id}/${action}`, { method: 'POST', body: {} });
      UI.toast(result.message);
    } else if (action === 'scan-consent') {
      const enabled = b.dataset.enabled === 'true';
      if (enabled && !window.confirm('Cho phép peer tự gửi nội dung file này qua server lên VirusTotal khi admin yêu cầu? Nội dung có thể được chia sẻ với đối tác và khách hàng VirusTotal. Chỉ bật nếu bạn có quyền chia sẻ file.')) return;
      const result = await UI.api(`/api/library/${id}/scan-consent`, {
        method: 'PATCH', body: { enabled },
      });
      UI.toast(result.message);
    } else if (action === 'sharing') {
      const result = await UI.api(`/api/library/${id}/sharing`, {
        method: 'PATCH',
        body: { enabled: b.dataset.enabled === 'true' },
      });
      UI.toast(result.message);
    } else if (action === 'sources') {
      const sources = await UI.api(`/api/files/${id}/sources`);
      const manifest =
        catalog.find((f) => f.manifest.id === id)?.manifest ||
        state.library.find((f) => f.manifest.id === id)?.manifest;
      $('#sources-content').innerHTML =
        `<p><strong>${esc(manifest?.name || id)}</strong></p><p class="section-note">SHA-256</p><p class="mono hash">${esc(id)}</p>${
          sources.length
            ? UI.table(
                ['Nguồn', 'Địa chỉ TCP', 'Phần đang có'],
                sources.map(
                  (s) =>
                    `<tr><td>${esc(s.name)}<small>${esc(s.owner)}</small></td><td class="mono">${esc(s.host)}:${s.port}</td><td>${s.chunks.length} / ${manifest?.hashes.length ?? '?'}</td></tr>`,
                ),
              )
            : UI.empty('Hiện chưa có nguồn trực tuyến. Phiên tải sẽ tự tìm lại nguồn.')
        }<p class="section-note">Danh sách được lấy từ tracker tại thời điểm mở. Peer chỉ chấp nhận tải khi có vé hợp lệ.</p>`;
      $('#sources-dialog').showModal();
    }
    if (!['sources', 'chunk-history'].includes(action)) await poll();
  } catch (e) {
    UI.toast(e.message, true);
  } finally {
    b.disabled = false;
  }
});

poll();

setInterval(() => {
  if (state && !live.connected) poll();
}, 10000);
