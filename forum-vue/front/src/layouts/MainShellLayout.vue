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
            <div class="home-search-inner" :class="{ 'home-search-inner--ai-rag': aiSearchMode }">
              <el-input
                v-model="searchQuery"
                :placeholder="searchInputPlaceholder"
                class="home-xhs-search"
                size="large"
                clearable
                @keyup.enter="submitSearch"
              >
                <template #prefix>
                  <span class="home-search-prefix">
                    <el-icon class="home-search-prefix-icon"><Search /></el-icon>
                    <span
                      class="home-search-mode-trigger"
                      role="button"
                      tabindex="0"
                      :aria-label="searchTargetMode === 'article' ? '当前：搜帖子，点击切换搜用户' : '当前：搜用户，点击切换搜帖子'"
                      @click.stop="toggleSearchTargetMode"
                      @keydown.enter.prevent="toggleSearchTargetMode"
                    >
                      <img
                        :src="searchTargetMode === 'article' ? articleSearchIconUrl : userSearchIconUrl"
                        alt=""
                        class="home-search-mode-icon"
                      />
                    </span>
                  </span>
                </template>
                <template #suffix>
                  <span
                    class="home-search-ai-trigger"
                    role="button"
                    tabindex="0"
                    aria-label="切换 AI 语义搜索"
                    @click.stop="toggleAiSearchMode"
                    @keydown.enter.prevent="toggleAiSearchMode"
                  >
                    <img :src="aiSearchIconUrl" alt="" class="home-search-ai-icon" />
                  </span>
                </template>
              </el-input>
            </div>
          </div>
          <div class="home-main-tools">
            <el-tooltip :content="mascotUi.pointerPassThrough ? '关闭鼠标穿透（可点击看板娘）' : '开启鼠标穿透（点击穿透看板娘）'" placement="bottom">
              <el-button
                text
                class="home-tool-btn home-mascot-pass-btn"
                :class="{ 'is-active': mascotUi.pointerPassThrough }"
                @click="mascotUi.togglePointerPassThrough()"
              >
                <el-icon><Pointer /></el-icon>
                <span>鼠标穿透</span>
              </el-button>
            </el-tooltip>
            <el-dropdown v-if="userStore.isLoggedIn" trigger="click" placement="bottom-end">
              <el-button text class="home-tool-btn home-more-top">
                更多
                <el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="$router.push('/checkin')">每日签到</el-dropdown-item>
                  <el-dropdown-item @click="$router.push('/emoji-shop')">表情商城</el-dropdown-item>
                  <el-dropdown-item @click="$router.push('/lottery')">积分抽奖</el-dropdown-item>
                  <el-dropdown-item divided @click="$router.push('/vip')">会员中心</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <template v-if="userStore.isLoggedIn">
              <el-button text class="home-tool-btn" @click="goToCreative">
                <el-icon><EditPen /></el-icon>
                <span>创作中心</span>
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
                积分 {{ pointsBalance }}
              </el-tag>
              <el-tooltip content="站点公告" placement="bottom">
                <el-button circle class="home-icon-btn" aria-label="公告" @click="showAnnouncement">
                  <el-icon><Notification /></el-icon>
                </el-button>
              </el-tooltip>
            </template>
            <template v-else>
              <el-dropdown trigger="click" placement="bottom-end">
                <el-button text class="home-tool-btn home-more-top">
                  更多
                  <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="$router.push('/emoji-shop')">表情商城</el-dropdown-item>
                    <el-dropdown-item divided @click="$router.push('/vip')">会员中心</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
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
import { Pointer } from '@element-plus/icons-vue'
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
  ArrowDown,
  EditPen,
  Message,
  Notification,
  Search,
  aiSearchIconUrl,
  aiSearchMode,
  announcementRef,
  articleSearchIconUrl,
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
  searchTargetMode,
  selectCategoryMenu,
  showAnnouncement,
  sidebarMenuActive,
  submitSearch,
  toggleAiSearchMode,
  toggleSearchTargetMode,
  userSearchIconUrl,
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
</style>
