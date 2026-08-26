import path from 'node:path'
import fs from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

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
        // 外部 .js 变更时必须触发 .vue 重新 transform，否则 HMR 会继续跑旧内联脚本
        this.addWatchFile(sourcePath)
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
    plugins: [scriptSetupSrcPlugin(), vue()],
    assetsInclude: ['**/*.glb'],
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
        // 开发代理打到 java-cloud-standalone Gateway(10086)，前缀与 gateway routes 对齐
        '^/(user|board|article|articleQuestion|articleDanmaku|message|mail|sms|category|like|file|articleReply|articleSubReply|replyLike|checkin|shop|points|favorite|search|system-message|mascot|lottery|game|vip|starlight|profile|recommend|ai|notice|captcha|group-chat|gallery)/': {
          target: env.VITE_API_BASE_URL || 'http://localhost:10086',
          changeOrigin: true,
          // AI 润色 / 标签 / 配图可达约 5 分钟，避免开发代理先断
          timeout: 600000,
          proxyTimeout: 600000,
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
        },
        // 门户 3D 模型：开发态同源代理，绕过 OSS 未配 CORS 时的 Failed to fetch
        '^/forum_3d/': {
          target: 'https://item-for-picture-with-zhanglihong.oss-cn-shenzhen.aliyuncs.com',
          changeOrigin: true,
          secure: true,
        },
        // 开发态下载原图：绕过浏览器对阿里云 OSS 的 CORS 限制
        '^/oss-dl/': {
          target: 'https://oss-cn-shenzhen.aliyuncs.com',
          changeOrigin: true,
          secure: true,
          rewrite: (p) => p.replace(/^\/oss-dl\/[^/?]+/, ''),
          router: (req) => {
            const raw = req.url || ''
            const m = raw.match(/^\/oss-dl\/([^/?]+)/)
            return m ? `https://${m[1]}` : 'https://oss-cn-shenzhen.aliyuncs.com'
          },
        },
      }
    }
  }
})
