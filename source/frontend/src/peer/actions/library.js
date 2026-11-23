import { $ } from '../../shared/dom.js';
import { UI } from '../../shared/ui.js';
import { peerApi } from '../api/index.js';

export function createLibraryActions({ model, data, requests, client = peerApi }) {
  async function upload(form) {
    const session = data.session;
    await requests.run('upload', async () => {
      $('#upload-status').textContent = 'Đang sao chép file, chia phần và tính SHA-256…';
      try {
        const result = await client.upload(new FormData(form));
        if (session !== data.session || !model.snapshot?.user) return;
        UI.toast(result.message);
        form.reset();
        $('#upload-dialog').close();
        model.tab = 'library';
        await data.poll();
      } finally {
        $('#upload-status').textContent = '';
      }
    });
  }

  async function sharing(id, enabled) {
    const session = data.session;
    await requests.run(`library:${id}`, async () => {
      const result = await client.sharing(id, enabled);
      if (session !== data.session || !model.snapshot?.user) return;
      UI.toast(result.message);
      await data.poll();
    });
  }

  return { upload, sharing };
}
