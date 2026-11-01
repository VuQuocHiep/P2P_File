const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

async function renderHistory(history, chunks = [0, 1], totals = { 'Source A · peer-a': 524288 }) {
  const calls = [];
  const content = { innerHTML: '' };
  const dialog = { open: false, showModal() { this.open = true; } };
  const context = vm.createContext({
    document: { addEventListener() {} },
    state: {
      jobs: [{ id: 'job-a', fileId: 'file-a', name: '<sample>.bin', sourceBytes: totals }],
      library: [{ manifest: { id: 'file-a', hashes: ['hash-0', 'hash-1'] }, chunks }],
    },
    $: (selector) => selector === '#sources-content' ? content : dialog,
  });
  context.window = context;
  vm.runInContext(fs.readFileSync(path.resolve(__dirname, '../../../../common/src/main/resources/META-INF/resources/assets/js/ui.js'), 'utf8'), context);
  vm.runInContext('const {escape: esc, bytes} = UI;', context);
  context.UI.api = async (url) => { calls.push(url); return history; };
  vm.runInContext(fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/peer/dialogs/chunk-history.js'), 'utf8'), context);
  await vm.runInContext('showChunkHistory("job-a")', context);
  assert.deepEqual(calls, ['/api/downloads/job-a/chunks']);
  assert.equal(dialog.open, true);
  return content.innerHTML;
}

test('recorded chunk history renders without contacting a source or tracker', async () => {
  const source = { peerId: 'peer-a', name: 'Source <A>', owner: 'alice', host: '127.0.0.1', port: 9001, receivedAt: 123456 };
  const html = await renderHistory({ 0: source, 1: source });
  assert.ok(html.includes('Source &lt;A&gt;'));
  assert.ok(html.includes('alice'));
  assert.ok(html.includes('127.0.0.1:9001'));
  assert.ok(!html.includes('Nguồn chưa được ghi lại'));
  assert.ok(!html.includes('Nguồn đã gửi (tổng hợp)'));
});

test('legacy downloads show saved totals without inventing per-chunk sources', async () => {
  const html = await renderHistory({});
  assert.ok(html.includes('2 chunk đã tải chưa có thông tin nguồn'));
  assert.ok(html.includes('không phải trạng thái online/offline'));
  assert.ok(html.includes('Source A · peer-a'));
  assert.ok(html.includes('512 KiB'));
  assert.equal((html.match(/Nguồn chưa được ghi lại trong phiên này/g) || []).length, 2);
  assert.ok(html.includes('&lt;sample&gt;.bin'));
});

test('incomplete chunks are distinguished from downloaded chunks with missing history', async () => {
  const html = await renderHistory({}, [0], {});
  assert.ok(html.includes('1 chunk đã tải chưa có thông tin nguồn'));
  assert.ok(html.includes('Chưa tải'));
  assert.equal((html.match(/Nguồn chưa được ghi lại trong phiên này/g) || []).length, 1);
});
