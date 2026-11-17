const { test } = require('node:test');
const assert = require('node:assert/strict');
const { createModuleLoader } = require('./modules.cjs');
const common = 'common/src/main/resources/META-INF/resources/assets/js/';

test('JSON mutations preserve same-origin cookies and the existing API header', async () => {
  let request;
  const load = createModuleLoader({
    fetch: async (url, options) => {
      request = { url, options };
      return { ok: true, json: async () => ({ message: 'ok' }) };
    },
  });
  const { api } = await load(common + 'http.js');
  await api('/api/downloads', { method: 'POST', body: { fileId: 'file' } });
  assert.equal(request.options.credentials, 'same-origin');
  assert.equal(request.options.headers['X-PeerMesh'], '1');
  assert.equal(request.options.headers['Content-Type'], 'application/json');
  assert.deepEqual(JSON.parse(request.options.body), { fileId: 'file' });
});

test('uploads keep FormData intact so the browser supplies the multipart boundary', async () => {
  const body = new FormData();
  body.append('file', 'sample');
  const load = createModuleLoader({
    fetch: async (url, options) => {
      assert.equal(options.body, body);
      assert.equal(options.headers['Content-Type'], undefined);
      return { ok: true, json: async () => ({}) };
    },
  });
  const { api } = await load(common + 'http.js');
  await api('/api/library', { method: 'POST', body });
});

test('API errors retain their status and server message for auth handling', async () => {
  const load = createModuleLoader({
    fetch: async () => ({
      ok: false,
      status: 401,
      json: async () => ({ message: 'Cần đăng nhập lại.' }),
    }),
  });
  const { api } = await load(common + 'http.js');
  await assert.rejects(
    api('/api/status'),
    (error) => error.status === 401 && error.message === 'Cần đăng nhập lại.',
  );
});
