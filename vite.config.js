import { defineConfig } from 'vite'
import squint from 'squint-cljs/vite'

export default defineConfig({
  base: process.env.GITHUB_ACTIONS ? '/idioma/' : '/',
  optimizeDeps: { include: ['reagami'] },
  plugins: [squint()]
})