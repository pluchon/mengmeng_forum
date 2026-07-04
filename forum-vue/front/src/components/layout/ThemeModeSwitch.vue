<template>
  <button
    ref="switchRef"
    type="button"
    class="theme-mode-switch"
    :class="{
      'is-dark': themeStore.isDark,
      'is-motion-light': motionMode === 'light',
      'is-motion-dark': motionMode === 'dark',
      'is-transitioning': isTransitioning,
    }"
    :aria-label="themeStore.isDark ? '切换浅色模式' : '切换深色模式'"
    :disabled="isTransitioning"
    @click="handleToggle"
  >
    <span class="theme-mode-switch__thumb">
      <span class="theme-mode-switch__sun" aria-hidden="true">
        <svg class="theme-mode-switch__icon" viewBox="0 0 24 24" focusable="false">
          <circle cx="12" cy="12" r="4.25" />
          <path d="M12 2.8v2.4M12 18.8v2.4M4.5 4.5l1.7 1.7M17.8 17.8l1.7 1.7M2.8 12h2.4M18.8 12h2.4M4.5 19.5l1.7-1.7M17.8 6.2l1.7-1.7" />
        </svg>
      </span>
      <span class="theme-mode-switch__moon" aria-hidden="true">
        <svg class="theme-mode-switch__icon theme-mode-switch__icon--moon" viewBox="0 0 24 24" focusable="false">
          <circle class="theme-mode-switch__moon-full" cx="12" cy="12" r="8" />
          <path
            class="theme-mode-switch__moon-crescent"
            d="M20.2 14.4A8.3 8.3 0 0 1 9.6 3.8A8.6 8.6 0 1 0 20.2 14.4Z"
          />
        </svg>
      </span>
    </span>
  </button>
</template>

<script setup src="@scripts/components/layout/ThemeModeSwitch.js"></script>
