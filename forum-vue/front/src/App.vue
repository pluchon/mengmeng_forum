<template>
  <el-config-provider :locale="zhCn">
    <div :class="['app-root', { 'has-header-padding': showGlobalHeader }]">
      <ParticleSea v-if="isAuthPage" />
      <TheHeader v-if="showGlobalHeader" />
      <div class="app-shell">
        <router-view />
        <MascotDock v-if="showMascot" />
        <MessageView />
      </div>
    </div>
  </el-config-provider>
</template>

<script setup>
import { computed } from 'vue'
import { useApp } from '@scripts/App'
import ParticleSea from '@/components/common/ParticleSea.vue'
import MascotDock from '@/components/mascot/MascotDock.vue'
import MessageView from '@/views/MessageView.vue'
const {
  ElConfigProvider,
  TheHeader,
  isAuthPage,
  showGlobalHeader,
  zhCn,
} = useApp()

const showMascot = computed(() => import.meta.env.VITE_ENABLE_MASCOT === 'true')
</script>

<style scoped>
.app-root {
  position: relative;
  min-height: 100vh;
}

.app-shell {
  position: relative;
  z-index: 1;
}
</style>
