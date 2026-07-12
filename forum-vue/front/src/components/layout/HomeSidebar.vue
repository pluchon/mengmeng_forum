<template>
  <aside class="home-xhs-sidebar home-sidebar-v2">
    <el-scrollbar class="home-xhs-sidebar-scroll">
      <nav class="home-sidebar-nav" aria-label="内容发现">
        <button
          type="button"
          class="home-sidebar-link"
          :class="{ 'is-active': menuActiveKey === 'home' }"
          @click="selectCategoryMenu('home')"
        >
          <el-icon><HomeFilled /></el-icon>
          <span>首页</span>
        </button>
        <button
          type="button"
          class="home-sidebar-link"
          :class="{ 'is-active': menuActiveKey === 'rec' }"
          @click="selectCategoryMenu('rec')"
        >
          <el-icon><Compass /></el-icon>
          <span>为你推荐</span>
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
        <button type="button" class="home-sidebar-link" @click="$router.push('/drift-bottle')">
          <el-icon>
            <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
              <path fill="currentColor" d="M848 830.6L619.5 352H608V160h64c8.8 0 16-7.2 16-16v-64c0-8.8-7.2-16-16-16H352c-8.8 0-16 7.2-16 16v64c0 8.8 7.2 16 16 16h64v192h-11.5L176 830.6c-21.7 45.4 11.4 97.4 61.6 97.4h548.8c50.2 0 83.3-52 61.6-97.4zM472 160h80v196h-80V160zm-62 260h204l114.7 240H295.3L410 420zm385.2 436H230.8L269 732h486l38.2 124z"></path>
            </svg>
          </el-icon>
          <span>漂流瓶</span>
        </button>
        <button type="button" class="home-sidebar-link" @click="$router.push('/growth-center')">
          <el-icon><Opportunity /></el-icon>
          <span>成长中心</span>
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

  </aside>
</template>

<script setup src="@scripts/components/layout/HomeSidebar.js"></script>
