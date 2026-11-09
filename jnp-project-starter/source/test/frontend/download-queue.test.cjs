const { test } = require('node:test');
const assert = require('node:assert/strict');
const { createModuleLoader } = require('./modules.cjs');
const peer = 'client/src/main/resources/static/js/peer/';
const common = 'common/src/main/resources/META-INF/resources/assets/js/';

async function fixture(client = {}) {
  const load = createModuleLoader();
  const { Requests } = await load(common + 'requests.js');
  const { UI } = await load(common + 'ui.js');
  const notices = [];
  UI.toast = (message) => notices.push(message);
  const { createDownloadActions } = await load(peer + 'actions/downloads.js');
  const model = { snapshot: { user: { username: 'alice' } }, tab: 'explore' };
  const data = {
    session: 0,
    polls: 0,
    async poll() {
      this.polls++;
    },
  };
  const actions = createDownloadActions({ model, data, requests: new Requests(), client });
  return { actions, model, data, notices, load };
}

test('priority changes use the job mutation lock so pause cannot race the same file', async () => {
  let finish;
  const pending = new Promise((resolve) => {
    finish = resolve;
  });
  const calls = [];
  const f = await fixture({
    downloadPriority(id, priority) {
      calls.push([id, priority]);
      return pending;
    },
    downloadAction() {
      throw new Error('Pause raced priority update');
    },
  });
  const first = f.actions('priority', 'job-1', 'HIGH');
  await f.actions('pause', 'job-1');
  await f.actions('priority', 'job-1', 'LOW');
  assert.deepEqual(calls, [['job-1', 'HIGH']]);
  finish({ message: 'Priority updated' });
  await first;
  assert.equal(f.data.polls, 1);
  assert.deepEqual(f.notices, ['Priority updated']);
});

test('late priority responses after logout do not refresh or display a success', async () => {
  let finish;
  const pending = new Promise((resolve) => {
    finish = resolve;
  });
  const f = await fixture({ downloadPriority: () => pending });
  const first = f.actions('priority', 'job-1', 'HIGH');
  f.data.session++;
  f.model.snapshot = null;
  finish({ message: 'saved' });
  await first;
  assert.equal(f.data.polls, 0);
  assert.deepEqual(f.notices, []);
});

test('failed priority updates retain the snapshot and release the job for retry', async () => {
  let calls = 0;
  const f = await fixture({
    async downloadPriority() {
      if (++calls === 1) throw new Error('offline');
      return { message: 'saved' };
    },
  });
  const snapshot = f.model.snapshot;
  await assert.rejects(f.actions('priority', 'job-1', 'HIGH'), /offline/);
  assert.equal(f.model.snapshot, snapshot);
  await f.actions('priority', 'job-1', 'HIGH');
  assert.equal(f.data.polls, 1);
});

test('downloads display active files then queue positions, priorities and paused history', async () => {
  const f = await fixture();
  const { downloads } = await f.load(peer + 'views/downloads.js');
  const job = (id, status, queuePosition = 0) => ({
    id,
    fileId: id,
    name: id,
    status,
    queuePosition,
    priority: 'HIGH',
    bytes: 0,
    size: 10,
    speed: 0,
    chunks: 0,
    sourceBytes: {},
    error: '',
  });
  const jobs = [
    job('active', 'WAITING'),
    job('queue-second', 'QUEUED', 2),
    job('queue-first', 'QUEUED', 1),
    job('paused', 'PAUSED'),
    job('complete', 'COMPLETED'),
  ];
  const html = downloads({
    user: { status: 'APPROVED' },
    peerStatus: 'APPROVED',
    jobs,
    network: { fileLimit: 2 },
    library: jobs.map((j) => ({ manifest: { id: j.fileId, hashes: ['hash'] }, chunks: [] })),
  });
  assert.match(html, /Đang tải 1\/2 file · Đang xếp hàng 2 file/);
  const keys = [...html.matchAll(/<article class="download-card" data-key="([^"]+)"/g)].map(
    (m) => m[1],
  );
  assert.deepEqual(keys, ['active', 'queue-first', 'queue-second', 'complete', 'paused']);
  assert.match(html, /Vị trí hàng đợi: #1/);
  const first = html.slice(
    html.indexOf('data-key="queue-first"'),
    html.indexOf('data-key="queue-second"'),
  );
  assert.match(first, /data-action="pause"/);
  assert.doesNotMatch(first, /data-action="resume"/);
  assert.match(first, /value="HIGH"\s+selected/);
  assert.match(first, /Đang xếp hàng/);
  assert.doesNotMatch(
    html.slice(html.indexOf('data-key="complete"'), html.indexOf('data-key="paused"')),
    /data-action="priority"/,
  );
});

test('priority transport uses PATCH and the existing authenticated API helper', async () => {
  const calls = [];
  const load = createModuleLoader({
    fetch: async (url, options) => {
      calls.push({ url, ...options });
      return {
        ok: true,
        headers: { get: () => 'application/json' },
        json: async () => ({ message: 'saved' }),
      };
    },
  });
  const { peerApi } = await load(peer + 'api.js');
  await peerApi.downloadPriority('job-1', 'LOW');
  assert.equal(calls[0].url, '/api/downloads/job-1/priority');
  assert.equal(calls[0].method, 'PATCH');
  assert.equal(calls[0].headers['X-PeerMesh'], '1');
  assert.equal(calls[0].body, JSON.stringify({ priority: 'LOW' }));
});
