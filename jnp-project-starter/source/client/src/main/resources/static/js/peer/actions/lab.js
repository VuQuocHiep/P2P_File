import { $ } from '/assets/js/dom.js';
import { UI } from '/assets/js/ui.js';
import { peerApi } from '../api.js';

export function createLabActions({ model, data, requests, client = peerApi }) {
  return async function labAction(action, mode) {
    const session = data.session;
    await requests.run('lab', async () => {
      model.lab.busy = true;
      try {
        const body =
          action === 'fault'
            ? { mode, delayMs: 1500, seconds: 30 }
            : { fileId: $('#lab-file')?.value || model.lab.selected };
        const result = await client.lab(action, body);
        if (session !== data.session || !model.snapshot?.user) return;
        UI.toast(result.message);
        await data.poll();
      } finally {
        model.lab.busy = false;
      }
    });
  };
}
