'use strict';

function downloads() {
  if (!state.jobs.length)
    return UI.empty('Chưa có phiên tải. Chọn một file trong Khám phá để bắt đầu.');
  return state.jobs
    .slice()
    .reverse()
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
      return `<article class="download-card"><div class="download-top"><div><h3>${esc(j.name)}</h3><span class="muted">${bytes(j.size)} · ${j.chunks}/${m.hashes.length} phần</span></div>${UI.badge(j.status)}</div><div class="progress-track" role="progressbar" aria-label="Tiến độ tải" aria-valuenow="${percent}" aria-valuemin="0" aria-valuemax="100"><div class="progress-fill p${percent}"></div></div><div class="download-meta"><span>${bytes(j.bytes)} / ${bytes(j.size)} · ${percent}%</span><span>${bytes(j.speed)}/s · ${Object.keys(j.sourceBytes).length} nguồn đã gửi</span></div><div class="chunk-grid" aria-label="Các phần đã tải">${Array.from({ length: count }, (_, i) => `<span class="chunk ${done.has(i) ? 'done' : ''}" title="Phần ${i + 1}: ${done.has(i) ? 'đã xác minh' : 'chưa tải'}"></span>`).join('')}</div>${m.hashes.length > count ? `<p class="section-note">Hiển thị ${count} phần đầu / ${m.hashes.length} phần.</p>` : ''}<div class="source-row">${Object.entries(
        j.sourceBytes,
      )
        .map(([name, n]) => `<span class="source-chip">↙ ${esc(name)} · ${bytes(n)}</span>`)
        .join(
          '',
        )}</div>${j.error ? `<p class="status-message">${esc(j.error)}</p>` : ''}<div class="actions source-row">${['DOWNLOADING', 'WAITING'].includes(j.status) ? `<button class="small" data-action="pause" data-id="${j.id}">Ⅱ Tạm dừng</button>` : j.status !== 'COMPLETED' ? `<button class="small primary" data-action="resume" data-id="${j.id}" ${!ready() ? 'disabled' : ''}>↻ Tiếp tục tải</button>` : `<a href="/api/library/${j.fileId}/content">↓ Lưu file về máy</a><span class="badge COMPLETED">✓ SHA-256 toàn file hợp lệ</span>`}<button class="small ghost" data-action="chunk-history" data-id="${j.id}">Nguồn từng chunk</button><button class="small ghost" data-action="sources" data-id="${j.fileId}">Kiểm tra nguồn</button></div></article>`;
    })
    .join('');
}
