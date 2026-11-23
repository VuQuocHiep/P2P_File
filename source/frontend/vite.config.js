import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const fromFrontend = (path) => fileURLToPath(new URL(path, import.meta.url));

export default defineConfig(({ mode }) => {
  const admin = mode === 'admin';
  const app = admin ? 'admin' : 'peer';
  const backend = admin ? 'server' : 'client';
  const target =
    process.env[admin ? 'VITE_TRACKER_URL' : 'VITE_PEER_URL'] ||
    `http://127.0.0.1:${admin ? 8080 : 8081}`;

  return {
    root: fromFrontend(`./src/${app}/`),
    plugins: [react()],
    server: {
      host: '127.0.0.1',
      port: admin ? 5173 : 5174,
      strictPort: true,
      fs: { allow: [fromFrontend('./')] },
      proxy: {
        '/api': { target, changeOrigin: true },
        '/ws': { target, changeOrigin: true, rewriteWsOrigin: true, ws: true },
      },
    },
    build: {
      outDir: fromFrontend(`../${backend}/target/frontend/`),
      emptyOutDir: true,
      rolldownOptions: {
        output: { entryFileNames: `js/${app}/app.js` },
      },
    },
  };
});
