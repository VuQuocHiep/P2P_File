import { $ } from '/assets/js/dom.js';
import { UI, esc } from '/assets/js/ui.js';
import { peerApi } from '../api.js';
import { renderChunkHistory } from './chunk-history.js';

export function createFileDialogs({ model, client = peerApi }) {
  let revision = 0;

  function open(html, request) {
    if (request !== revision || !model.snapshot?.user) return;
    $('#sources-content').innerHTML = html;
    if (!$('#sources-dialog').open) $('#sources-dialog').showModal();
  }

  async function sources(id) {
    const request = ++revision;
    const list = await client.sources(id);
    const manifest =
      model.catalog.find((f) => f.manifest.id === id)?.manifest ||
      model.snapshot?.library.find((f) => f.manifest.id === id)?.manifest;
    open(
      /* HTML */ `<p><strong>${esc(manifest?.name || id)}</strong></p>
        <p class="section-note">SHA-256</p>
        <p class="mono hash">${esc(id)}</p>
        ${list.length
          ? UI.table(
              ['Nguồn', 'Địa chỉ TCP', 'Phần đang có'],
              list.map(
                (source) =>
                  /* HTML */ `<tr>
                    <td>${esc(source.name)}<small>${esc(source.owner)}</small></td>
                    <td class="mono">${esc(source.host)}:${source.port}</td>
                    <td>${source.chunks.length} / ${manifest?.hashes.length ?? '?'}</td>
                  </tr>`,
              ),
            )
          : UI.empty('Hiện chưa có nguồn trực tuyến. Phiên tải sẽ tự tìm lại nguồn.')}
        <p class="section-note">
          Danh sách được lấy từ tracker tại thời điểm mở. Peer chỉ chấp nhận tải khi có vé hợp lệ.
        </p>`,
      request,
    );
  }

  async function chunkHistory(id, page = 0) {
    const request = ++revision;
    const job = model.snapshot?.jobs.find((item) => item.id === id);
    const entry = model.snapshot?.library.find((item) => item.manifest.id === job?.fileId);
    if (!job || !entry) return;
    const history = await client.chunkHistory(id);
    open(renderChunkHistory(job, entry, history, page), request);
  }

  return {
    sources,
    chunkHistory,
    invalidate() {
      revision++;
    },
  };
}
