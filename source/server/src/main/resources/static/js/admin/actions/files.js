import { UI } from '/assets/js/ui.js';
import { adminApi } from '../api.js';

export function createFileActions({ model, data, requests, render, client = adminApi }) {
  async function moderate(kind, id, status) {
    const session = data.session;
    await requests.run(`${kind}:${id}`, async () => {
      await client.moderate(kind, id, status);
      if (session !== data.session || !model.signedIn) return;
      UI.toast('Đã cập nhật phê duyệt.');
      await data.refresh();
    });
  }

  async function deleteFile(id) {
    if (requests.pending.has(`files:${id}`)) return;
    const file = model.data?.files.find((item) => item.manifest.id === id);
    const name = file?.manifest.name || id;
    if (
      !window.confirm(
        `Xóa file “${name}” khỏi toàn mạng?\n\nCác bản file/chunk do ứng dụng quản lý trên peer sẽ bị xóa. Peer offline sẽ xử lý khi kết nối lại. File gốc bên ngoài thư mục dữ liệu peer và bản bạn đã lưu riêng không bị xóa.\n\nKhông thể hoàn tác thao tác này.`,
      )
    )
      return;
    const session = data.session;
    await requests.run(`files:${id}`, async () => {
      const result = await client.deleteFile(id);
      if (session !== data.session || !model.signedIn) return;
      UI.toast(result.message);
      await data.refresh();
    });
  }

  async function scan(id, method = 'POST') {
    const session = data.session;
    await requests.run(`files:${id}`, async () => {
      model.malware.scanningFiles.add(id);
      model.malware.scanErrors.delete(id);
      render();
      try {
        const result = await client.scan(id, method);
        if (session === data.session && model.signedIn) UI.toast(result.message);
      } catch (error) {
        if (session === data.session && model.signedIn) {
          model.malware.scanErrors.set(id, error.message);
          UI.toast(error.message, true);
        }
      } finally {
        model.malware.scanningFiles.delete(id);
        if (session === data.session && model.signedIn) await data.refresh();
      }
    });
  }

  return { moderate, deleteFile, scan };
}
