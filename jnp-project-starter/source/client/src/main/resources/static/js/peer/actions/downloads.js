import { UI } from '/assets/js/ui.js';
import { peerApi } from '../api.js';

export function createDownloadActions({ model, data, requests, client = peerApi }) {
  return async function downloadAction(action, id) {
    const session = data.session;
    const key = action === 'download' ? `download:${id}` : `job:${id}`;
    await requests.run(key, async () => {
      const result =
        action === 'download' ? await client.download(id) : await client.downloadAction(id, action);
      if (session !== data.session || !model.snapshot?.user) return;
      if (action === 'download') model.tab = 'downloads';
      UI.toast(action === 'download' ? 'Đã bắt đầu phiên tải nhiều nguồn.' : result.message);
      await data.poll();
    });
  };
}
