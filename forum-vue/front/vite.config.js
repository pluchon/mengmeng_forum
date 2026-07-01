import path from 'node:path'
import fs from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { live2dAssetsPlugin } from './vite-plugin-live2d-assets.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

function resolveScriptSetupSrc(src, vueFile) {
  if (src.startsWith('@/')) {
    return path.resolve(__dirname, 'src', src.slice(2))
  }
  if (src.startsWith('@scripts/')) {
    return path.resolve(__dirname, 'src/scripts', src.slice('@scripts/'.length))
  }
  return path.resolve(path.dirname(vueFile), src)
}

function scriptSetupSrcPlugin() {
  return {
    name: 'forum-script-setup-src',
    enforce: 'pre',
    async transform(code, id) {
      if (!id.endsWith('.vue') || !code.includes('<script setup src=')) {
        return null
      }

      const scriptSrcPattern = /<script\s+setup\s+src=(["'])([^"']+)\1\s*><\/script>/g
      const matches = [...code.matchAll(scriptSrcPattern)]
      if (!matches.length) {
        return null
      }

      let transformed = code
      for (const match of matches) {
        const sourcePath = resolveScriptSetupSrc(match[2], id)
        const source = await fs.readFile(sourcePath, 'utf-8')
        transformed = transformed.replace(match[0], `<script setup>\n${source}\n</script>`)
      }
      return {
        code: transformed,
        map: null,
      }
    },
  }
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())
  return {
    plugins: [scriptSetupSrcPlugin(), vue(), live2dAssetsPlugin()],
    optimizeDeps: {
      include: ['oh-my-live2d'],
    },
    resolve: {
      // 避免多份 @vue/shared 导致 Element Plus 的 isFunction 绑定错乱
      dedupe: ['vue', '@vue/shared'],
      alias: {
        '@': path.resolve(__dirname, 'src'),
        '@scripts': path.resolve(__dirname, 'src/scripts'),
      },
    },
    server: {
      host: '0.0.0.0',
      port: 5173,
      proxy: {
        // 匹配 API 请求代理到后端（排除 SPA 路由导航）
        '^/(user|board|article|articleDanmaku|message|mail|sms|category|like|file|articleReply|articleSubReply|replyLike|checkin|shop|points|favorite|search|system-message|mascot|lottery|game|vip|ai|notice|captcha|drift-bottle|group-chat)/': {
          target: env.VITE_API_BASE_URL || 'http://localhost:10086',
          changeOrigin: true,
          // 如果请求 Accepts HTML，说明是浏览器导航而非 API 调用，返回 index.html
          bypass(req) {
            if (req.headers.accept?.includes('text/html')) {
              return '/index.html'
            }
          }
        },
        '^/ws/': {
          target: env.VITE_WS_BASE_URL || 'ws://localhost:10086',
          changeOrigin: true,
          ws: true
        }
      }
    }
  }
})
