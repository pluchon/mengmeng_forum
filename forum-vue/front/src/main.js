import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'

import App from './App.vue'
import router from './router'

import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import './assets/styles/global.css'
import './assets/styles/banner.css'
import './components/dialog/dialog-tokens.scss'

import { useUserStore } from './stores/user'
import { usePointsWalletStore } from './stores/pointsWallet'

const app = createApp(App)
const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)

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

router.isReady().then(() => {
  const userStore = useUserStore()
  if (userStore.isLoggedIn) {
    void usePointsWalletStore().refresh()
  }
})

app.mount('#app')
