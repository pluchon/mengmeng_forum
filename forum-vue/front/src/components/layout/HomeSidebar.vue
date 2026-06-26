<template>
  <aside class="home-xhs-sidebar home-sidebar-v2">
    <el-scrollbar class="home-xhs-sidebar-scroll">
      <nav class="home-sidebar-nav" aria-label="内容分类">
        <button
          type="button"
          class="home-sidebar-link"
          :class="{ 'is-active': menuActiveKey === 'rec' }"
          @click="selectCategoryMenu('rec')"
        >
          <el-icon><Compass /></el-icon>
          <span>推荐</span>
        </button>
        <button
          type="button"
          class="home-sidebar-link"
          :class="{ 'is-active': menuActiveKey === 'hot' }"
          @click="selectCategoryMenu('hot')"
        >
          <el-icon><TrendCharts /></el-icon>
          <span>热帖榜</span>
        </button>
        <button
          v-for="(cat, idx) in categoriesWithId"
          :key="cat.category.id"
          type="button"
          class="home-sidebar-link"
          :class="{ 'is-active': menuActiveKey === `cat_${cat.category.id}` }"
          @click="selectCategoryMenu(`cat_${cat.category.id}`)"
        >
          <el-icon><component :is="categoryIcon(idx)" /></el-icon>
          <span>{{ cat.category.name }}</span>
        </button>
      </nav>

      <div class="home-sidebar-divider">
        <span>快速入口</span>
      </div>

      <nav class="home-sidebar-nav home-sidebar-nav--quick" aria-label="快速入口">
        <button type="button" class="home-sidebar-link" @click="goProfile">
          <el-icon><User /></el-icon>
          <span>个人主页</span>
        </button>
        <button type="button" class="home-sidebar-link" @click="$router.push('/games')">
          <el-icon><Trophy /></el-icon>
          <span>游戏中心</span>
        </button>
        <button type="button" class="home-sidebar-link" @click="$router.push('/emoji-shop')">
          <el-icon><ShoppingBag /></el-icon>
          <span>积分商城</span>
        </button>
        <button type="button" class="home-sidebar-link" @click="$router.push('/vip')">
          <el-icon><Medal /></el-icon>
          <span>会员中心</span>
        </button>
        <button type="button" class="home-sidebar-link" @click="goToCreative">
          <el-icon><EditPen /></el-icon>
          <span>创作中心</span>
        </button>
        <button type="button" class="home-sidebar-link" @click="goCheckin">
          <el-icon><Calendar /></el-icon>
          <span>签到</span>
        </button>
        <button type="button" class="home-sidebar-link" @click="goLottery">
          <el-icon><Present /></el-icon>
          <span>积分抽奖</span>
        </button>
      </nav>
    </el-scrollbar>

    <div v-if="!userStore.isLoggedIn" class="home-xhs-sidebar-foot">
      <el-button type="primary" round class="home-sidebar-guest-btn" @click="$router.push('/sign-in')">
        登录 / 注册
      </el-button>
    </div>
  </aside>
</template>

<script setup src="@scripts/components/layout/HomeSidebar.js"></script>
