<template>
  <header class="home-shell-header">
    <div class="home-shell-search">
      <div class="home-search-inner" :class="{ 'home-search-inner--ai-rag': aiSearchMode }">
        <div class="home-search-bar home-search-bar--stream">
          <el-input
            v-model="searchQuery"
            :maxlength="100"
            :placeholder="searchInputPlaceholder"
            class="home-xhs-search home-xhs-search--stream"
            size="large"
            :clearable="false"
            @keyup.enter="submitSearch"
          >
            <template #prefix>
              <div class="home-search-prefix-inner home-search-prefix-inner--stream">
                <el-icon class="home-search-prefix-icon"><Search /></el-icon>
                <span
                  role="button"
                  tabindex="0"
                  class="home-search-mode-text"
                  :class="{ 'is-ai': aiSearchMode }"
                  @click.stop="toggleAiSearchMode"
                  @keydown.enter.prevent="toggleAiSearchMode"
                >{{ aiSearchMode ? 'AI' : '综合' }}</span>
              </div>
            </template>
          </el-input>
        </div>
      </div>
    </div>

    <div class="home-shell-tools">
          <button
            v-if="userStore.isLoggedIn"
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
      <button type="button" class="home-shell-icon-btn" aria-label="设置" @click="goSettings">
        <el-icon><Setting /></el-icon>
      </button>
      <span class="home-shell-tools-divider" aria-hidden="true" />
      <button
        v-if="userStore.isLoggedIn"
        type="button"
        class="home-shell-points"
        @click="goPoints"
      >
        <span>萌币 {{ pointsBalance }}</span>
      </button>
      <template v-if="userStore.isLoggedIn">
        <el-dropdown trigger="click" placement="bottom-end">
          <button type="button" class="home-shell-avatar-btn" aria-label="个人菜单">
            <UserAvatarVip
              :size="36"
              :src="userStore.avatarUrl || defaultAvatar"            />
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </template>
      <template v-else>
        <el-button class="home-shell-auth-entry" round @click="$router.push('/sign-in')">
          登录 / 注册
        </el-button>
      </template>
    </div>

    <VipSubscribeDialog v-model="vipDialogVisible" />
  </header>
</template>

<script setup src="@scripts/components/layout/HomeTopBar.js"></script>
