import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

// Element Plus
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

// 全局样式
import './assets/styles/global.css'
import './assets/styles/banner.css'

import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'

const app = createApp(App)
const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)

// 注册所有图标（跳过非组件导出，避免生产构建偶发 undefined）
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  if (typeof component === 'object' || typeof component === 'function') {
    app.component(key, component)
  }
}

app.use(pinia)
app.use(router)
app.use(ElementPlus, {
  locale: zhCn,
})

app.mount('#app')
