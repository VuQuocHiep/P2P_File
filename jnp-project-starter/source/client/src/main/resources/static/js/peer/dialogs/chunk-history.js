'use strict';

async function showChunkHistory(id, page = 0) {
  const job = state.jobs.find((j) => j.id === id);
  if (!job) return;
  const entry = state.library.find((e) => e.manifest.id === job.fileId),
    m = entry.manifest,
    done = new Set(entry.chunks);
  const history = await UI.api(`/api/downloads/${id}/chunks`);
  const missingHistory = entry.chunks.filter((index) => !history[index]).length;
  const sourceTotals = Object.entries(job.sourceBytes || {});
  const historyNotice = missingHistory
    ? `<p class="section-note">${missingHistory} chunk đã tải chưa có thông tin nguồn trong dữ liệu lưu của phiên này. Đây không phải trạng thái online/offline của máy nguồn; bật lại nguồn không bổ sung được lịch sử đã thiếu.</p>${
        sourceTotals.length
          ? `<p class="section-note">Số liệu tổng theo nguồn còn được lưu trên máy nhận. Không dùng số liệu này để suy ra nguồn của từng chunk.</p>${UI.table(
              ['Nguồn đã gửi (tổng hợp)', 'Dữ liệu đã nhận'],
              sourceTotals.map(
                ([name, received]) => `<tr><td>${esc(name)}</td><td>${bytes(received)}</td></tr>`,
              ),
            )}`
          : ''
      }`
    : '';
  const pageSize = 100,
    pages = Math.ceil(m.hashes.length / pageSize);
  page = Math.max(0, Math.min(page, pages - 1));
  const start = page * pageSize,
    end = Math.min(start + pageSize, m.hashes.length);
  $('#sources-content').innerHTML =
    `<p><strong>${esc(job.name)}</strong></p><p class="section-note">Lịch sử nguồn từng chunk đã nhận và xác minh, đọc từ dữ liệu trên máy nhận; không cần máy nguồn online. Số phần hiển thị bắt đầu từ 1.</p>${historyNotice}${UI.table(
      ['Phần', 'Nguồn gửi / tài khoản', 'Địa chỉ TCP', 'Nhận lúc'],
      Array.from({ length: end - start }, (_, offset) => {
        const index = start + offset,
          source = history[index];
        return `<tr><td>${index + 1}</td><td>${!done.has(index) ? 'Chưa tải' : !source ? 'Nguồn chưa được ghi lại trong phiên này' : `${esc(source.name)}<small>${esc(source.owner || 'Không rõ tài khoản')}</small><small class="mono">${esc(source.peerId)}</small>`}</td><td>${source ? `${esc(source.host)}:${source.port}` : '—'}</td><td>${source ? esc(new Date(source.receivedAt).toLocaleString('vi-VN')) : '—'}</td></tr>`;
      }),
    )}<div class="actions"><button class="small ghost" data-action="chunk-history" data-id="${job.id}" data-page="${page - 1}" ${page === 0 ? 'disabled' : ''}>← Trước</button><span>Phần ${start + 1}–${end} / ${m.hashes.length}</span><button class="small ghost" data-action="chunk-history" data-id="${job.id}" data-page="${page + 1}" ${page + 1 >= pages ? 'disabled' : ''}>Sau →</button></div>`;
  if (!$('#sources-dialog').open) $('#sources-dialog').showModal();
}
