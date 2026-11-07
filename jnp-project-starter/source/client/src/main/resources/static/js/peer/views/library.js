import { UI, esc, bytes } from '/assets/js/ui.js';
import { ready } from '../state.js';

export function library(state) {
  return state.library.length
    ? /* HTML */ `<section class="panel">
          ${UI.table(
            ['File', 'Tiến độ lưu trữ', 'Danh mục', 'Chia sẻ', 'Quyền VirusTotal', 'Thao tác'],
            state.library.map(
              (e) =>
                /* HTML */ `<tr data-key="${esc(e.manifest.id)}">
                  <td>
                    <strong>${esc(e.manifest.name)}</strong
                    ><small class="mono">${e.manifest.id.slice(0, 16)}…</small>
                  </td>
                  <td>
                    ${bytes(e.manifest.size)}<small
                      >${e.complete
                        ? '✓ File hoàn chỉnh'
                        : e.chunks.length +
                          '/' +
                          e.manifest.hashes.length +
                          ' phần đã xác minh'}</small
                    >
                  </td>
                  <td>${UI.badge(e.catalogStatus)}</td>
                  <td>${UI.badge(e.sharing ? 'ONLINE' : 'OFFLINE')}</td>
                  <td>
                    <span class="badge">${e.scanConsent ? 'Đã cho phép' : 'Chưa cho phép'}</span>
                  </td>
                  <td>
                    <div class="actions">
                      <button
                        class="small"
                        data-action="sharing"
                        data-request-key="library:${e.manifest.id}"
                        data-id="${e.manifest.id}"
                        data-enabled="${!e.sharing}"
                        ${!ready(state) ? 'disabled' : ''}
                      >
                        ${e.sharing ? 'Dừng chia sẻ' : 'Bật chia sẻ'}</button
                      >${e.complete
                        ? /* HTML */ `<button
                            class="small ghost"
                            data-action="scan-consent"
                            data-request-key="library:${e.manifest.id}"
                            data-id="${e.manifest.id}"
                            data-enabled="${!e.scanConsent}"
                            ${!ready(state) ? 'disabled' : ''}
                          >
                            ${e.scanConsent ? 'Tắt quyền VirusTotal' : 'Cho phép VirusTotal'}
                          </button>`
                        : ''}${e.complete
                        ? /* HTML */ `<a href="/api/library/${e.manifest.id}/content"
                            >↓ Lưu file</a
                          >`
                        : ''}
                    </div>
                  </td>
                </tr>`,
            ),
          )}
        </section>
        <p class="section-note">
          Các phần tải về đã xác minh có thể được chia sẻ trước khi toàn bộ file hoàn tất. Dừng chia
          sẻ sẽ giữ lại dữ liệu trên máy.
        </p>`
    : UI.empty('Thư viện trống. Thêm file để bắt đầu chia sẻ với mạng.');
}
