import { api } from '../../shared/http.js';

export const labApi = {
  lab: (action, body) => api('/api/lab/' + action, { method: 'POST', body }),
};
