// @ts-nocheck
// Node built-ins are used only to copy dist/privacy and dist/pricing index files after the build.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'

const community = JSON.parse(
  fs.readFileSync(path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../community.json'), 'utf8'),
)

/** Static hosts can serve these routes from a copied index without a rewrite rule. */
function spaRoutes(): Plugin {
  return {
    name: 'spa-routes',
    closeBundle() {
      const dist = path.resolve(path.dirname(fileURLToPath(import.meta.url)), 'dist')
      for (const route of ['privacy', 'pricing']) {
        const dir = path.join(dist, route)
        fs.mkdirSync(dir, { recursive: true })
        fs.copyFileSync(path.join(dist, 'index.html'), path.join(dir, 'index.html'))
      }
    },
  }
}

/**
 * The public site is https://myracingwheel.site/ at the domain root.
 * `npm run build -- --base=/` matches this default.
 */
export default defineConfig(() => ({
  base: '/',
  define: {
    __TELEGRAM_URL__: JSON.stringify(community.telegram),
  },
  plugins: [react(), spaRoutes()],
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
