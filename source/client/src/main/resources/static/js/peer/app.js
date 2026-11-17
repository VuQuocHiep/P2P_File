import { $ } from '/assets/js/dom.js';
import { UI, initializeUI } from '/assets/js/ui.js';
import { Requests } from '/assets/js/requests.js';
import { createPeerState } from './state.js';
import { createPeerRenderer } from './views/page.js';
import { createPeerData } from './data.js';
import { createPeerChannel } from './realtime.js';
import { createPeerAuth } from './auth.js';
import { createDownloadActions } from './actions/downloads.js';
import { createLibraryActions } from './actions/library.js';
import { createLabActions } from './actions/lab.js';
import { createFileDialogs } from './dialogs/sources.js';
import { createBandwidthActions } from './actions/bandwidth.js';
import { createRoomActions } from './actions/rooms.js';

initializeUI();
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
const room = createRoomActions({ data, requests });
let searchTimer;

function render() {
  requests.restore();
  renderPage();
  requests.sync();
}

for (const key of ['username', 'name', 'host']) {
  const value = localStorage.getItem('peermesh-' + key);
  if (value) $('#' + key).value = value;
}

$('#login-form').addEventListener('submit', (event) => {
  event.preventDefault();
  auth.login(event.target).catch((error) => UI.toast(error.message, true));
});
$('#logout').addEventListener('click', () =>
  auth.logout().catch((error) => UI.toast(error.message, true)),
);
$('#open-upload').addEventListener('click', () => $('#upload-dialog').showModal());
$('#upload-form').addEventListener('submit', (event) => {
  event.preventDefault();
  library.upload(event.target).catch((error) => UI.toast(error.message, true));
});
$('#bandwidth-form').addEventListener('submit', (event) => {
  event.preventDefault();
  bandwidth.save(event.target).catch((error) => UI.toast(error.message, true));
});
document.addEventListener('submit', (event) => {
  if (event.target.id === 'room-create-form') {
    event.preventDefault();
    room.create(event.target).catch((error) => UI.toast(error.message, true));
  }
  if (event.target.id === 'room-join-form') {
    event.preventDefault();
    room.join(event.target).catch((error) => UI.toast(error.message, true));
  }
});

document.addEventListener('input', (event) => {
  if (event.target.id !== 'search') return;
  model.query = event.target.value;
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => data.loadCatalog(), 300);
});
document.addEventListener('change', (event) => {
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
$('#sources-dialog').addEventListener('close', () => dialogs.invalidate());

document.addEventListener('click', async (event) => {
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
      render();
      if (model.tab === 'explore') await data.loadCatalog();
      if (model.tab === 'rooms') await data.loadRooms();
      return;
    }
    if (button.id === 'refresh-catalog') return await data.loadCatalog();
    const { action, id } = button.dataset;
    if (action === 'bandwidth') bandwidth.open();
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
    else if (['sharing', 'scan-consent'].includes(action))
      await library.change(action, id, button.dataset.enabled === 'true');
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
window.addEventListener('pagehide', () => {
  clearTimeout(searchTimer);
  clearInterval(fallback);
  live.stop();
  data.invalidate();
});
window.addEventListener('pageshow', (event) => {
  if (!event.persisted) return;
  if (model.snapshot) live.start();
  data.poll();
  startFallback();
});
