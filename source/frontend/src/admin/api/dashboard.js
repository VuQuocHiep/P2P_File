import { api } from '../../shared/http.js';

export const dashboardApi = {
  dashboard: () => api('/api/admin/dashboard'),
  moderate: (kind, id, status) =>
    api(`/api/admin/${kind}/${encodeURIComponent(id)}`, { method: 'PATCH', body: { status } }),
};
