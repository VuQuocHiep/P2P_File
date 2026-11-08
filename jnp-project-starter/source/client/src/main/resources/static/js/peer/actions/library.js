import { $ } from '/assets/js/dom.js';
import { UI } from '/assets/js/ui.js';
import { peerApi } from '../api.js';

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

  async function change(action, id, enabled) {
    if (
      action === 'scan-consent' &&
      enabled &&
      !window.confirm(
        'Cho phép peer tự gửi nội dung file này qua server lên VirusTotal khi admin yêu cầu? Nội dung có thể được chia sẻ với đối tác và khách hàng VirusTotal. Chỉ bật nếu bạn có quyền chia sẻ file.',
      )
    )
      return;
    const session = data.session;
    await requests.run(`library:${id}`, async () => {
      const result =
        action === 'sharing'
          ? await client.sharing(id, enabled)
          : await client.scanConsent(id, enabled);
      if (session !== data.session || !model.snapshot?.user) return;
      UI.toast(result.message);
      await data.poll();
    });
  }

  return { upload, change };
}
