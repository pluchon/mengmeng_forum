<template>
  <aside class="home-xhs-sidebar home-sidebar-v2">
    <router-link to="/" class="home-sidebar-brand-link" :aria-label="siteName">
      <img class="home-sidebar-brand-logo" src="/login_big.png" alt="" />
      <img
        v-if="!brandTitleFailed"
        class="home-sidebar-brand-title"
        :src="loginTitleUrl"
        :alt="siteName"
        @error="brandTitleFailed = true"
      />
      <span v-else class="home-sidebar-brand-fallback">{{ siteName }}</span>
    </router-link>

    <div class="home-xhs-sidebar-scroll">
      <div class="home-sidebar-divider home-sidebar-divider--first">
        <span>内容发现</span>
      </div>

      <nav class="home-sidebar-nav" aria-label="内容发现">
        <button
          type="button"
          class="home-sidebar-link"
          :class="{ 'is-active': sidebarMenuActive === 'home' }"
          @click="selectCategoryMenu('home')"
        >
          <img class="home-sidebar-icon" :src="iconHome" alt="" />
          <span>社区首页</span>
        </button>
        <button
          type="button"
          class="home-sidebar-link"
          :class="{ 'is-active': sidebarMenuActive === 'rec' }"
          @click="selectCategoryMenu('rec')"
        >
          <img class="home-sidebar-icon" :src="iconRecommend" alt="" />
          <span>为你推荐</span>
        </button>
      </nav>

      <div class="home-sidebar-divider">
        <span>社区入口</span>
      </div>

      <nav class="home-sidebar-nav home-sidebar-nav--quick" aria-label="社区入口">
        <button type="button" class="home-sidebar-link" @click="openMessageCenter">
          <el-badge :value="msgUnread" :hidden="msgUnread === 0" class="home-sidebar-message-badge">
            <img class="home-sidebar-icon" :src="iconMessages" alt="" />
          </el-badge>
          <span>消息中心</span>
        </button>
        <button
          type="button"
          class="home-sidebar-link"
          :class="{ 'is-active': sidebarMenuActive === 'profile' }"
          @click="goProfile"
        >
          <img class="home-sidebar-icon" :src="iconProfile" alt="" />
          <span>个人主页</span>
        </button>
        <button
          type="button"
          class="home-sidebar-link"
          :class="{ 'is-active': sidebarMenuActive === 'game' }"
          @click="$router.push('/games')"
        >
          <img class="home-sidebar-icon" :src="iconGameCenter" alt="" />
          <span>游戏中心</span>
        </button>
        <button
          type="button"
          class="home-sidebar-link"
          :class="{ 'is-active': sidebarMenuActive === 'music' }"
          @click="goMusicHall"
        >
          <img class="home-sidebar-icon" :src="iconMusicHall" alt="" />
          <span>音乐大厅</span>
        </button>
      </nav>

      <div class="home-sidebar-divider">
        <span>日常与创作</span>
      </div>

      <nav class="home-sidebar-nav home-sidebar-nav--quick" aria-label="日常与创作">
        <button
          type="button"
          class="home-sidebar-link"
          :class="{ 'is-active': sidebarMenuActive === 'creative' }"
          @click="goToCreative"
        >
          <img class="home-sidebar-icon" :src="iconCreationCenter" alt="" />
          <span>创作中心</span>
        </button>
        <button
          type="button"
          class="home-sidebar-link"
          :class="{ 'is-active': sidebarMenuActive === 'checkin' }"
          @click="goCheckin"
        >
          <img class="home-sidebar-icon" :src="iconCheckin" alt="" />
          <span>签到中心</span>
        </button>
      </nav>

      <div class="home-sidebar-divider">
        <span>会员与福利</span>
      </div>

      <nav class="home-sidebar-nav home-sidebar-nav--quick" aria-label="会员与福利">
        <button
          type="button"
          class="home-sidebar-link"
          :class="{ 'is-active': sidebarMenuActive === 'emoji' }"
          @click="$router.push('/emoji-shop')"
        >
          <img class="home-sidebar-icon" :src="iconPointsShop" alt="" />
          <span>表情商城</span>
        </button>
        <button
          type="button"
          class="home-sidebar-link"
          :class="{ 'is-active': sidebarMenuActive === 'lottery' }"
          @click="goLottery"
        >
          <img class="home-sidebar-icon" :src="iconPointsLottery" alt="" />
          <span>积分抽奖</span>
        </button>
      </nav>
    </div>

    <div class="home-sidebar-footer">
      <button
        type="button"
        class="home-sidebar-hot-entry"
        aria-label="查看热帖榜"
        @click="openHotRankingDialog"
      >
        <span class="home-sidebar-hot-entry-title">
          <el-icon class="home-sidebar-hot-flame"><TrendCharts /></el-icon>
          <span>热帖榜</span>
        </span>
        <span class="home-sidebar-hot-entry-action">查看</span>
      </button>
    </div>

    <HotRankingDialog v-model="hotRankingDialogVisible" />
  </aside>
</template>

<script setup src="@scripts/components/layout/HomeSidebar.js"></script>
