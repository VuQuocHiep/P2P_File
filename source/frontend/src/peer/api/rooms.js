import { api } from '../../shared/http.js';

export const roomsApi = {
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
