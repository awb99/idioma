import { defineConfig } from 'vite'
import squint from 'squint-cljs/vite'

const base = process.env.GITHUB_ACTIONS ? '/idioma/' : '/'

export default defineConfig({
  base,
  optimizeDeps: { include: ['reagami'] },
  plugins: [
    squint(),
    {
      name: 'idioma-base',
      transformIndexHtml(html) {
        return html.replace(
          '<head>',
          `<head>\n    <script>window.__idiomaBase=${JSON.stringify(base)}</script>`
        )
      }
    }
  ]
})