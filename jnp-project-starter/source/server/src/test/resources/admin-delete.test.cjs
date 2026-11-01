const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../../main/resources/static/js/admin');

test('file deletion button is immediately after pending approval and absent from account controls', () => {
  const context = vm.createContext({ esc: (value) => value });
  vm.runInContext(fs.readFileSync(path.join(root, 'views/tables.js'), 'utf8'), context);
  const html = vm.runInContext('controls("files","hash","APPROVED")', context);
  assert.ok(html.indexOf('Chờ duyệt') < html.indexOf('Xóa file'));
  assert.ok(html.includes('data-action="delete-file"'));
  assert.ok(!vm.runInContext('controls("accounts","alice","APPROVED")', context).includes('delete-file'));
});

async function clickDeletion(confirmed) {
  const requests = [], notices = [];
  let click, refreshes = 0;
  const button = { dataset: { action: 'delete-file', id: 'hash' }, disabled: false };
  const context = vm.createContext({
    data: { files: [{ manifest: { id: 'hash', name: 'sample.pdf' } }] },
    document: { querySelector: () => ({ addEventListener() {} }), addEventListener: (event, callback) => { if (event === 'click') click = callback; } },
    UI: { api: (url, options) => { requests.push([url, options?.method || 'GET']); return url === '/api/auth/me' ? new Promise(() => {}) : Promise.resolve({ message: 'queued' }); }, toast: (message) => notices.push(message) },
    confirm: (message) => { assert.ok(message.includes('Peer offline')); assert.ok(message.includes('File gốc')); return confirmed; },
    refresh: async () => { refreshes++; }, setInterval() {},
  });
  context.window = context;
  vm.runInContext(fs.readFileSync(path.join(root, 'app.js'), 'utf8'), context);
  await click({ target: { closest: () => button } });
  return { requests, notices, refreshes, disabled: button.disabled };
}

test('cancelled confirmation sends no delete request', async () => {
  const result = await clickDeletion(false);
  assert.deepEqual(result.requests, [['/api/auth/me', 'GET']]);
  assert.equal(result.refreshes, 0);
  assert.equal(result.disabled, false);
});

test('confirmed deletion uses DELETE and refreshes the existing dashboard', async () => {
  const result = await clickDeletion(true);
  assert.deepEqual(result.requests, [['/api/auth/me', 'GET'], ['/api/admin/files/hash', 'DELETE']]);
  assert.deepEqual(result.notices, ['queued']);
  assert.equal(result.refreshes, 1);
  assert.equal(result.disabled, false);
});
