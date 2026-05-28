import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { live2dAssetsPlugin } from './vite-plugin-live2d-assets.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())
  return {
    plugins: [vue(), live2dAssetsPlugin()],
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
        '^/(user|board|article|message|mail|sms|category|like|file|articleReply|articleSubReply|checkin|shop|points|favorite|search|system-message|mascot|lottery|vip|ai|notice|captcha)/': {
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
