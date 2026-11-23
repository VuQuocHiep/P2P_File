import { authApi } from './auth.js';
import { deviceApi } from './device.js';
import { libraryApi } from './library.js';
import { downloadsApi } from './downloads.js';
import { labApi } from './lab.js';
import { roomsApi } from './rooms.js';

export const peerApi = {
  ...authApi,
  ...deviceApi,
  ...libraryApi,
  ...downloadsApi,
  ...labApi,
  ...roomsApi,
};
