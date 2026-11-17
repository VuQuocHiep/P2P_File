import { $, updateHTML } from '/assets/js/dom.js';
import { titles, notes } from '../state.js';
import { overview } from './overview.js';
import { accountTable, peerTable, fileTable, transferTable, auditRows } from './tables.js';

export function createAdminRenderer(model) {
  let lastTab = '';
  return function render() {
    const { current, data } = model;
    if (!model.signedIn || !data) return;
    $('#breadcrumb').textContent = titles[current];
    document.querySelectorAll('[data-tab]').forEach((button) => {
      button.classList.toggle('active', button.dataset.tab === current);
    });
    const views = {
      accounts: () => accountTable(data),
      peers: () => peerTable(data.peers),
      files: () => fileTable(data, model.malware),
      transfers: () => transferTable(data),
      audit: () => auditRows(data),
    };
    const content =
      current === 'overview'
        ? overview(data)
        : /* HTML */ `<section class="panel">${views[current]()}</section>`;
    updateHTML(
      $('#content'),
      /* HTML */ `<div class="page-heading">
          <div>
            <div class="eyebrow">PEERMESH CONTROL CENTER</div>
            <h1>${titles[current]}</h1>
            <p>${notes[current]}</p>
          </div>
          <span class="badge">LAN NETWORK</span>
        </div>
        ${content}`,
      lastTab !== current,
    );
    lastTab = current;
  };
}
