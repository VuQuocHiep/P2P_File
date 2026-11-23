import { api } from '../../shared/http.js';

export const downloadsApi = {
  download: (id) => api('/api/downloads', { method: 'POST', body: { fileId: id } }),
  downloadAction: (id, action) =>
    api(`/api/downloads/${id}/${action}`, { method: 'POST', body: {} }),
  downloadPriority: (id, priority) =>
    api(`/api/downloads/${id}/priority`, { method: 'PATCH', body: { priority } }),
  chunkHistory: (id) => api(`/api/downloads/${id}/chunks`),
};
