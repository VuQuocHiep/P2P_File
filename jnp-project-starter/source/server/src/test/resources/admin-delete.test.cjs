const { test } = require('node:test');
const assert = require('node:assert/strict');
const { createModuleLoader } = require('../../../../test/frontend/modules.cjs');

test('file deletion button is immediately after pending approval and absent from account controls', async () => {
  const load = createModuleLoader();
  const { controls } = await load('server/src/main/resources/static/js/admin/views/tables.js');
  const html = controls('files', 'hash', 'APPROVED');
  assert.ok(html.indexOf('Chờ duyệt') < html.indexOf('Xóa file'));
  assert.ok(html.includes('data-action="delete-file"'));
  assert.ok(!controls('accounts', 'alice', 'APPROVED').includes('delete-file'));
});

async function clickDeletion(confirmed) {
  const requests = [],
    notices = [];
  let refreshes = 0;
  const load = createModuleLoader({
    window: {
      confirm(message) {
        assert.ok(message.includes('Peer offline'));
        assert.ok(message.includes('File gốc'));
        return confirmed;
      },
    },
  });
  const { UI } = await load('common/src/main/resources/META-INF/resources/assets/js/ui.js');
  UI.toast = (message) => notices.push(message);
  const { Requests } = await load(
    'common/src/main/resources/META-INF/resources/assets/js/requests.js',
  );
  const { createFileActions } = await load(
    'server/src/main/resources/static/js/admin/actions/files.js',
  );
  const pending = new Requests();
  const actions = createFileActions({
    model: { signedIn: true, data: { files: [{ manifest: { id: 'hash', name: 'sample.pdf' } }] } },
    data: {
      session: 0,
      refresh: async () => {
        refreshes++;
      },
    },
    requests: pending,
    client: {
      deleteFile: async (id) => {
        requests.push([`/api/admin/files/${id}`, 'DELETE']);
        return { message: 'queued' };
      },
    },
  });
  await actions.deleteFile('hash');
  return { requests, notices, refreshes, pending: pending.pending.size };
}

test('cancelled confirmation sends no delete request', async () => {
  const result = await clickDeletion(false);
  assert.deepEqual(result.requests, []);
  assert.equal(result.refreshes, 0);
  assert.equal(result.pending, 0);
});

test('confirmed deletion uses DELETE and refreshes the existing dashboard', async () => {
  const result = await clickDeletion(true);
  assert.deepEqual(result.requests, [['/api/admin/files/hash', 'DELETE']]);
  assert.deepEqual(result.notices, ['queued']);
  assert.equal(result.refreshes, 1);
  assert.equal(result.pending, 0);
});
