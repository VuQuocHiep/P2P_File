const { test } = require('node:test');
const assert = require('node:assert/strict');
const { createModuleLoader } = require('./modules.cjs');

const peer = 'client/src/main/resources/static/js/peer/';
const admin = 'server/src/main/resources/static/js/admin/';
const common = 'common/src/main/resources/META-INF/resources/assets/js/';

function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}

function documentStub() {
  const nodes = new Map();
  return {
    querySelector(selector) {
      if (!nodes.has(selector))
        nodes.set(selector, {
          textContent: '',
          classList: { add() {}, remove() {}, contains: () => false },
        });
      return nodes.get(selector);
    },
  };
}

async function peerFixture(client) {
  const load = createModuleLoader({ document: documentStub() });
  const { createPeerState } = await load(peer + 'state.js');
  const { createPeerData } = await load(peer + 'data.js');
  const { UI } = await load(common + 'ui.js');
  const notices = [];
  UI.toast = (message) => notices.push(message);
  const model = createPeerState();
  model.snapshot = { user: { status: 'APPROVED' } };
  let renders = 0,
    logins = 0;
  const data = createPeerData({
    model,
    client,
    render() {
      renders++;
    },
    showLogin() {
      logins++;
    },
    showApp() {},
  });
  return {
    model,
    data,
    notices,
    load,
    get renders() {
      return renders;
    },
    get logins() {
      return logins;
    },
  };
}

test('typing a newer search aborts the old request and keeps the newest result', async () => {
  const first = deferred(),
    second = deferred(),
    calls = [];
  const fixture = await peerFixture({
    catalog(query, signal) {
      calls.push({ query, signal });
      return calls.length === 1 ? first.promise : second.promise;
    },
  });
  fixture.model.query = 'a';
  const a = fixture.data.loadCatalog();
  fixture.model.query = 'ab';
  const ab = fixture.data.loadCatalog();
  assert.equal(calls[0].signal.aborted, true);
  second.resolve(['new']);
  await ab;
  first.resolve(['old']);
  await a;
  assert.deepEqual(fixture.model.catalog, ['new']);
  assert.equal(fixture.renders, 1);
});

test('a failed obsolete search does not clear new results or display an error', async () => {
  const first = deferred();
  const fixture = await peerFixture({
    catalog: (query) => (query === 'old' ? first.promise : Promise.resolve(['new'])),
  });
  fixture.model.query = 'old';
  const old = fixture.data.loadCatalog();
  fixture.model.query = 'new';
  await fixture.data.loadCatalog();
  first.reject(new Error('old failure'));
  await old;
  assert.deepEqual(fixture.model.catalog, ['new']);
  assert.deepEqual(fixture.notices, []);
});

test('HTTP status started before logout cannot restore the old user', async () => {
  const pending = deferred();
  const fixture = await peerFixture({ status: () => pending.promise });
  const request = fixture.data.poll();
  fixture.data.invalidate();
  fixture.model.snapshot = null;
  pending.resolve({ user: { username: 'old-user' } });
  await request;
  assert.equal(fixture.model.snapshot, null);
  assert.equal(fixture.renders, 0);
});

test('a newer peer WebSocket snapshot wins over an older HTTP response', async () => {
  const pending = deferred();
  const fixture = await peerFixture({ status: () => pending.promise });
  const request = fixture.data.poll();
  fixture.data.acceptSnapshot({ user: { username: 'live' } });
  pending.resolve({ user: { username: 'old-http' } });
  await request;
  assert.equal(fixture.model.snapshot.user.username, 'live');
  assert.equal(fixture.renders, 1);
});

test('search requests invalidated by logout cannot publish results', async () => {
  const pending = deferred();
  const fixture = await peerFixture({ catalog: () => pending.promise });
  const request = fixture.data.loadCatalog();
  fixture.data.invalidate();
  fixture.model.catalog = [];
  pending.resolve(['old']);
  await request;
  assert.deepEqual(fixture.model.catalog, []);
});

test('duplicate mutations are suppressed until completion, and failures release the lock', async () => {
  const load = createModuleLoader();
  const { Requests } = await load(common + 'requests.js');
  const requests = new Requests();
  const pending = deferred();
  let calls = 0;
  const first = requests.run('job:1', () => {
    calls++;
    return pending.promise;
  });
  await requests.run('job:1', () => {
    calls++;
  });
  assert.equal(calls, 1);
  pending.reject(new Error('network failed'));
  await assert.rejects(first, /network failed/);
  assert.equal(requests.pending.size, 0);
  await requests.run('job:1', () => {
    calls++;
  });
  assert.equal(calls, 2);
});

test('a completed download request after logout cannot navigate or refresh the old session', async () => {
  const pending = deferred();
  const fixture = await peerFixture({});
  const { Requests } = await fixture.load(common + 'requests.js');
  const { createDownloadActions } = await fixture.load(peer + 'actions/downloads.js');
  const action = createDownloadActions({
    model: fixture.model,
    data: fixture.data,
    requests: new Requests(),
    client: { download: () => pending.promise },
  });
  const download = action('download', 'file');
  fixture.data.invalidate();
  fixture.model.snapshot = null;
  pending.resolve({});
  await download;
  assert.equal(fixture.model.tab, 'explore');
  assert.deepEqual(fixture.notices, []);
});

test('admin ignores an obsolete dashboard error after a live snapshot has arrived', async () => {
  const load = createModuleLoader({ document: documentStub() });
  const { createAdminState } = await load(admin + 'state.js');
  const { createAdminData } = await load(admin + 'data.js');
  const model = createAdminState();
  model.signedIn = true;
  const pending = deferred();
  let logins = 0;
  const data = createAdminData({
    model,
    render() {},
    showLogin() {
      logins++;
    },
    client: { dashboard: () => pending.promise },
  });
  const request = data.refresh();
  data.acceptSnapshot({ files: ['latest'] });
  pending.reject(Object.assign(new Error('expired'), { status: 401 }));
  await request;
  assert.equal(logins, 0);
  assert.deepEqual(model.data.files, ['latest']);
});

test('admin dashboard response after logout cannot replace data from the next session', async () => {
  const load = createModuleLoader({ document: documentStub() });
  const { createAdminState } = await load(admin + 'state.js');
  const { createAdminData } = await load(admin + 'data.js');
  const model = createAdminState();
  model.signedIn = true;
  const pending = deferred();
  const data = createAdminData({
    model,
    render() {},
    showLogin() {},
    client: { dashboard: () => pending.promise },
  });
  const request = data.refresh();
  data.invalidate();
  model.data = null;
  model.signedIn = false;
  pending.resolve({ files: ['old'] });
  await request;
  assert.equal(model.data, null);
});

test('closing a dialog invalidates its in-flight response', async () => {
  const pending = deferred();
  const fixture = await peerFixture({});
  fixture.model.snapshot.library = [];
  const { createFileDialogs } = await fixture.load(peer + 'dialogs/sources.js');
  const dialogs = createFileDialogs({
    model: fixture.model,
    client: { sources: () => pending.promise },
  });
  const request = dialogs.sources('file');
  dialogs.invalidate();
  pending.resolve([]);
  await request; // The document stub has no dialog methods: reopening would fail this test.
});
