import { defineConfig } from 'vite'
import squint from 'squint-cljs/vite'

export default defineConfig({
  optimizeDeps: { include: ['reagami'] },
  plugins: [squint()]
})