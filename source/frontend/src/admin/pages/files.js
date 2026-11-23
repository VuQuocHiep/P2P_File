import { UI, esc, bytes } from '../../shared/ui.js';
import { controls } from '../components/moderationControls.js';
import { malwareCell, scanAllowsApproval } from '../components/malware.js';

export function fileTable(data, scanState) {
  return data.files.length
    ? /* HTML */ `<div class="notice scan-guide"><strong>Trạng thái kiểm tra VirusTotal</strong><div class="scan-legend"><span>🔴 Phát hiện mã độc</span><span>🟠 Có dấu hiệu đáng ngờ</span><span>🟢 Chưa phát hiện mã độc</span><span>⚪ Chưa kiểm tra hoặc chưa có dữ liệu</span></div></div>` +
        UI.table(
          ['File', 'Kích thước / phần', 'Nguồn online', 'VirusTotal', 'Phê duyệt', 'Thao tác'],
          data.files.map(
            (f) =>
              /* HTML */ `<tr data-key="${esc(f.manifest.id)}">
                <td>
                  <strong>${esc(f.manifest.name)}</strong
                  ><small class="mono" title="${esc(f.manifest.id)}"
                    >${esc(f.manifest.id.slice(0, 16))}… · ${esc(f.submittedBy)}</small
                  >
                </td>
                <td>
                  ${bytes(f.manifest.size)}<small
                    >${f.manifest.hashes.length} phần × ${bytes(f.manifest.chunkSize)}</small
                  >
                </td>
                <td>${f.sources} / ${f.holders}</td>
                <td>${malwareCell(f, scanState)}</td>
                <td>${UI.badge(f.status)}</td>
                <td>${controls('files', f.manifest.id, f.status, false, scanAllowsApproval(f))}</td>
              </tr>`,
          ),
        )
    : UI.empty('Chưa có file được chia sẻ.');
}
