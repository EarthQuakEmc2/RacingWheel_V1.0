import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/**
 * GitHub project Pages is served from /RacingWheel_V1.0/.
 * The dev server keeps base `/` so `npm run dev` opens at http://localhost:5173/.
 * `npm run preview` serves the production build, so that URL includes the base path.
 */
const pagesBase = '/RacingWheel_V1.0/'

export default defineConfig(({ command, isPreview }) => ({
  // Preview serves the files `vite build` already wrote, so it must use the same base.
  base: command === 'build' || isPreview ? pagesBase : '/',
  plugins: [react()],
  server: {
    fs: {
      allow: ['..'],
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
