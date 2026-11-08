const { test } = require('node:test');
const assert = require('node:assert/strict');
const { createModuleLoader } = require('./modules.cjs');
const peer = 'client/src/main/resources/static/js/peer/';
const common = 'common/src/main/resources/META-INF/resources/assets/js/';

async function fixture(client) {
  const nodes = new Map();
  const document = {
    querySelector(selector) {
      if (!nodes.has(selector))
        nodes.set(selector, {
          value: '',
          textContent: '',
          open: true,
          showModal() {
            this.open = true;
          },
          close() {
            this.open = false;
          },
        });
      return nodes.get(selector);
    },
  };
  const load = createModuleLoader({ document });
  const { Requests } = await load(common + 'requests.js');
  const { UI } = await load(common + 'ui.js');
  const notices = [];
  UI.toast = (message) => notices.push(message);
  const { createBandwidthActions } = await load(peer + 'actions/bandwidth.js');
  const model = {
    snapshot: {
      user: { username: 'alice' },
      network: { bandwidth: { uploadLimitKiB: 64, downloadLimitKiB: 32 } },
    },
  };
  const data = {
    session: 0,
    polls: 0,
    async poll() {
      this.polls++;
    },
  };
  const requests = new Requests();
  const actions = createBandwidthActions({ model, data, requests, client });
  const form = (upload, download) => ({
    elements: { uploadLimitKiB: { value: upload }, downloadLimitKiB: { value: download } },
  });
  return {
    actions,
    data,
    model,
    requests,
    notices,
    form,
    node: (selector) => document.querySelector(selector),
    load,
  };
}

test('bandwidth dialog opens with the saved settings', async () => {
  const f = await fixture({});
  f.actions.open();
  assert.equal(f.node('#upload-limit-kib').value, 64);
  assert.equal(f.node('#download-limit-kib').value, 32);
  assert.equal(f.node('#bandwidth-dialog').open, true);
});

test('saving sends whole KiB values, prevents duplicate submissions and refreshes once', async () => {
  let resolve;
  const response = new Promise((done) => {
    resolve = done;
  });
  const bodies = [];
  const f = await fixture({
    bandwidth: (body) => {
      bodies.push(body);
      return response;
    },
  });
  const first = f.actions.save(f.form('128', '0'));
  await f.actions.save(f.form('256', '64'));
  assert.equal(bodies.length, 1);
  assert.equal(bodies[0].uploadLimitKiB, 128);
  assert.equal(bodies[0].downloadLimitKiB, 0);
  resolve({ uploadLimitKiB: 128, downloadLimitKiB: 0 });
  await first;
  assert.equal(f.data.polls, 1);
  assert.equal(f.model.snapshot.network.bandwidth.uploadLimitKiB, 128);
  assert.equal(f.node('#bandwidth-dialog').open, false);
  assert.equal(f.requests.pending.size, 0);
});

test('invalid input never reaches the API', async () => {
  const f = await fixture({
    bandwidth() {
      assert.fail('Invalid settings must not be sent');
    },
  });
  for (const [upload, download] of [
    ['', '0'],
    ['-1', '0'],
    ['1.5', '0'],
    ['0', '1048577'],
    ['0', 'NaN'],
  ]) {
    await f.actions.save(f.form(upload, download));
    assert.match(f.node('#bandwidth-error').textContent, /số nguyên/);
  }
});

test('failed saves leave the dialog open and the previous settings intact', async () => {
  const f = await fixture({
    bandwidth: async () => {
      throw new Error('Không lưu được tiến độ tải.');
    },
  });
  await f.actions.save(f.form('128', '64'));
  assert.equal(f.node('#bandwidth-dialog').open, true);
  assert.match(f.node('#bandwidth-error').textContent, /Không lưu/);
  assert.equal(f.model.snapshot.network.bandwidth.uploadLimitKiB, 64);
  assert.equal(f.requests.pending.size, 0);
});

test('a save completed after logout cannot update the next session', async () => {
  let resolve;
  const f = await fixture({
    bandwidth: () =>
      new Promise((done) => {
        resolve = done;
      }),
  });
  const save = f.actions.save(f.form('128', '64'));
  f.data.session++;
  f.model.snapshot = null;
  resolve({ uploadLimitKiB: 128, downloadLimitKiB: 64 });
  await save;
  assert.equal(f.data.polls, 0);
  assert.deepEqual(f.notices, []);
});

test('the bandwidth panel distinguishes live payload speeds and unlimited settings', async () => {
  const f = await fixture({});
  const { bandwidthPanel } = await f.load(peer + 'views/bandwidth.js');
  const html = bandwidthPanel({
    uploadLimitKiB: 64,
    downloadLimitKiB: 0,
    uploadBytesPerSecond: 1024,
    downloadBytesPerSecond: 2048,
  });
  assert.match(html, /Upload: 1.0 KiB\/s/);
  assert.match(html, /Download: 2.0 KiB\/s/);
  assert.match(html, /64 KiB\/s/);
  assert.match(html, /Không giới hạn/);
});
