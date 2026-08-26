<template>
  <router-view v-if="isPortalRoute" />
  <div
    v-else
    class="home-xhs-root"
    :class="{
      'home-xhs-root--bare': isShellBare,
      'home-xhs-root--particle': isShellParticle,
      'home-xhs-root--stream': !isShellBare,
    }"
  >
    <div class="home-xhs-layout">
      <HomeSidebar v-if="!isShellBare" />

      <section
        class="home-xhs-main-column"
        :class="{ 'home-xhs-main-column--bare': isShellBare }"
      >
        <HomeTopBar v-if="!isShellBare && !hideShellTopBar" />
        <div class="shell-main-outlet">
          <router-view v-slot="{ Component }">
            <keep-alive :max="12">
              <component :is="resolveShellLayer(Component)" />
            </keep-alive>
            <component v-if="isArticleDetailRoute" :is="Component" />
          </router-view>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup src="@scripts/layouts/MainShellLayout.js"></script>

<style src="@/assets/styles/home.css"></style>
<style scoped lang="scss" src="@/layouts/MainShellLayout.scss"></style>
