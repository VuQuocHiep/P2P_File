import { api } from '../../shared/http.js';

export const authApi = {
  login: (body) => api('/api/auth/login', { method: 'POST', body }),
  logout: () => api('/api/auth/logout', { method: 'POST', body: {} }),
  me: () => api('/api/auth/me'),
};
