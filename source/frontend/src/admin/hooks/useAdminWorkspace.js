import { useEffect } from 'react';
import { $ } from '../../shared/dom.js';
import { UI } from '../../shared/ui.js';
import { Requests } from '../../shared/requests.js';
import { createAdminState } from '../store/state.js';
import { createAdminRenderer } from '../layout/page.js';
import { createAdminData } from '../store/data.js';
import { createAdminChannel } from '../utils/realtime.js';
import { createAdminAuth } from '../utils/auth.js';
import { createFileActions } from '../actions/files.js';

export function useAdminWorkspace() {
  useEffect(() => {
    const events = new AbortController();
    const listen = (target, name, callback) =>
      target.addEventListener(name, callback, { signal: events.signal });

    const model = createAdminState();
    const renderPage = createAdminRenderer(model);
    const requests = new Requests(render);
    const live = createAdminChannel(
      (next) => data.acceptSnapshot(next),
      () => auth.showLogin(),
    );
    const data = createAdminData({ model, render, showLogin: () => auth.showLogin() });
    const auth = createAdminAuth({ model, live, data, requests });
    const actions = createFileActions({ model, data, requests, render });

    function render() {
      requests.restore();
      renderPage();
      requests.sync();
    }

    listen($('#login-form'), 'submit', (event) => {
      event.preventDefault();
      auth.login(event.target).catch((error) => UI.toast(error.message, true));
    });
    listen($('#logout'), 'click', () =>
      auth.logout().catch((error) => UI.toast(error.message, true)),
    );
    listen($('#refresh'), 'click', () => {
      if (!live.refresh()) data.refresh();
    });

    listen(document, 'click', async (event) => {
      const button = event.target.closest('button');
      if (!button) return;
      if (button.dataset.tab || button.dataset.goto) {
        model.current = button.dataset.tab || button.dataset.goto;
        render();
      }
      try {
        const { action, id, kind, status } = button.dataset;
        if (action === 'moderate') await actions.moderate(kind, id, status);
        else if (action === 'delete-file') await actions.deleteFile(id);
        else if (action === 'scan-file') await actions.scan(id);
        else if (action === 'scan-cancel') await actions.scan(id, 'DELETE');
      } catch (error) {
        UI.toast(error.message, true);
      }
    });

    auth.restore();
    let fallback;
    function startFallback() {
      clearInterval(fallback);
      fallback = setInterval(() => {
        if (model.signedIn && !live.connected) data.refresh();
      }, 10000);
    }
    startFallback();
    listen(window, 'pagehide', () => {
      clearInterval(fallback);
      live.stop();
      data.invalidate();
    });
    listen(window, 'pageshow', (event) => {
      if (!event.persisted) return;
      if (model.signedIn) {
        live.start();
        data.refresh();
      } else auth.restore();
      startFallback();
    });

    return () => {
      events.abort();
      clearInterval(fallback);
      live.stop();
      data.invalidate();
    };
  }, []);
}
