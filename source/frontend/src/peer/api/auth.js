import { api } from '../../shared/http.js';

export const authApi = {
  register: (body) => api('/api/auth/register', { method: 'POST', body }),
  login: (body) => api('/api/auth/login', { method: 'POST', body }),
  logout: () => api('/api/auth/logout', { method: 'POST', body: {} }),
};
