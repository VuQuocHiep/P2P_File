import { api } from '/assets/js/http.js';

export const peerApi = {
  bandwidth: (body) => api('/api/settings/bandwidth', { method: 'PATCH', body }),
  status: () => api('/api/status'),
  catalog: (query, signal) => api('/api/catalog?q=' + encodeURIComponent(query), { signal }),
  register: (body) => api('/api/auth/register', { method: 'POST', body }),
  login: (body) => api('/api/auth/login', { method: 'POST', body }),
  logout: () => api('/api/auth/logout', { method: 'POST', body: {} }),
  upload: (body) => api('/api/library', { method: 'POST', body }),
  download: (id) => api('/api/downloads', { method: 'POST', body: { fileId: id } }),
  downloadAction: (id, action) =>
    api(`/api/downloads/${id}/${action}`, { method: 'POST', body: {} }),
  sharing: (id, enabled) =>
    api(`/api/library/${id}/sharing`, { method: 'PATCH', body: { enabled } }),
  scanConsent: (id, enabled) =>
    api(`/api/library/${id}/scan-consent`, { method: 'PATCH', body: { enabled } }),
  sources: (id) => api(`/api/files/${id}/sources`),
  chunkHistory: (id) => api(`/api/downloads/${id}/chunks`),
  lab: (action, body) => api('/api/lab/' + action, { method: 'POST', body }),
};
