<template>
  <div
    class="home-xhs-root"
    :class="{
      'home-xhs-root--bare': isShellBare,
      'home-xhs-root--particle': isShellParticle,
    }"
  >
    <div class="home-xhs-layout">
      <aside v-if="!isShellBare" class="home-xhs-sidebar">
        <div class="home-sidebar-brand">{{ siteName }}</div>
        <el-scrollbar class="home-xhs-sidebar-scroll">
          <el-menu
            :key="'cat-' + $route.path + '-' + menuActiveKey"
            :default-active="sidebarMenuActive"
            class="home-xhs-cat-menu"
            @select="selectCategoryMenu"
          >
            <el-menu-item index="rec">
              <span>推荐</span>
            </el-menu-item>
            <el-menu-item index="hot">
              <span>热帖榜</span>
            </el-menu-item>
            <el-menu-item
              v-for="cat in categoriesWithId"
              :key="cat.category.id"
              :index="'cat_' + cat.category.id"
            >
              {{ cat.category.name }}
            </el-menu-item>
          </el-menu>
        </el-scrollbar>

        <div class="home-xhs-sidebar-foot">
          <template v-if="userStore.isLoggedIn">
            <el-dropdown trigger="click" placement="top-start">
              <div class="home-user-block" role="button" tabindex="0">
                <UserAvatarVip
                  :size="44"
                  :src="userStore.avatarUrl || defaultAvatar"
                  :vip-tier="Number(userStore.vipTier) || 0"
                  :vip-expire-at="userStore.vipExpireAt"
                />
                <div class="home-user-meta">
                  <div class="home-user-name-row">
                    <span class="home-nickname">{{ userStore.nickname || '用户' }}</span>
                    <img
                      v-if="effectiveVipTier > 0"
                      :src="vipBadgeSrc"
                      class="home-vip-badge-img"
                      alt="VIP"
                    />
                  </div>
                </div>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="$router.push(`/profile/${userStore.id}`)">
                    个人主页
                  </el-dropdown-item>
                  <el-dropdown-item @click="$router.push('/settings')">设置</el-dropdown-item>
                  <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <div v-else class="home-sidebar-guest">
            <el-button type="primary" round class="home-sidebar-guest-btn" @click="$router.push('/sign-in')">
              登录 / 注册
            </el-button>
          </div>
        </div>
      </aside>

      <section
        class="home-xhs-main-column"
        :class="{ 'home-xhs-main-column--bare': isShellBare }"
      >
        <div v-if="!isShellBare" class="home-main-top">
          <div class="home-search-wrap">
            <div
              class="home-search-inner"
              :class="{ 'home-search-inner--ai-rag': aiSearchMode }"
            >
            <div class="home-search-bar">
              <el-input
                v-model="searchQuery"
                :placeholder="searchInputPlaceholder"
                class="home-xhs-search"
                size="large"
                :clearable="false"
                @keyup.enter="submitSearch"
              >
                <template #prefix>
                  <div class="home-search-prefix-inner">
                    <el-icon class="home-search-prefix-icon"><Search /></el-icon>
                    <span
                      role="button"
                      tabindex="0"
                      class="home-search-mode-text"
                      :class="{ 'is-ai': aiSearchMode }"
                      @click.stop="toggleAiSearchMode"
                      @keydown.enter.prevent="toggleAiSearchMode"
                    >{{ aiSearchMode ? 'AI增强搜索' : '普通搜索' }}</span>
                  </div>
                </template>
              </el-input>
              <button type="button" class="home-search-submit-btn" @click="submitSearch">
                搜索
              </button>
            </div>
            </div>
          </div>
          <div class="home-main-tools">
            <el-tooltip :content="mascotUi.pointerPassThrough ? '关闭看板娘鼠标穿透' : '开启看板娘鼠标穿透'" placement="bottom">
              <el-button
                text
                class="home-tool-btn home-mascot-pass-btn"
                :class="{ 'is-active': mascotUi.pointerPassThrough }"
                @click="mascotUi.togglePointerPassThrough()"
              >
                <el-icon><Pointer /></el-icon>
                <span class="home-tool-btn__text">点击看板娘鼠标穿透</span>
              </el-button>
            </el-tooltip>
            <template v-if="userStore.isLoggedIn">
              <el-button text class="home-tool-btn home-game-center-btn" @click="$router.push('/games')">
                <el-icon><Trophy /></el-icon>
                <span class="home-tool-btn__text">游戏中心</span>
              </el-button>
              <el-button text class="home-tool-btn" @click="$router.push('/checkin')">
                <el-icon><Calendar /></el-icon>
                <span class="home-tool-btn__text">每日签到</span>
              </el-button>
              <el-button text class="home-tool-btn" @click="$router.push('/emoji-shop')">
                <el-icon><Goods /></el-icon>
                <span class="home-tool-btn__text">表情商城</span>
              </el-button>
              <el-button text class="home-tool-btn" @click="$router.push('/lottery')">
                <el-icon><Present /></el-icon>
                <span class="home-tool-btn__text">积分抽奖</span>
              </el-button>
              <el-button text class="home-tool-btn" @click="$router.push('/vip')">
                <el-icon><Medal /></el-icon>
                <span class="home-tool-btn__text">会员中心</span>
              </el-button>
              <el-button text class="home-tool-btn" @click="goToCreative">
                <el-icon><EditPen /></el-icon>
                <span class="home-tool-btn__text">创作中心</span>
              </el-button>
              <div class="home-msg-notify-wrap">
                <el-badge :value="msgUnread" :hidden="msgUnread === 0" class="home-msg-badge">
                  <el-button circle class="home-icon-btn" aria-label="站内信" @click="messageCenterUi.open()">
                    <el-icon><Message /></el-icon>
                  </el-button>
                </el-badge>
                <MessageIncomingBubble />
              </div>
              <el-tag
                round
                effect="plain"
                class="home-points-tag"
                role="button"
                tabindex="0"
                @click="$router.push('/points')"
                @keydown.enter="$router.push('/points')"
              >
                <el-icon class="home-points-tag-icon"><Coin /></el-icon>
                积分 {{ pointsBalance }}
              </el-tag>
              <el-tooltip content="公告与活动中心" placement="bottom">
                <el-button circle class="home-icon-btn" aria-label="公告与活动" @click="showAnnouncement">
                  <el-icon><Notification /></el-icon>
                </el-button>
              </el-tooltip>
            </template>
            <template v-else>
              <el-button text class="home-tool-btn home-game-center-btn" @click="$router.push('/games')">
                <el-icon><Trophy /></el-icon>
                <span class="home-tool-btn__text">游戏中心</span>
              </el-button>
              <el-button text class="home-tool-btn" @click="$router.push('/emoji-shop')">
                <el-icon><Goods /></el-icon>
                <span class="home-tool-btn__text">表情商城</span>
              </el-button>
              <el-button text class="home-tool-btn" @click="$router.push('/vip')">
                <el-icon><Medal /></el-icon>
                <span class="home-tool-btn__text">会员中心</span>
              </el-button>
              <el-button type="primary" round size="small" @click="$router.push('/sign-in')">
                登录 / 注册
              </el-button>
            </template>
          </div>
        </div>

        <div class="shell-main-outlet">
          <router-view v-slot="{ Component }">
            <keep-alive include="HomeFeed">
              <component :is="Component" />
            </keep-alive>
          </router-view>
        </div>
        <SiteIcpBar v-if="!isShellBare" />
      </section>
    </div>
    <AnnouncementBoard ref="announcementRef" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Calendar, Coin, EditPen, Goods, Medal, Message, Notification, Pointer, Present, Search, Trophy } from '@element-plus/icons-vue'
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import AnnouncementBoard from '@/components/common/AnnouncementBoard.vue'
import MessageIncomingBubble from '@/components/layout/MessageIncomingBubble.vue'
import SiteIcpBar from '@/components/layout/SiteIcpBar.vue'
import { SITE_NAME as siteName } from '@/constants/site'
import { provideHomeShellContext } from '@/composables/useHomeShell'
import { useMascotUiStore } from '@/stores/mascotUi'
import { useMessageCenterUiStore } from '@/stores/messageCenterUi'
import vipBadgeUrl from '@/assets/svg/VIP.svg?url'

const vipBadgeSrc = vipBadgeUrl

const route = useRoute()
const mascotUi = useMascotUiStore()
const messageCenterUi = useMessageCenterUiStore()
const isShellBare = computed(() => route.matched.some((r) => r.meta?.shellBare))
const isShellParticle = computed(() => route.matched.some((r) => r.meta?.shellParticle))

const {
  aiSearchMode,
  announcementRef,
  categoriesWithId,
  defaultAvatar,
  effectiveVipTier,
  goToCreative,
  handleLogout,
  menuActiveKey,
  msgUnread,
  pointsBalance,
  searchInputPlaceholder,
  searchQuery,
  selectCategoryMenu,
  showAnnouncement,
  sidebarMenuActive,
  submitSearch,
  toggleAiSearchMode,
  userStore,
} = provideHomeShellContext()
</script>

<style src="@/assets/styles/home.css"></style>

<style scoped>
.shell-main-outlet {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.home-mascot-pass-btn.is-active {
  color: #7c3aed;
  background: rgba(124, 58, 237, 0.1);
}

.home-game-center-btn {
  color: #ff2442;
}
</style>
