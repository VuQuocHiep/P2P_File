'use strict';

async function loadCatalog() {
  if (!state?.user || state.user.status !== 'APPROVED' || catalogLoading) return;
  catalogLoading = true;
  const requested = query;
  try {
    const next = await UI.api('/api/catalog?q=' + encodeURIComponent(requested));
    if (requested === query) {
      catalog = next;
      if (tab === 'explore') render();
    }
  } catch (e) {
    if (tab === 'explore') {
      catalog = [];
      render();
    }
    if (e.status !== 403) UI.toast(e.message, true);
  } finally {
    catalogLoading = false;
  }
}

async function poll() {
  if (polling) return;
  polling = true;
  try {
    const next = await UI.api('/api/status');
    if (!next.user) {
      showLogin();
      return;
    }
    applyState(next);
    if ($('#app').classList.contains('hidden')) showApp(next);
    else render();
  } catch (e) {
    if (state) {
      $('#connection').textContent = 'Mất kết nối với ứng dụng peer';
      $('#live-dot').classList.add('off');
    }
  } finally {
    polling = false;
  }
}
