<template>
  <div class="music-hall-page animate-fade-in">
    <header class="music-hall-page__topbar">
      <div class="music-hall-page__top-left">
        <div class="music-hall-page__brand">
          <span class="music-hall-page__brand-icon" aria-hidden="true">
            <img class="music-hall-page__brand-logo" :src="iconMusicHall" alt="">
          </span>
          <span class="music-hall-page__brand-text">音乐大厅</span>
        </div>
        <nav class="music-hall-page__nav" aria-label="音乐大厅主导航">
          <router-link
            to="/music-hall"
            class="music-hall-page__nav-link"
            :class="{ 'is-active': hallTab === 'discover' }"
          >
            发现
          </router-link>
          <router-link
            to="/music-hall/mine"
            class="music-hall-page__nav-link"
            :class="{ 'is-active': hallTab === 'mine' }"
          >
            我的音乐
          </router-link>
        </nav>
      </div>
      <div class="music-hall-page__top-right">
        <template v-if="userStore.isLoggedIn">
          <button
            type="button"
            class="vip-status-pill"
            :class="vipStatusPillClass"
            :aria-label="vipStatusLabel"
            @click="openVipPurchase"
          >
            <svg
              class="vip-status-pill__icon"
              viewBox="0 0 14 14"
              xmlns="http://www.w3.org/2000/svg"
              aria-hidden="true"
            >
              <path :d="vipStatusIcon.d" :fill="vipStatusIcon.fill" />
            </svg>
            <span>{{ vipStatusLabel }}</span>
          </button>
          <button type="button" class="music-hall-page__avatar-btn" aria-label="个人主页" @click="goProfile">
            <UserAvatarVip
              :size="32"
              :src="userStore.avatarUrl || defaultAvatar"
              :vip-tier="Number(userStore.vipTier) || 0"
              :vip-expire-at="userStore.vipExpireAt"
              :show-vip-ring="false"
            />
          </button>
        </template>
        <template v-else>
          <el-button class="music-hall-page__auth-btn" round @click="$router.push('/sign-in')">
            登录 / 注册
          </el-button>
        </template>
      </div>
    </header>

    <MusicHall embedded :hall-tab="hallTab" />

    <VipSubscribeDialog v-model="vipDialogVisible" />
  </div>
</template>

<script setup src="@scripts/views/MusicHallPage.js"></script>
<style scoped lang="scss" src="@/views/MusicHallPage.scss"></style>
