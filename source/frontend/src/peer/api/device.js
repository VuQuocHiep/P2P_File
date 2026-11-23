import { api } from '../../shared/http.js';

export const deviceApi = {
  status: () => api('/api/status'),
  bandwidth: (body) => api('/api/settings/bandwidth', { method: 'PATCH', body }),
};
