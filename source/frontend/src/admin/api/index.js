import { authApi } from './auth.js';
import { dashboardApi } from './dashboard.js';
import { filesApi } from './files.js';

export const adminApi = {
  ...authApi,
  ...dashboardApi,
  ...filesApi,
};
