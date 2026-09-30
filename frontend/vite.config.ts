import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The dev server proxies /api to the Spring Boot backend on port 8081.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
});
