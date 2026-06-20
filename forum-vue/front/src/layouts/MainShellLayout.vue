<template>
  <div
    class="home-xhs-root"
    :class="{
      'home-xhs-root--bare': isShellBare,
      'home-xhs-root--particle': isShellParticle,
      'home-xhs-root--stream': !isShellBare,
    }"
  >
    <HomeTopBar v-if="!isShellBare" />

    <div class="home-xhs-layout">
      <HomeSidebar v-if="!isShellBare" />

      <section
        class="home-xhs-main-column"
        :class="{ 'home-xhs-main-column--bare': isShellBare }"
      >
        <div class="shell-main-outlet">
          <router-view v-slot="{ Component }">
            <keep-alive include="HomeFeed">
              <component :is="Component" />
            </keep-alive>
          </router-view>
        </div>
        <SiteIcpBar v-if="!isShellBare" />
      </section>
    </div>
    <AnnouncementBoard ref="announcementRef" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AnnouncementBoard from '@/components/common/AnnouncementBoard.vue'
import HomeSidebar from '@/components/layout/HomeSidebar.vue'
import HomeTopBar from '@/components/layout/HomeTopBar.vue'
import SiteIcpBar from '@/components/layout/SiteIcpBar.vue'
import { provideHomeShellContext } from '@/composables/useHomeShell'

const route = useRoute()
const isShellBare = computed(() => route.matched.some((r) => r.meta?.shellBare))
const isShellParticle = computed(() => route.matched.some((r) => r.meta?.shellParticle))

const { announcementRef } = provideHomeShellContext()
</script>

<style src="@/assets/styles/home.css"></style>

<style scoped>
.shell-main-outlet {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
</style>
