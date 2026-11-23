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
  downloadPriority: (id, priority) =>
    api(`/api/downloads/${id}/priority`, { method: 'PATCH', body: { priority } }),
  sharing: (id, enabled) =>
    api(`/api/library/${id}/sharing`, { method: 'PATCH', body: { enabled } }),
  sources: (id) => api(`/api/files/${id}/sources`),
  chunkHistory: (id) => api(`/api/downloads/${id}/chunks`),
  lab: (action, body) => api('/api/lab/' + action, { method: 'POST', body }),
  rooms: () => api('/api/rooms'),
  createRoom: (body) => api('/api/rooms', { method: 'POST', body }),
  joinRoom: (id, body) => api(`/api/rooms/${id}/join`, { method: 'POST', body }),
  roomRequests: (id) => api(`/api/rooms/${id}/requests`),
  roomMembers: (id) => api(`/api/rooms/${id}/members`),
  roomDecide: (id, username, body) =>
    api(`/api/rooms/${id}/requests/${encodeURIComponent(username)}`, { method: 'PATCH', body }),
  roomFiles: (id) => api(`/api/rooms/${id}/files`),
  roomUpdate: (id, body) => api(`/api/rooms/${id}`, { method: 'PATCH', body }),
  roomDelete: (id) => api(`/api/rooms/${id}`, { method: 'DELETE' }),
  roomRemoveMember: (id, username) =>
    api(`/api/rooms/${id}/members/${encodeURIComponent(username)}`, { method: 'DELETE' }),
  shareRoomFile: (id, fileId) =>
    api(`/api/rooms/${id}/files/${fileId}`, { method: 'PATCH', body: {} }),
};
