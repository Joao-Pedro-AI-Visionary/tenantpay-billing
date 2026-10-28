import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// The dev server proxies API traffic to the backend published by docker compose (host port 18080),
// so the panel always calls relative URLs in dev and in the nginx container.
const apiTarget = process.env.VITE_API_TARGET ?? 'http://localhost:18080'

export default defineConfig(({ mode }) => ({
  plugins: [vue()],
  resolve: {
    alias: {
      // Demo mode (`--mode demo`) bundles the in-browser mock API; every other build gets a stub.
      '#demo-api': fileURLToPath(
        new URL(mode === 'demo' ? './src/mock/demoApi.ts' : './src/mock/demoStub.ts', import.meta.url),
      ),
    },
  },
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: apiTarget, changeOrigin: true },
      '/webhooks': { target: apiTarget, changeOrigin: true },
      '/actuator': { target: apiTarget, changeOrigin: true },
    },
  },
}))
