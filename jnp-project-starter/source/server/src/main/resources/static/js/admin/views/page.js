'use strict';

function render() {
  if (!data) return;
  document.querySelector('#breadcrumb').textContent = titles[current];
  document
    .querySelectorAll('[data-tab]')
    .forEach((b) => b.classList.toggle('active', b.dataset.tab === current));
  let content =
    current === 'overview'
      ? overview()
      : `<section class="panel">${{ accounts: accountTable, peers: peerTable, files: fileTable, transfers: transferTable, audit: auditRows }[current]()}</section>`;
  document.querySelector('#content').innerHTML =
    `<div class="page-heading"><div><div class="eyebrow">PEERMESH CONTROL CENTER</div><h1>${titles[current]}</h1><p>${notes[current]}</p></div><span class="badge">LAN NETWORK</span></div>${content}`;
}
