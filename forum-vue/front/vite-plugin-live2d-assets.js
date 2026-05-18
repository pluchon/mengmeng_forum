import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const MIME = {
  '.json': 'application/json; charset=utf-8',
  '.moc3': 'application/octet-stream',
  '.moc': 'application/octet-stream',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
  '.mtn': 'application/octet-stream',
  '.motion3': 'application/json; charset=utf-8',
  '.exp3': 'application/json; charset=utf-8',
  '.physics3': 'application/json; charset=utf-8',
  '.cdi3': 'application/json; charset=utf-8',
  '.wav': 'audio/wav',
  '.mp3': 'audio/mpeg',
}

/**
 * 将仓库根下 live2d/live2d-master 映射到 /live2d-assets，供 Cubism4 model3.json 及贴图加载。
 * 生产环境请在网关/Nginx 配置同等静态别名。
 */
export function live2dAssetsPlugin() {
  const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../live2d/live2d-master')

  function mount(server) {
    server.middlewares.use('/live2d-assets', (req, res, next) => {
      try {
        let u = req.url?.split('?')[0] || ''
        if (u.startsWith('/')) u = u.slice(1)
        if (!u) {
          res.statusCode = 404
          res.end()
          return
        }
        const rel = decodeURIComponent(u)
        const normalized = path.normalize(rel).replace(/^(\.\.(\/|\\|$))+/, '')
        const full = path.join(root, normalized)
        if (!full.startsWith(root)) {
          res.statusCode = 403
          res.end()
          return
        }
        fs.stat(full, (err, st) => {
          if (err || !st.isFile()) {
            res.statusCode = 404
            res.end()
            return
          }
          const ext = path.extname(full).toLowerCase()
          res.setHeader('Content-Type', MIME[ext] || 'application/octet-stream')
          fs.createReadStream(full).pipe(res)
        })
      }
      catch (e) {
        next(e)
      }
    })
  }

  return {
    name: 'live2d-assets',
    configureServer: mount,
    configurePreviewServer: mount,
  }
}
