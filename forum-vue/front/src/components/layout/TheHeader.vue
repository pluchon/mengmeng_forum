<template>
  <header class="red-header">
    <div class="header-inner">
      <div class="header-left">
        <router-link to="/" class="logo">
          <img src="/picture.ico" alt="logo">
          <span class="logo-text">{{ siteName }}</span>
        </router-link>
      </div>

      <div v-if="route.path !== '/privacy' && route.path !== '/terms'" class="header-center">
        <el-input
          v-model="searchQuery"
          placeholder="搜索感兴趣的内容"
          :prefix-icon="Search"
          class="red-search-input"
          clearable
          @keyup.enter="submitSearch"
        />
      </div>

      <div class="header-right">
        <template v-if="userStore.isLoggedIn">
          <el-tooltip content="站点公告" placement="bottom">
            <el-icon class="icon-btn notice-icon" @click="showAnnouncement"><Notification /></el-icon>
          </el-tooltip>
          <el-tooltip content="游戏中心" placement="bottom">
            <el-icon class="icon-btn game-center-header-icon" @click="$router.push('/games')"><Trophy /></el-icon>
          </el-tooltip>
          <router-link
            v-if="checkinLoaded"
            to="/checkin"
            class="header-moebi"
          >
            <PawCoinIcon class="header-moebi-paw" />
            <span class="header-moebi-num">{{ checkinTotalPoints }}</span>
            <span class="header-moebi-label">萌币</span>
          </router-link>
          <el-button link class="nav-link" @click="$router.push('/checkin')">每日签到</el-button>
          <el-button
            v-if="!isLegalDocPage"
            link
            class="nav-link"
            @click="$router.push('/emoji-shop')"
          >表情商城</el-button>
          <el-button link class="nav-link" @click="$router.push('/points')">我的积分</el-button>
          <el-button link class="nav-link vip-nav-link" @click="$router.push('/vip')">会员中心</el-button>
          <el-button link class="nav-link" @click="$router.push('/lottery')">积分抽奖</el-button>
          <el-button link class="nav-link" @click="goToCreative">创作中心</el-button>

          <div class="header-msg-notify-wrap">
            <el-badge :value="msgUnread" :hidden="msgUnread === 0" class="red-badge">
              <el-icon class="icon-btn" @click="openMessageCenter"><Message /></el-icon>
            </el-badge>
            <MessageIncomingBubble />
          </div>

          <el-dropdown trigger="hover">
            <div class="user-trigger">
              <UserAvatarVip
                :size="32"
                :src="userStore.avatarUrl || defaultAvatar"
                :vip-tier="userStore.vipTier"
                :vip-expire-at="userStore.vipExpireAt"
              />
            </div>
            <template #dropdown>
              <el-dropdown-menu class="red-dropdown">
                <div class="dropdown-user-info">
                  <div class="nick">{{ userStore.nickname }}</div>
                  <div class="id">ID: {{ userStore.id }}</div>
                </div>
                <el-dropdown-item divided @click="$router.push(`/profile/${userStore.id}`)">
                  个人主页
                </el-dropdown-item>
                <el-dropdown-item @click="$router.push('/settings')">
                  设置
                </el-dropdown-item>
                <el-dropdown-item divided class="logout-item" @click="handleLogout">
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>

        <div v-else class="auth-btns">
          <el-button
            v-if="!isLegalDocPage"
            link
            class="nav-link"
            @click="$router.push('/emoji-shop')"
          >表情商城</el-button>
          <el-button type="primary" round @click="$router.push('/sign-in')">
            登录 / 注册
          </el-button>
        </div>
      </div>
    </div>

    <AnnouncementBoard ref="announcementRef" />
  </header>
</template>

<script setup>
import PawCoinIcon from '@/components/common/PawCoinIcon.vue'
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import MessageIncomingBubble from '@/components/layout/MessageIncomingBubble.vue'
import { useTheHeader } from '@scripts/components/layout/TheHeader'
import { SITE_NAME as siteName } from '@/constants/site'

const {
  AnnouncementBoard,
  checkinLoaded,
  checkinTotalPoints,
  Message,
  Notification,
  Search,
  announcementRef,
  defaultAvatar,
  goToCreative,
  openMessageCenter,
  handleLogout,
  isLegalDocPage,
  messageStore,
  msgUnread,
  route,
  searchQuery,
  submitSearch,
  showAnnouncement,
  userStore,
} = useTheHeader()
</script>

<style scoped src="@/assets/styles/layout.css"></style>
