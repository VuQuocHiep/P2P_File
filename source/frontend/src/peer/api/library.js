import { api } from '../../shared/http.js';

export const libraryApi = {
  catalog: (query, signal) => api('/api/catalog?q=' + encodeURIComponent(query), { signal }),
  upload: (body) => api('/api/library', { method: 'POST', body }),
  sharing: (id, enabled) =>
    api(`/api/library/${id}/sharing`, { method: 'PATCH', body: { enabled } }),
  sources: (id) => api(`/api/files/${id}/sources`),
};
