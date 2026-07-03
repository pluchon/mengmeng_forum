<template>
  <button
    type="button"
    class="home-sidebar-link theme-toggle-entry"
    :aria-label="`当前${themeStore.modeText}模式，点击切换主题`"
    @click="themeStore.toggleTheme"
  >
    <el-icon>
      <Moon v-if="themeStore.isDark" />
      <Sunny v-else />
    </el-icon>
    <span>主题模式</span>
    <em class="theme-toggle-entry__mode">{{ themeStore.modeText }}</em>
  </button>
</template>

<script setup src="@scripts/components/theme/ThemeToggle.js"></script>

<style scoped src="./ThemeToggle.css"></style>
