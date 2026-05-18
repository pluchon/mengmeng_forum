<template>
  <div class="static-hero">
    <div class="hero-content animate-fade-in">
      <h1 class="hero-title">发现你感兴趣的社区</h1>
      <p class="hero-subtitle">
        在这里，分享灵感，记录生活，碰撞思想的火花
      </p>
    </div>
  </div>
</template>

<script setup>
import { useHeroBanner } from '@scripts/components/layout/HeroBanner'

useHeroBanner()
</script>

<style scoped src="@/assets/styles/banner.css"></style>
