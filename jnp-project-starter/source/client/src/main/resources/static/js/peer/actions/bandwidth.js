import { $ } from '/assets/js/dom.js';
import { UI } from '/assets/js/ui.js';
import { peerApi } from '../api.js';

export function createBandwidthActions({ model, data, requests, client = peerApi }) {
  function open() {
    const bandwidth = model.snapshot?.network?.bandwidth || {};
    $('#upload-limit-kib').value = bandwidth.uploadLimitKiB || 0;
    $('#download-limit-kib').value = bandwidth.downloadLimitKiB || 0;
    $('#bandwidth-error').textContent = '';
    $('#bandwidth-dialog').showModal();
  }

  async function save(form) {
    const uploadText = form.elements.uploadLimitKiB.value;
    const downloadText = form.elements.downloadLimitKiB.value;
    const uploadLimitKiB = Number(uploadText);
    const downloadLimitKiB = Number(downloadText);
    if (
      [uploadText, downloadText].some((value) => value.trim() === '') ||
      [uploadLimitKiB, downloadLimitKiB].some(
        (value) => !Number.isSafeInteger(value) || value < 0 || value > 1048576,
      )
    ) {
      $('#bandwidth-error').textContent =
        'Điền số nguyên từ 0 đến 1048576 KiB/s; 0 là không giới hạn.';
      return;
    }
    const session = data.session;
    await requests.run('bandwidth', async () => {
      $('#bandwidth-error').textContent = '';
      try {
        const result = await client.bandwidth({ uploadLimitKiB, downloadLimitKiB });
        if (session !== data.session || !model.snapshot?.user) return;
        model.snapshot.network.bandwidth = result;
        $('#bandwidth-dialog').close();
        UI.toast('Đã lưu giới hạn băng thông cho peer này.');
        await data.poll();
      } catch (error) {
        if (session === data.session && model.snapshot?.user) {
          $('#bandwidth-error').textContent = error.message;
        }
      }
    });
  }

  return { open, save };
}
