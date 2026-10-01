import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

// The dev server proxies /api to the Spring Boot backend on port 8081.
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      // The manifest is a static file in public/manifest.webmanifest (linked from
      // index.html) so it resolves identically in dev and prod. The plugin here
      // only manages the service worker.
      manifest: false,
      includeAssets: ['favicon-32x32.png', 'apple-touch-icon.png', 'icon.svg'],
      workbox: {
        // Cache the app shell; never cache API calls (they need fresh auth/data).
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/api/],
        runtimeCaching: [
          {
            urlPattern: ({ url }) => url.pathname.startsWith('/api'),
            handler: 'NetworkOnly',
          },
          {
            urlPattern: ({ url }) => url.origin === 'https://fonts.googleapis.com',
            handler: 'StaleWhileRevalidate',
            options: { cacheName: 'google-fonts-stylesheets' },
          },
          {
            urlPattern: ({ url }) => url.origin === 'https://fonts.gstatic.com',
            handler: 'CacheFirst',
            options: {
              cacheName: 'google-fonts-webfonts',
              expiration: { maxEntries: 20, maxAgeSeconds: 60 * 60 * 24 * 365 },
            },
          },
        ],
      },
      devOptions: {
        // Keep the service worker OFF in `npm run dev`. In development it only
        // gets in the way: it intercepts requests (API 401s show up as "from
        // service worker") and serves stale cached JS. The SW is still built and
        // active in the production build, where it belongs.
        enabled: false,
        type: 'module',
      },
    }),
  ],
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
