import { $ } from '/assets/js/dom.js';
import { UI } from '/assets/js/ui.js';
import { peerApi } from '../api/index.js';
import { applyState } from './state.js';

/** HTTP fallback and WebSocket snapshots feed the same model. */
export function createPeerData({ model, render, showLogin, showApp, client = peerApi }) {
  let generation = 0;
  let revision = 0;
  let polling = null;
  let catalogRequest = null;

  function invalidate() {
    generation++;
    catalogRequest?.abort();
    catalogRequest = null;
    polling = null;
  }

  function acceptSnapshot(next) {
    revision++;
    if (!next.user) return showLogin();
    applyState(model, next);
    if ($('#app').classList.contains('hidden')) showApp(next);
    else render();
  }

  async function loadCatalog() {
    if (model.snapshot?.user?.status !== 'APPROVED') return;
    catalogRequest?.abort();
    const controller = new AbortController();
    catalogRequest = controller;
    const requested = model.query;
    const session = generation;
    try {
      const next = await client.catalog(requested, controller.signal);
      if (controller.signal.aborted || session !== generation || requested !== model.query) return;
      model.catalog = next;
      if (model.tab === 'explore') render();
    } catch (error) {
      if (controller.signal.aborted || session !== generation || requested !== model.query) return;
      if (model.tab === 'explore') {
        model.catalog = [];
        render();
      }
      if (error.status !== 403) UI.toast(error.message, true);
    } finally {
      if (catalogRequest === controller) catalogRequest = null;
    }
  }

  async function loadRooms() {
    if (model.snapshot?.user?.status !== 'APPROVED') return;
    const session = generation;
    try {
      const list = await client.rooms();
      if (session !== generation) return;
      const member = (room) => room.mine || room.membership === 'APPROVED';
      const files = await Promise.all(
        list.map((room) => (member(room) ? client.roomFiles(room.id).catch(() => []) : [])),
      );
      const requests = await Promise.all(
        list.map((room) => (room.mine ? client.roomRequests(room.id).catch(() => []) : [])),
      );
      const members = await Promise.all(
        list.map((room) => (room.mine ? client.roomMembers(room.id).catch(() => []) : [])),
      );
      if (session !== generation) return;
      model.rooms = list;
      model.roomFiles = Object.fromEntries(list.map((room, i) => [room.id, files[i]]));
      model.roomRequests = Object.fromEntries(list.map((room, i) => [room.id, requests[i]]));
      model.roomMembers = Object.fromEntries(list.map((room, i) => [room.id, members[i]]));
      if (model.tab === 'rooms') render();
    } catch (error) {
      if (session !== generation) return;
      model.rooms = [];
      model.roomFiles = {};
      model.roomRequests = {};
      model.roomMembers = {};
      if (model.tab === 'rooms') render();
      if (error.status !== 403) UI.toast(error.message, true);
    }
  }

  function poll() {
    if (polling) return polling;
    const session = generation;
    const startedAtRevision = revision;
    const request = (async () => {
      try {
        const next = await client.status();
        // A response started before logout or a newer live snapshot cannot replace it.
        if (session !== generation || startedAtRevision !== revision) return;
        acceptSnapshot(next);
      } catch (error) {
        if (session === generation && startedAtRevision === revision && model.snapshot) {
          $('#connection').textContent = 'Mất kết nối với ứng dụng peer';
          $('#live-dot').classList.add('off');
        }
      } finally {
        if (polling === request) polling = null;
      }
    })();
    polling = request;
    return request;
  }

  return {
    poll,
    loadCatalog,
    loadRooms,
    acceptSnapshot,
    invalidate,
    get session() {
      return generation;
    },
  };
}
