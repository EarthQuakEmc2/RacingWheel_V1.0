import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/**
 * The public site is https://myracingwheel.site/ at the domain root.
 * `npm run build -- --base=/` matches this default.
 */
export default defineConfig(() => ({
  base: '/',
  plugins: [react()],
  server: {
    fs: {
      allow: ['..'],
    },
    proxy: {
      '/api': 'http://127.0.0.1:8787',
    },
  },
  build: {
    target: 'es2020',
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules/three')) return 'three'
          if (id.includes('node_modules/@react-three')) return 'r3f'
        },
      },
    },
  },
}))
