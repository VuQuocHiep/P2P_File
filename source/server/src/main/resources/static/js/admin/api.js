import { api } from '/assets/js/http.js';

export const adminApi = {
  login: (body) => api('/api/auth/login', { method: 'POST', body }),
  logout: () => api('/api/auth/logout', { method: 'POST', body: {} }),
  me: () => api('/api/auth/me'),
  dashboard: () => api('/api/admin/dashboard'),
  moderate: (kind, id, status) =>
    api(`/api/admin/${kind}/${encodeURIComponent(id)}`, { method: 'PATCH', body: { status } }),
  deleteFile: (id) => api(`/api/admin/files/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  scan: (id, method) => api(`/api/admin/files/${encodeURIComponent(id)}/scan-request`, { method }),
};
