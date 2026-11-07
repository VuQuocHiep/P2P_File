import { $ } from '/assets/js/dom.js';
import { UI, initializeUI } from '/assets/js/ui.js';
import { Requests } from '/assets/js/requests.js';
import { createAdminState } from './state.js';
import { createAdminRenderer } from './views/page.js';
import { createAdminData } from './data.js';
import { createAdminChannel } from './realtime.js';
import { createAdminAuth } from './auth.js';
import { createFileActions } from './actions/files.js';

initializeUI();
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

$('#login-form').addEventListener('submit', (event) => {
  event.preventDefault();
  auth.login(event.target).catch((error) => UI.toast(error.message, true));
});
$('#logout').addEventListener('click', () =>
  auth.logout().catch((error) => UI.toast(error.message, true)),
);
$('#refresh').addEventListener('click', () => {
  if (!live.refresh()) data.refresh();
});

document.addEventListener('click', async (event) => {
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
window.addEventListener('pagehide', () => {
  clearInterval(fallback);
  live.stop();
  data.invalidate();
});
window.addEventListener('pageshow', (event) => {
  if (!event.persisted) return;
  if (model.signedIn) {
    live.start();
    data.refresh();
  } else auth.restore();
  startFallback();
});
