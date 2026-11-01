'use strict';

function explore() {
  if (!catalog.length)
    return UI.empty(
      query
        ? 'Không có file khớp với từ khóa.'
        : 'Chưa có file được duyệt. Hãy chia sẻ file và duyệt tại server.',
    );
  return `<div class="file-grid">${catalog
    .map((f) => {
      const m = f.manifest,
        owned = state.library.some((e) => e.manifest.id === m.id && e.complete),
        extension = m.name.includes('.')
          ? m.name.split('.').pop().slice(0, 5).toUpperCase()
          : 'FILE';
      return `<article class="file-card"><div class="file-meta"><div class="file-symbol">${esc(extension)}</div><span class="badge ${f.sources ? 'ONLINE' : 'OFFLINE'}">${f.sources} nguồn online</span></div><h3>${esc(m.name)}</h3><p>${bytes(m.size)} · ${m.hashes.length} phần · ${esc(f.submittedBy)}</p><div class="hash mono" title="SHA-256">${esc(m.id.slice(0, 32))}…</div><div class="actions"><button class="primary small" data-action="download" data-id="${m.id}" ${owned || !ready() ? 'disabled' : ''}>${owned ? '✓ Đã có file' : '↓ Tải nhiều nguồn'}</button><button class="ghost small" data-action="sources" data-id="${m.id}">Xem nguồn</button></div></article>`;
    })
    .join('')}</div>`;
}
