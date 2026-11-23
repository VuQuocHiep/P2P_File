import { api } from '../../shared/http.js';

export const filesApi = {
  deleteFile: (id) => api(`/api/admin/files/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  scan: (id, method) => api(`/api/admin/files/${encodeURIComponent(id)}/scan-request`, { method }),
};
