/** Browser regressions against the real static modules, with a local HTTP/WebSocket API fixture. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const crypto = require('node:crypto');
const { chromium } = require(process.env.PEERMESH_PLAYWRIGHT_MODULE || 'playwright');
const { source } = require('./modules.cjs');

const firstId = 'a'.repeat(64);
const secondId = 'b'.repeat(64);
const manifest = (id, name) => ({
  id,
  name,
  size: 524288,
  chunkSize: 262144,
  hashes: ['hash-1', 'hash-2'],
});
const files = [firstId, secondId].map((id, index) => ({
  manifest: manifest(id, index ? 'Beta.bin' : 'Alpha.bin'),
  sources: 2,
  holders: 2,
  submittedBy: 'alice',
  status: 'PENDING',
  malware: {
    status: 'UNDETECTED',
    total: 1,
    malicious: 0,
    suspicious: 0,
    checkedAt: 123,
    engines: [{ name: 'Engine', result: 'undetected' }],
  },
}));

function frame(data) {
  const payload = Buffer.from(JSON.stringify(data));
  const header = Buffer.alloc(payload.length < 126 ? 2 : 4);
  header[0] = 0x81;
  if (payload.length < 126) header[1] = payload.length;
  else {
    header[1] = 126;
    header.writeUInt16BE(payload.length, 2);
  }
  return Buffer.concat([header, payload]);
}

async function fixture(role) {
  const calls = [];
  const sockets = new Set();
  let signedIn = false;
  let sequence = 0;
  let benchmark = { status: 'IDLE', results: [] };
  let scanStage;
  let bandwidth = {
    uploadLimitKiB: 0,
    downloadLimitKiB: 0,
    uploadBytesPerSecond: 1024,
    downloadBytesPerSecond: 2048,
  };
  const jobs = [
    {
      id: 'job-1',
      fileId: firstId,
      name: 'Alpha.bin',
      status: 'DOWNLOADING',
      bytes: 262144,
      size: 524288,
      speed: 2048,
      chunks: 1,
      sourceBytes: { 'Source A': 262144 },
      error: '',
    },
  ];
  const library = files.map((file, index) => ({
    manifest: file.manifest,
    complete: Boolean(index),
    sharing: true,
    catalogStatus: 'APPROVED',
    chunks: index ? [0, 1] : [0],
    scanConsent: false,
  }));

  function snapshot() {
    const user = {
      username: 'alice',
      status: 'APPROVED',
      role: role === 'admin' ? 'ADMIN' : 'USER',
    };
    if (role === 'admin')
      return {
        accounts: [{ ...user, created: 123 }],
        peers: [
          {
            id: 'peer-1',
            name: 'Peer A',
            owner: 'alice',
            host: '127.0.0.1',
            port: 9001,
            online: true,
            lastSeen: Date.now(),
            status: 'APPROVED',
          },
        ],
        files: files.map((file) => ({ ...file, scanStage })),
        transfers: [],
        audit: [],
      };
    return {
      user,
      name: 'Peer A',
      peerId: 'peer-1',
      peerStatus: 'APPROVED',
      host: '127.0.0.1',
      tcpPort: 9001,
      tracker: 'http://127.0.0.1:8080',
      connection: 'Đã kết nối tracker',
      uploaded: sequence,
      catalog: files,
      library,
      jobs,
      events: ['Đã kết nối tracker'],
      network: {
        bandwidth,
        upload: {
          activeWorkers: 0,
          workerLimit: 4,
          queued: 0,
          queueLimit: 16,
          connections: 0,
          remotes: [],
          injected: 0,
        },
        download: { connections: 0, requests: 1, failures: 0 },
        downloadWorkers: 0,
      },
      lab: {
        links: [],
        recentLinks: [],
        sourceStats: {},
        samples: [
          { at: Date.now() - 1000, receive: 0, send: 0 },
          { at: Date.now(), receive: sequence, send: 0 },
        ],
        benchmark,
        fault: { mode: 'NORMAL', until: 0 },
      },
    };
  }

  const server = http.createServer(async (request, response) => {
    const url = new URL(request.url, 'http://127.0.0.1');
    if (url.pathname.startsWith('/api/')) {
      const chunks = [];
      for await (const chunk of request) chunks.push(chunk);
      const body = request.headers['content-type']?.includes('application/json')
        ? JSON.parse(Buffer.concat(chunks).toString() || '{}')
        : undefined;
      calls.push({ path: url.pathname, query: url.search, method: request.method, body });
      response.setHeader('Content-Type', 'application/json');
      const send = (data, status = 200) => {
        response.statusCode = status;
        response.end(JSON.stringify(data));
      };
      if (request.method !== 'GET' && request.headers['x-peermesh'] !== '1')
        return send({ message: 'Missing header' }, 403);
      if (url.pathname === '/api/auth/register')
        return send({ message: 'Đã đăng ký.', status: 'PENDING' });
      if (url.pathname === '/api/auth/login') {
        signedIn = true;
        return send(role === 'admin' ? { user: snapshot().accounts[0] } : snapshot());
      }
      if (url.pathname === '/api/auth/logout') {
        signedIn = false;
        return send({ message: 'Đã đăng xuất.' });
      }
      if (url.pathname === '/api/status') return send(signedIn ? snapshot() : { user: null });
      if (!signedIn) return send({ message: 'Cần đăng nhập.' }, 401);
      if (url.pathname === '/api/auth/me') return send(snapshot().accounts[0]);
      if (url.pathname === '/api/admin/dashboard') return send(snapshot());
      if (url.pathname === '/api/settings/bandwidth') {
        if (request.method === 'PATCH') {
          await new Promise((resolve) => setTimeout(resolve, 200));
          bandwidth = { ...bandwidth, ...body };
        }
        return send(bandwidth);
      }
      if (url.pathname === '/api/catalog') {
        const query = url.searchParams.get('q') || '';
        if (query === 'Al') await new Promise((resolve) => setTimeout(resolve, 800));
        return send(
          files.filter((file) => file.manifest.name.toLowerCase().includes(query.toLowerCase())),
        );
      }
      if (url.pathname.endsWith('/sources'))
        return send([
          {
            id: 'source-1',
            name: 'Source A',
            owner: 'bob',
            host: '127.0.0.1',
            port: 9002,
            chunks: [0, 1],
          },
        ]);
      if (url.pathname.endsWith('/chunks'))
        return send({
          0: {
            peerId: 'source-1',
            name: 'Source A',
            owner: 'bob',
            host: '127.0.0.1',
            port: 9002,
            receivedAt: 123,
          },
        });
      if (url.pathname.endsWith('/pause')) {
        await new Promise((resolve) => setTimeout(resolve, 1200));
        jobs[0].status = 'PAUSED';
      }
      if (url.pathname.endsWith('/resume')) jobs[0].status = 'DOWNLOADING';
      if (url.pathname.endsWith('/sharing'))
        library.find((entry) => url.pathname.includes(entry.manifest.id)).sharing = body.enabled;
      if (url.pathname.endsWith('/scan-consent'))
        library.find((entry) => url.pathname.includes(entry.manifest.id)).scanConsent =
          body.enabled;
      if (url.pathname === '/api/lab/benchmark') benchmark = { status: 'COMPLETED', results: [] };
      if (url.pathname.endsWith('/scan-request'))
        scanStage = request.method === 'DELETE' ? 'CANCELLED' : 'WAIT_SOURCE';
      return send({ message: 'Đã cập nhật.' });
    }
    const relative = url.pathname === '/' ? '/index.html' : url.pathname;
    const base = relative.startsWith('/assets/')
      ? path.join(source, 'common/src/main/resources/META-INF/resources')
      : path.join(source, role === 'peer' ? 'client' : 'server', 'src/main/resources/static');
    const filename = path.resolve(base, '.' + relative);
    if (!filename.startsWith(base + path.sep) || !fs.existsSync(filename)) {
      response.writeHead(404);
      response.end();
      return;
    }
    response.setHeader(
      'Content-Type',
      filename.endsWith('.js')
        ? 'text/javascript'
        : filename.endsWith('.css')
          ? 'text/css'
          : 'text/html',
    );
    response.setHeader(
      'Content-Security-Policy',
      "default-src 'self'; connect-src 'self' ws: wss:; script-src 'self'; style-src 'self'; img-src 'self' data:",
    );
    response.end(fs.readFileSync(filename));
  });
  server.on('upgrade', (request, socket) => {
    const accept = crypto
      .createHash('sha1')
      .update(request.headers['sec-websocket-key'] + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11')
      .digest('base64');
    socket.write(
      'HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ' +
        accept +
        '\r\n\r\n',
    );
    sockets.add(socket);
    const timer = setInterval(() => {
      if (signedIn && !socket.destroyed)
        socket.write(frame({ type: 'snapshot', sequence: ++sequence, data: snapshot() }));
    }, 100);
    socket.on('error', () => {});
    socket.on('data', (bytes) => {
      if ((bytes[0] & 15) === 8) socket.end();
    });
    socket.on('close', () => {
      clearInterval(timer);
      sockets.delete(socket);
    });
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  return {
    url: 'http://127.0.0.1:' + server.address().port,
    calls,
    get sequence() {
      return sequence;
    },
    close: () =>
      new Promise((resolve) => {
        for (const socket of sockets) socket.destroy();
        server.closeAllConnections();
        server.close(resolve);
      }),
  };
}

const pageSockets = new WeakMap();
function watch(page, errors) {
  page.on('websocket', (socket) => pageSockets.set(page, socket));
  page.on('console', (message) => {
    if (message.type() === 'error' && message.text().includes('WebSocket snapshot')) {
      console.error(message.text());
      errors.push(message.text());
    }
  });
}

async function waitFrames(page, count = 3) {
  const socket = pageSockets.get(page);
  assert.ok(socket, 'Expected an active WebSocket');
  await new Promise((resolve, reject) => {
    let received = 0;
    const timer = setTimeout(() => {
      socket.off('framereceived', onFrame);
      reject(new Error('No live snapshots received'));
    }, 5000);
    function onFrame() {
      if (++received < count) return;
      clearTimeout(timer);
      socket.off('framereceived', onFrame);
      resolve();
    }
    socket.on('framereceived', onFrame);
  });
}

async function run() {
  const peer = await fixture('peer');
  const admin = await fixture('admin');
  let browser;
  const errors = [];
  try {
    browser = await chromium.launch({
      channel: process.env.PEERMESH_BROWSER_CHANNEL || 'chrome',
      headless: true,
    });
    const page = await browser.newPage();
    watch(page, errors);
    page.on('pageerror', (error) => errors.push(error.message));
    await page.goto(peer.url);
    await page.locator('[data-mode="register"]').click();
    await page.locator('#username').fill('alice');
    await page.locator('#password').fill('test-password');
    await page.locator('#submit-login').click();
    await page.waitForFunction(
      () => document.querySelector('#form-title').textContent === 'Kết nối peer của bạn',
    );
    await page.locator('#password').fill('test-password');
    await page.locator('#submit-login').click();
    await page.locator('#app').waitFor({ state: 'visible' });
    await page.waitForFunction(
      () => document.querySelector('#realtime').textContent === 'WebSocket trực tiếp',
    );
    await page.evaluate(async () => {
      const { updateHTML } = await import('/assets/js/dom.js');
      const root = document.createElement('div');
      document.body.append(root);
      updateHTML(
        root,
        '<button data-key="a">A</button><button data-key="b">B</button><details><summary>More</summary>Report</details>',
      );
      const button = root.querySelector('[data-key="b"]');
      button.focus();
      const details = root.querySelector('details');
      details.open = true;
      updateHTML(
        root,
        '<button data-key="b">B updated</button><button data-key="a">A</button><details><summary>More</summary>New report</details>',
      );
      if (root.firstChild !== button || document.activeElement !== button || !details.open)
        throw new Error('DOM state was lost during a keyed update');
      root.remove();
    });
    const resumedSocket = page.waitForEvent('websocket');
    await page.evaluate(() => {
      window.dispatchEvent(new PageTransitionEvent('pagehide', { persisted: true }));
      window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true }));
    });
    await resumedSocket;
    await waitFrames(page);
    await page.locator('#search').fill('Al');
    await page.waitForRequest((request) => request.url().includes('/api/catalog?q=Al'));
    await page.locator('#search').fill('Beta');
    await page.waitForFunction(
      () =>
        document.querySelector('#content').textContent.includes('Beta.bin') &&
        !document.querySelector('#content').textContent.includes('Alpha.bin'),
    );
    assert.equal(await page.locator('#search').inputValue(), 'Beta');

    await page.locator('#search').fill('');
    await page.locator(`[data-action="download"][data-id="${firstId}"]`).click();
    await page.locator('[data-action="pause"]').waitFor();
    assert.equal(
      peer.calls.filter((call) => call.path === '/api/downloads' && call.method === 'POST').length,
      1,
    );
    const pause = page.locator('[data-action="pause"]');
    await pause.focus();
    await page.evaluate(() => {
      window.originalPause = document.querySelector('[data-action="pause"]');
    });
    await waitFrames(page);
    assert.equal(await page.evaluate(() => document.activeElement === window.originalPause), true);
    await pause.click();
    await page.waitForFunction(() => document.querySelector('[data-action="pause"]')?.disabled);
    await waitFrames(page);
    assert.equal(await pause.isDisabled(), true);
    await page.evaluate(() =>
      document
        .querySelector('[data-action="pause"]')
        .dispatchEvent(new MouseEvent('click', { bubbles: true })),
    );
    await page.locator('[data-action="resume"]').waitFor();
    assert.equal(peer.calls.filter((call) => call.path.endsWith('/pause')).length, 1);
    await page.locator('[data-action="resume"]').click();
    await page.locator('[data-action="pause"]').waitFor();
    await page.locator('[data-action="chunk-history"]').click();
    await page.locator('#sources-dialog').waitFor({ state: 'visible' });
    assert.match(await page.locator('#sources-content').textContent(), /Source A/);
    await page.locator('[data-close="sources-dialog"]').click();
    await page.locator('[data-action="sources"]').click();
    await page.locator('#sources-dialog').waitFor({ state: 'visible' });
    await page.locator('[data-close="sources-dialog"]').click();

    await page.locator('[data-tab="library"]').click();
    await page.locator('[data-action="sharing"]').first().click();
    await page.waitForFunction(
      () => document.querySelector('[data-action="sharing"]').dataset.enabled === 'true',
    );
    page.once('dialog', (dialog) => dialog.accept());
    await page.locator('[data-action="scan-consent"]').click();
    await page.waitForFunction(
      () => document.querySelector('[data-action="scan-consent"]').dataset.enabled === 'false',
    );
    await page.locator('#open-upload').click();
    await page
      .locator('input[type="file"]')
      .setInputFiles({ name: 'demo.txt', mimeType: 'text/plain', buffer: Buffer.from('sample') });
    await page.locator('#upload-form button[type="submit"]').click();
    await page.locator('#upload-dialog').waitFor({ state: 'hidden' });

    await page.locator('[data-tab="lab"]').click();
    await page.locator('#lab-file').selectOption(secondId);
    await page.locator('#lab-file').focus();
    await waitFrames(page);
    assert.equal(await page.locator('#lab-file').inputValue(), secondId);
    assert.equal(await page.evaluate(() => document.activeElement.id), 'lab-file');
    await page.locator('[data-lab="benchmark"]').click();
    await page.waitForFunction(() =>
      document.querySelector('#content').textContent.includes('Hoàn tất'),
    );
    await page.locator('[data-lab="fault"][data-mode="CORRUPT"]').click();
    await page.waitForFunction(() => !document.querySelector('[data-lab="benchmark"]').disabled);
    assert.equal(
      peer.calls.find((call) => call.path === '/api/lab/benchmark').body.fileId,
      secondId,
    );
    assert.equal(peer.calls.find((call) => call.path === '/api/lab/fault').body.mode, 'CORRUPT');
    await page.locator('[data-tab="activity"]').click();
    await page.locator('[data-action="bandwidth"]').click();
    await page.locator('#upload-limit-kib').fill('128');
    await page.locator('#download-limit-kib').fill('64');
    await page.locator('#download-limit-kib').focus();
    await waitFrames(page);
    assert.equal(await page.locator('#download-limit-kib').inputValue(), '64');
    assert.equal(await page.evaluate(() => document.activeElement.id), 'download-limit-kib');
    await page.locator('#bandwidth-form button[type="submit"]').click();
    await page.locator('#bandwidth-dialog').waitFor({ state: 'hidden' });
    await page.waitForFunction(() =>
      document.querySelector('[data-bandwidth="upload"]').textContent.includes('128 KiB/s'),
    );
    assert.match(await page.locator('[data-bandwidth="download"]').textContent(), /64 KiB\/s/);
    await page.locator('[data-action="bandwidth"]').click();
    assert.equal(await page.locator('#upload-limit-kib').inputValue(), '128');
    await page.locator('#upload-limit-kib').fill('256');
    await waitFrames(page);
    assert.equal(await page.locator('#upload-limit-kib').inputValue(), '256');
    await page.locator('[data-close="bandwidth-dialog"]').click();
    assert.equal(peer.calls.filter((call) => call.path === '/api/settings/bandwidth').length, 1);
    await page.locator('[data-action="bandwidth"]').click();
    await page.locator('#upload-limit-kib').fill('0');
    await page.locator('#download-limit-kib').fill('0');
    await page.locator('#bandwidth-form button[type="submit"]').click();
    await page.locator('#bandwidth-dialog').waitFor({ state: 'hidden' });
    await page.waitForFunction(() =>
      document.querySelector('[data-bandwidth="upload"]').textContent.includes('Không giới hạn'),
    );
    await page.locator('#logout').click();
    await page.locator('#login').waitFor({ state: 'visible' });

    const control = await browser.newPage();
    watch(control, errors);
    control.on('pageerror', (error) => errors.push(error.message));
    await control.goto(admin.url);
    await control.locator('#username').fill('alice');
    await control.locator('#password').fill('test-password');
    await control.locator('#login-form button[type="submit"]').click();
    await control.locator('#app').waitFor({ state: 'visible' });
    await control.locator('[data-tab="files"]').click();
    await control.locator('details summary').first().click();
    await waitFrames(control);
    assert.equal(
      await control
        .locator('details')
        .first()
        .evaluate((element) => element.open),
      true,
    );
    control.once('dialog', (dialog) => dialog.dismiss());
    await control.locator('[data-action="delete-file"]').first().click();
    assert.equal(admin.calls.filter((call) => call.method === 'DELETE').length, 0);
    await control.locator('[data-action="scan-file"]').first().click();
    await control.locator('[data-action="scan-cancel"]').first().waitFor();
    await control.locator('[data-action="scan-cancel"]').first().click();
    await control.locator('[data-action="scan-file"]').first().waitFor();
    await control.locator('[data-action="moderate"][data-status="APPROVED"]').first().click();
    control.once('dialog', (dialog) => dialog.accept());
    await control.locator('[data-action="delete-file"]').first().click();
    await control.locator('#logout').click();
    await control.locator('#login').waitFor({ state: 'visible' });
    assert.equal(
      admin.calls.filter((call) => call.path.endsWith('/scan-request') && call.method === 'POST')
        .length,
      1,
    );
    assert.equal(
      admin.calls.filter((call) => call.path.endsWith('/scan-request') && call.method === 'DELETE')
        .length,
      1,
    );
    assert.equal(
      admin.calls.filter(
        (call) => call.path === '/api/admin/files/' + firstId && call.method === 'DELETE',
      ).length,
      1,
    );
    assert.deepEqual(errors, []);
    console.log(
      'PASS: peer registration/login/search/download controls/history/sources/sharing/consent/upload/lab/logout; admin login/details/scan/moderation/delete/logout; live focus and request locks.',
    );
  } finally {
    await browser?.close();
    await peer.close();
    await admin.close();
  }
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
