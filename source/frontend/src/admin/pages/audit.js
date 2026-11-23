import { UI, esc, time } from '../../shared/ui.js';

export function auditRows(data, limit = 100) {
  return data.audit.length
    ? /* HTML */ `<div class="activity">
        ${data.audit
          .slice(0, limit)
          .map(
            (a) =>
              /* HTML */ `<div class="activity-row">
                <span class="activity-mark">↗</span>
                <div>
                  <strong>${esc(a.actor)}</strong> · ${esc(a.action)}<small class="mono"
                    >${esc(a.target)} · ${time(a.time)}</small
                  >
                </div>
              </div>`,
          )
          .join('')}
      </div>`
    : UI.empty('Chưa có hoạt động.');
}
