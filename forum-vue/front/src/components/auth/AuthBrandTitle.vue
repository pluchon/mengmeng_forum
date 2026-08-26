<template>
  <header class="auth-brand-header">
    <img
      v-if="!failed"
      ref="imgRef"
      class="auth-brand-title-img"
      :src="src"
      :alt="alt"
      loading="eager"
      decoding="async"
      @load="handleLoad"
      @error="handleError"
    >
    <h1
      v-else
      class="auth-brand-title auth-brand-title--fallback"
    >{{ fallbackText }}</h1>
  </header>
</template>

<script setup src="./AuthBrandTitle.js"></script>
