import { UI, esc, bytes } from '/assets/js/ui.js';
import { ready } from '../state.js';

export function downloads(state) {
  if (!state.jobs.length)
    return UI.empty('Chưa có phiên tải. Chọn một file trong Khám phá để bắt đầu.');
  const queued = state.jobs.filter((job) => job.status === 'QUEUED').length;
  const active = state.jobs.filter((job) => ['DOWNLOADING', 'WAITING'].includes(job.status)).length;
  const rank = (job) =>
    ['DOWNLOADING', 'WAITING'].includes(job.status) ? 0 : job.status === 'QUEUED' ? 1 : 2;
  const cards = state.jobs
    .slice()
    .reverse()
    .sort(
      (a, b) =>
        rank(a) - rank(b) || (a.status === 'QUEUED' ? a.queuePosition - b.queuePosition : 0),
    )
    .map((j) => {
      const entry = state.library.find((e) => e.manifest.id === j.fileId),
        m = entry.manifest,
        percent = j.size
          ? Math.floor((j.bytes * 100) / j.size)
          : j.status === 'COMPLETED'
            ? 100
            : 0,
        done = new Set(entry.chunks),
        count = Math.min(256, m.hashes.length);
      return /* HTML */ `<article class="download-card" data-key="${esc(j.id)}">
        <div class="download-top">
          <div>
            <h3>${esc(j.name)}</h3>
            <span class="muted">${bytes(j.size)} · ${j.chunks}/${m.hashes.length} phần</span>
          </div>
          ${UI.badge(j.status)}
        </div>
        <div
          class="progress-track"
          role="progressbar"
          aria-label="Tiến độ tải"
          aria-valuenow="${percent}"
          aria-valuemin="0"
          aria-valuemax="100"
        >
          <div class="progress-fill p${percent}"></div>
        </div>
        <div class="download-meta">
          <span>${bytes(j.bytes)} / ${bytes(j.size)} · ${percent}%</span
          ><span>${bytes(j.speed)}/s · ${Object.keys(j.sourceBytes).length} nguồn đã gửi</span>
        </div>
        ${j.status === 'QUEUED'
          ? /* HTML */ `<p class="status-message">
              Vị trí hàng đợi: #${j.queuePosition}. Tự tải khi có chỗ trống.
            </p>`
          : ''}
        ${j.status !== 'COMPLETED'
          ? /* HTML */ `<label for="priority-${esc(j.id)}">Độ ưu tiên</label>
              <select
                id="priority-${esc(j.id)}"
                data-action="priority"
                data-id="${esc(j.id)}"
                data-request-key="job:${esc(j.id)}"
                aria-label="Độ ưu tiên của ${esc(j.name)}"
              >
                ${[
                  ['HIGH', 'Cao'],
                  ['NORMAL', 'Bình thường'],
                  ['LOW', 'Thấp'],
                ]
                  .map(
                    ([value, label]) =>
                      /* HTML */ `<option
                        value="${value}"
                        ${value === (j.priority || 'NORMAL') ? 'selected' : ''}
                      >
                        ${label}
                      </option>`,
                  )
                  .join('')}
              </select>`
          : ''}
        <div class="chunk-grid" aria-label="Các phần đã tải">
          ${Array.from(
            { length: count },
            (_, i) =>
              /* HTML */ `<span
                class="chunk ${done.has(i) ? 'done' : ''}"
                title="Phần ${i + 1}: ${done.has(i) ? 'đã xác minh' : 'chưa tải'}"
              ></span>`,
          ).join('')}
        </div>
        ${m.hashes.length > count
          ? /* HTML */ `<p class="section-note">
              Hiển thị ${count} phần đầu / ${m.hashes.length} phần.
            </p>`
          : ''}
        <div class="source-row">
          ${Object.entries(j.sourceBytes)
            .map(
              ([name, n]) =>
                /* HTML */ `<span class="source-chip">↙ ${esc(name)} · ${bytes(n)}</span>`,
            )
            .join('')}
        </div>
        ${j.error ? /* HTML */ `<p class="status-message">${esc(j.error)}</p>` : ''}
        <div class="actions source-row">
          ${['DOWNLOADING', 'WAITING', 'QUEUED'].includes(j.status)
            ? /* HTML */ `<button
                class="small"
                data-action="pause"
                data-request-key="job:${j.id}"
                data-id="${j.id}"
              >
                Ⅱ Tạm dừng
              </button>`
            : j.status !== 'COMPLETED'
              ? /* HTML */ `<button
                  class="small primary"
                  data-action="resume"
                  data-request-key="job:${j.id}"
                  data-id="${j.id}"
                  ${!ready(state) ? 'disabled' : ''}
                >
                  ↻ Tiếp tục tải
                </button>`
              : /* HTML */ `<a href="/api/library/${j.fileId}/content">↓ Lưu file về máy</a
                  ><span class="badge COMPLETED">✓ SHA-256 toàn file hợp lệ</span>`}<button
            class="small ghost"
            data-action="chunk-history"
            data-id="${j.id}"
          >
            Nguồn từng chunk</button
          ><button class="small ghost" data-action="sources" data-id="${j.fileId}">
            Kiểm tra nguồn
          </button>
        </div>
      </article>`;
    })
    .join('');
  return (
    /* HTML */ `<p class="section-note">
      Đang tải ${active}/${state.network?.fileLimit || 2} file · Đang xếp hàng ${queued} file.
      Ưu tiên Cao → Bình thường → Thấp; cùng mức thì file vào hàng trước được tải trước.
      Đổi ưu tiên không ngắt file đang tải. Tạm dừng sẽ bỏ file khỏi hàng chờ.
    </p>` + cards
  );
}
