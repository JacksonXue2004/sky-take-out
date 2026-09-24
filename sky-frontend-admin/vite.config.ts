import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath, URL } from 'node:url'

// Serve as static files under nginx html/sky -> use relative asset base.
// Dev proxy mirrors the production nginx.conf so `npm run dev` behaves identically:
//   /api/*  -> http://localhost:8080/admin/*   (nginx: location /api/ -> /admin/)
//   /ws/*   -> ws://localhost:8080/ws/*         (nginx: location /ws/ , websocket upgrade)
export default defineConfig({
  base: './',
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, '/admin'),
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
        changeOrigin: true,
      },
    },
  },
})
