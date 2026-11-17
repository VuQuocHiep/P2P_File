import { $ } from '/assets/js/dom.js';
import { adminApi } from './api.js';

export function createAdminData({ model, render, showLogin, client = adminApi }) {
  let generation = 0;
  let revision = 0;
  let refreshing = null;

  function invalidate() {
    generation++;
    refreshing = null;
  }

  function acceptSnapshot(next) {
    if (!model.signedIn) return;
    revision++;
    model.data = next;
    $('#live-dot').classList.remove('off');
    $('#live-text').textContent = 'Cập nhật ' + new Date().toLocaleTimeString('vi-VN');
    $('#global-error').classList.add('hidden');
    render();
  }

  function refresh() {
    if (!model.signedIn) return Promise.resolve();
    if (refreshing) return refreshing;
    const session = generation;
    const startedAtRevision = revision;
    const request = (async () => {
      try {
        const next = await client.dashboard();
        if (session === generation && startedAtRevision === revision) acceptSnapshot(next);
      } catch (error) {
        if (session !== generation || startedAtRevision !== revision) return;
        if (error.status === 401) showLogin();
        else {
          $('#live-dot').classList.add('off');
          $('#global-error').textContent = error.message;
          $('#global-error').classList.remove('hidden');
        }
      } finally {
        if (refreshing === request) refreshing = null;
      }
    })();
    refreshing = request;
    return request;
  }

  return {
    refresh,
    acceptSnapshot,
    invalidate,
    get session() {
      return generation;
    },
  };
}
