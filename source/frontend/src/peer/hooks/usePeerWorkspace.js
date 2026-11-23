import { useEffect } from 'react';
import { $ } from '../../shared/dom.js';
import { UI } from '../../shared/ui.js';
import { Requests } from '../../shared/requests.js';
import { createPeerState } from '../store/state.js';
import { createPeerRenderer } from '../layout/page.js';
import { createPeerData } from '../store/data.js';
import { createPeerChannel } from '../utils/realtime.js';
import { createPeerAuth } from '../utils/auth.js';
import { createDownloadActions } from '../actions/downloads.js';
import { createLibraryActions } from '../actions/library.js';
import { createLabActions } from '../actions/lab.js';
import { createFileDialogs } from '../components/dialogs/sources.js';
import { createBandwidthActions } from '../actions/bandwidth.js';
import { createRoomActions } from '../actions/rooms.js';

export function usePeerWorkspace() {
  useEffect(() => {
    const events = new AbortController();
    const listen = (target, name, callback) =>
      target.addEventListener(name, callback, { signal: events.signal });

    const model = createPeerState();
    const renderPage = createPeerRenderer(model);
    const requests = new Requests(render);
    const dialogs = createFileDialogs({ model });
    const live = createPeerChannel(
      (next) => data.acceptSnapshot(next),
      () => auth.showLogin(),
    );
    const data = createPeerData({
      model,
      render,
      showLogin: () => auth.showLogin(),
      showApp: (next) => auth.showApp(next),
    });
    const auth = createPeerAuth({ model, live, data, render, requests, dialogs });
    const download = createDownloadActions({ model, data, requests });
    const library = createLibraryActions({ model, data, requests });
    const lab = createLabActions({ model, data, requests });
    const bandwidth = createBandwidthActions({ model, data, requests });
    const room = createRoomActions({ model, data, requests });
    let searchTimer;

    function render() {
      requests.restore();
      renderPage();
      requests.sync();
    }

    const previousUsername = localStorage.getItem('peermesh-username');
    if (previousUsername) $('#username').value = previousUsername;
    auth.restoreDevice();
    listen($('#username'), 'change', auth.restoreDevice);

    listen($('#login-form'), 'submit', (event) => {
      event.preventDefault();
      auth.login(event.target).catch((error) => UI.toast(error.message, true));
    });
    listen($('#logout'), 'click', () =>
      auth.logout().catch((error) => UI.toast(error.message, true)),
    );
    listen($('#open-upload'), 'click', () => $('#upload-dialog').showModal());
    listen($('#upload-form'), 'submit', (event) => {
      event.preventDefault();
      library.upload(event.target).catch((error) => UI.toast(error.message, true));
    });
    listen($('#bandwidth-form'), 'submit', (event) => {
      event.preventDefault();
      bandwidth.save(event.target).catch((error) => UI.toast(error.message, true));
    });
    listen(document, 'submit', (event) => {
      const form = event.target;
      // A control named "id" shadows form.id, so read the id attribute instead of the property.
      const formId = form instanceof HTMLFormElement ? form.getAttribute('id') : '';
      if (formId === 'room-create-form') {
        event.preventDefault();
        room.create(form).catch((error) => UI.toast(error.message, true));
      }
      if (formId === 'room-join-form') {
        event.preventDefault();
        room.join(form).catch((error) => UI.toast(error.message, true));
      }
    });

    listen(document, 'input', (event) => {
      if (event.target.id !== 'search') return;
      model.query = event.target.value;
      clearTimeout(searchTimer);
      searchTimer = setTimeout(() => data.loadCatalog(), 300);
    });
    listen(document, 'change', (event) => {
      if (event.target.id === 'lab-file') model.lab.selected = event.target.value;
      if (event.target.dataset.action === 'priority') {
        const control = event.target;
        download('priority', control.dataset.id, control.value)
          .catch((error) => UI.toast(error.message, true))
          .finally(() => {
            const job = model.snapshot?.jobs.find((job) => job.id === control.dataset.id);
            if (job) control.value = job.priority || 'NORMAL';
            render();
          });
      }
    });
    listen($('#sources-dialog'), 'close', () => dialogs.invalidate());

    listen(document, 'click', async (event) => {
      const button = event.target.closest('button');
      if (!button) return;
      try {
        if (button.dataset.lab) return await lab(button.dataset.lab, button.dataset.mode);
        if (button.dataset.mode) return auth.switchMode(button.dataset.mode);
        if (button.dataset.close) {
          dialogs.invalidate();
          return $('#' + button.dataset.close).close();
        }
        if (button.dataset.tab) {
          model.tab = button.dataset.tab;
          model.roomId = '';
          render();
          if (model.tab === 'explore') await data.loadCatalog();
          if (model.tab === 'rooms') await data.loadRooms();
          return;
        }
        if (button.id === 'refresh-catalog') return await data.loadCatalog();
        const { action, id } = button.dataset;
        if (action === 'bandwidth') bandwidth.open();
        else if (action === 'room-open') {
          model.roomId = button.dataset.room;
          render();
        } else if (action === 'room-back') {
          model.roomId = '';
          render();
        } else if (action === 'room-join-card')
          await room.joinCard(button.dataset.room, button.dataset.password === 'true');
        else if (action === 'room-approve')
          await room.decide(button.dataset.room, button.dataset.user, 'APPROVED');
        else if (action === 'room-reject')
          await room.decide(button.dataset.room, button.dataset.user, 'REJECTED');
        else if (action === 'room-rename') await room.rename(button.dataset.room, button.dataset.name);
        else if (action === 'room-password')
          await room.password(button.dataset.room, button.dataset.hasPassword === 'true');
        else if (action === 'room-kick') await room.kick(button.dataset.room, button.dataset.user);
        else if (action === 'room-delete') await room.destroy(button.dataset.room, button.dataset.name);
        else if (action === 'room-share') await room.share(button.dataset.room);
        else if (['download', 'pause', 'resume'].includes(action)) await download(action, id);
        else if (action === 'sharing') await library.sharing(id, button.dataset.enabled === 'true');
        else if (action === 'sources') await dialogs.sources(id);
        else if (action === 'chunk-history')
          await dialogs.chunkHistory(id, Number(button.dataset.page || 0));
      } catch (error) {
        UI.toast(error.message, true);
      }
    });

    data.poll();
    let fallback;
    function startFallback() {
      clearInterval(fallback);
      fallback = setInterval(() => {
        if (model.snapshot && !live.connected) data.poll();
      }, 10000);
    }
    startFallback();
    listen(window, 'pagehide', () => {
      clearTimeout(searchTimer);
      clearInterval(fallback);
      live.stop();
      data.invalidate();
    });
    listen(window, 'pageshow', (event) => {
      if (!event.persisted) return;
      if (model.snapshot) live.start();
      data.poll();
      startFallback();
    });

    return () => {
      events.abort();
      clearTimeout(searchTimer);
      clearInterval(fallback);
      live.stop();
      data.invalidate();
    };
  }, []);
}
