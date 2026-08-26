<template>
  <div ref="pageRef" class="door-page" @click="createClickSparks">
    <canvas ref="sparkCanvasRef" class="door-click-spark" aria-hidden="true" />
    <section
      class="door-landing"
      :class="{ 'is-guest-3d': !userStore.isLoggedIn }"
      aria-label="门户首页视觉区"
    >
      <div v-if="userStore.isLoggedIn" class="door-page-particles">
        <Particles
          :particle-colors="['#ffffff', '#e7e0ea', '#dbe5ef', '#f0d8c8']"
          :particle-count="180"
          :particle-spread="11"
          :speed="0.1"
          :particle-base-size="95"
          :move-particles-on-hover="true"
          :particle-hover-factor="0.7"
          :alpha-particles="false"
          :disable-rotation="false"
          class="door-page-particles__mesh"
        />
      </div>
      <DoorGuestScene v-else />

      <div class="door-landing__content">
        <h1 v-if="userStore.isLoggedIn" class="door-landing-enter">欢迎回来，今天去哪</h1>
        <h1 v-else class="door-landing-enter">先看看，大家在聊啥</h1>
      </div>

      <header class="door-header door-landing-enter door-landing-enter--header">
      <div class="door-container door-header__inner">
        <button type="button" class="door-brand" :aria-label="siteName" @click="scrollToTop">
          <img
            v-if="!brandMarkFailed"
            class="door-brand__mark"
            src="/login_big.png"
            alt=""
            @error="brandMarkFailed = true"
          />
          <img
            v-if="!brandTitleFailed"
            class="door-brand__title"
            :src="loginTitleUrl"
            :alt="siteName"
            @error="brandTitleFailed = true"
          />
          <strong v-else>{{ siteName }}</strong>
        </button>

        <nav class="door-nav" aria-label="门户导航">
          <button type="button" class="is-active" @click="scrollToTop">首页</button>
          <button type="button" @click="router.push('/community')">社区首页</button>
        </nav>

        <form class="door-search" :class="{ 'is-ai': aiSearchMode }" @submit.prevent="submitSearch">
          <el-icon class="door-search__icon"><Search /></el-icon>
          <button
            type="button"
            class="door-search__mode"
            :aria-pressed="aiSearchMode"
            @click="toggleAiSearch"
          >
            <span>{{ aiSearchMode ? 'AI' : '综合' }}</span>
          </button>
          <input
            ref="searchInputRef"
            v-model="searchQuery"
            type="search"
            :placeholder="aiSearchMode ? '向 AI 描述你想找的内容' : '搜你所想，或试试 AI 搜索'"
            aria-label="搜索社区内容"
          />
          <button type="submit" class="door-search__submit" aria-label="提交搜索">
            <el-icon><ArrowRight /></el-icon>
          </button>
        </form>

        <div class="door-header__account">
          <template v-if="userStore.isLoggedIn">
            <el-dropdown trigger="click" placement="bottom-end">
              <button type="button" class="door-user-trigger" aria-label="个人菜单">
                <UserAvatarVip
                  :size="36"
                  :src="userStore.avatarUrl || defaultAvatar"
                  :vip-tier="userStore.vipTier"
                  :vip-expire-at="userStore.vipExpireAt"
                  :show-vip-ring="false"
                />
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <button type="button" class="door-button door-button--ghost" @click="router.push('/sign-in')">登录</button>
            <button type="button" class="door-button door-button--primary" @click="router.push('/sign-up')">注册</button>
          </template>
        </div>
      </div>
      </header>
    </section>

    <main class="door-main">

      <section class="door-section door-section--community door-reveal">
        <div class="door-container">
          <div class="door-section__head">
            <div>
              <span class="door-kicker">COMMUNITY</span>
              <h2>社区热选</h2>
            </div>
            <button type="button" class="door-text-link" @click="router.push('/community')">
              查看全部
              <el-icon><ArrowRight /></el-icon>
            </button>
          </div>

          <div v-if="hotState.loading" class="door-state-panel">
            <el-icon class="is-spinning"><Refresh /></el-icon>
            <span>正在整理社区热选...</span>
          </div>
          <div v-else-if="hotState.error" class="door-state-panel is-error">
            <span>{{ hotState.error }}</span>
            <button type="button" @click="loadHotArticles">重新加载</button>
          </div>
          <div v-else-if="hotRecords.length === 0" class="door-state-panel">
            <el-icon><Document /></el-icon>
            <span>社区暂时还没有热选内容</span>
          </div>
          <div v-else class="door-community-grid">
            <article
              v-if="imageHotFeatured"
              class="door-featured-post"
              tabindex="0"
              @click="openArticle(imageHotFeatured)"
              @keyup.enter="openArticle(imageHotFeatured)"
            >
              <div
                v-if="articleCover(imageHotFeatured) && !imageErrors['hot-image']"
                class="door-featured-post__cover"
              >
                <img
                  :src="articleCover(imageHotFeatured)"
                  :alt="articleTitle(imageHotFeatured)"
                  @error="markImageError('hot-image')"
                >
              </div>
              <div v-else class="door-community-placeholder">
                <el-icon><Picture /></el-icon>
                <span>暂无封面</span>
              </div>
              <div class="door-featured-post__meta">
                <div class="door-featured-post__author">
                  <img
                    class="door-featured-post__avatar"
                    :src="articleAuthorAvatar(imageHotFeatured)"
                    :alt="articleAuthor(imageHotFeatured)"
                    @error="onFeaturedAvatarError"
                  >
                  <span>{{ articleAuthor(imageHotFeatured) }}</span>
                </div>
                <div class="door-featured-post__stats">
                  <span><el-icon><View /></el-icon>{{ formatCompactNumber(imageHotFeatured?.article?.visitCount) }}</span>
                  <span><el-icon><ChatDotRound /></el-icon>{{ formatCompactNumber(imageHotFeatured?.article?.replyCount) }}</span>
                  <span><el-icon><Star /></el-icon>{{ formatCompactNumber(imageHotFeatured?.article?.likeCount) }}</span>
                </div>
              </div>
              <h3>{{ articleTitle(imageHotFeatured) }}</h3>
              <p class="door-featured-post__excerpt">{{ articleExcerpt(imageHotFeatured) }}</p>
            </article>
            <div v-else class="door-featured-post door-featured-post--empty">
              <div class="door-community-placeholder">
                <el-icon><Picture /></el-icon>
                <span>暂无图文精选</span>
              </div>
            </div>

            <article
              v-if="videoHotFeatured"
              class="door-featured-post door-featured-post--video"
              tabindex="0"
              @click="openArticle(videoHotFeatured)"
              @keyup.enter="openArticle(videoHotFeatured)"
            >
              <div
                v-if="articleCover(videoHotFeatured) && !imageErrors['hot-video']"
                class="door-featured-post__cover"
              >
                <img
                  :src="articleCover(videoHotFeatured)"
                  :alt="articleTitle(videoHotFeatured, '社区视频')"
                  @error="markImageError('hot-video')"
                >
                <span class="door-featured-post__badge" aria-hidden="true">
                  <el-icon><VideoCamera /></el-icon>
                </span>
              </div>
              <div v-else class="door-community-placeholder">
                <el-icon><VideoCamera /></el-icon>
                <span>暂无封面</span>
              </div>
              <div class="door-featured-post__meta">
                <div class="door-featured-post__author">
                  <img
                    class="door-featured-post__avatar"
                    :src="articleAuthorAvatar(videoHotFeatured)"
                    :alt="articleAuthor(videoHotFeatured)"
                    @error="onFeaturedAvatarError"
                  >
                  <span>{{ articleAuthor(videoHotFeatured) }}</span>
                </div>
                <div class="door-featured-post__stats">
                  <span><el-icon><View /></el-icon>{{ formatCompactNumber(videoHotFeatured?.article?.visitCount) }}</span>
                  <span><el-icon><ChatDotRound /></el-icon>{{ formatCompactNumber(videoHotFeatured?.article?.replyCount) }}</span>
                  <span><el-icon><Star /></el-icon>{{ formatCompactNumber(videoHotFeatured?.article?.likeCount) }}</span>
                </div>
              </div>
              <h3>{{ articleTitle(videoHotFeatured, '社区视频') }}</h3>
              <p class="door-featured-post__excerpt">{{ articleExcerpt(videoHotFeatured) }}</p>
            </article>
            <div v-else class="door-featured-post door-featured-post--empty door-featured-post--video">
              <div class="door-community-placeholder">
                <el-icon><VideoCamera /></el-icon>
                <span>暂无视频精选</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="door-ai-section door-reveal">
        <div class="door-container door-ai-section__inner">
          <div class="door-ai-section__copy">
            <span class="door-kicker">AI GUIDE · XIAOMENG MOTIONS</span>
            <h2>{{ userStore.isLoggedIn ? '让小萌接着帮你做' : '认识一下，会执行任务的小萌' }}</h2>
            <p>从社区搜索到创作灵感，把想法说清楚一点，AI 会帮你找到下一步</p>
            <button type="button" class="door-button door-button--primary" @click="focusAiSearch">
              <el-icon><MagicStick /></el-icon>
              {{ userStore.isLoggedIn ? '用用小萌......' : '试试小萌......' }}
            </button>
          </div>
          <div class="door-ai-section__gallery">
            <div class="door-ai-section__gallery-head">
              <span>小萌动作图鉴</span>
              <small>拖动卡片，看看她正在做什么</small>
            </div>
            <CircularGallery
              :items="xiaomengActions"
              :atlas-url="xiaomengAtlasUrl"
              :bend="1.8"
              text-color="#59485f"
              :border-radius="0.085"
              :scroll-speed="1.7"
              :scroll-ease="0.065"
              :auto-play-ms="4200"
            />
          </div>
        </div>
      </section>

      <section class="door-section door-section--features door-reveal">
        <div class="door-container">
          <div class="door-section__head">
            <div>
              <span class="door-kicker">MORE TO EXPLORE</span>
              <h2>社区之外，还有这些日常</h2>
            </div>
          </div>

          <div class="door-feature-grid">
            <article class="door-feature-card door-feature-card--chat">
              <div class="door-feature-copy">
                <span class="door-card-eyebrow"><el-icon><Message /></el-icon> MESSAGES</span>
                <h3>从一条私信，<br />聊到一群朋友</h3>
                <p>支持好友私信与多人群聊，聊你所想</p>
                <button type="button" class="door-text-link" @click="openMessageCenter">
                  打开私信
                  <el-icon><ArrowRight /></el-icon>
                </button>
              </div>
              <div class="door-feature-media door-feature-media--chat">
                <img
                  v-if="!imageErrors.chat"
                  :src="doorChatShowUrl"
                  alt="社区私信界面展示"
                  @error="markImageError('chat')"
                />
                <div v-else class="door-image-fallback">
                  <el-icon><Message /></el-icon>
                  <span>私信海报暂时无法加载</span>
                </div>
              </div>
            </article>

            <div class="door-feature-stack">
              <article class="door-feature-card door-feature-card--shop">
                <div class="door-feature-copy">
                  <h3>表情商城</h3>
                  <p>收藏喜欢的表情包，聊天更有趣</p>
                </div>
                <div class="door-shop-preview">
                  <div
                    v-for="item in shopItems"
                    :key="item.id"
                    class="door-shop-preview__item"
                  >
                    <img
                      v-if="item.coverUrl && !imageErrors[`shop-${item.id}`]"
                      :src="item.coverUrl"
                      :alt="item.name"
                      @error="markImageError(`shop-${item.id}`)"
                    />
                    <span v-else><el-icon><Goods /></el-icon></span>
                  </div>
                  <span v-if="shopState.loading" class="door-shop-preview__state">正在读取</span>
                  <button v-else-if="shopState.error" type="button" class="door-shop-preview__state" @click="loadShopItems">重新加载</button>
                  <span v-else-if="!shopState.loading && shopItems.length === 0" class="door-shop-preview__state">暂无展示商品</span>
                </div>
              </article>

              <article class="door-feature-card door-feature-card--game">
                <div class="door-feature-copy">
                  <h3>游戏中心</h3>
                  <p>进入大厅匹配对手，展示实力</p>
                  <button type="button" class="door-text-link" @click="goProtected('/games', '游戏中心需要登录')">
                    进入大厅
                    <el-icon><ArrowRight /></el-icon>
                  </button>
                </div>
                <div class="door-feature-media door-feature-media--game">
                  <img
                    v-if="!imageErrors.game"
                    :src="doorGameShowUrl"
                    alt="游戏中心展示"
                    @error="markImageError('game')"
                  />
                  <div v-else class="door-image-fallback">
                    <el-icon><Trophy /></el-icon>
                    <span>游戏海报暂时无法加载</span>
                  </div>
                </div>
              </article>
            </div>

            <article class="door-feature-card door-feature-card--creator">
              <div class="door-feature-copy">
                <span class="door-card-eyebrow"><el-icon><EditPen /></el-icon> CREATOR STUDIO</span>
                <h3>灵感不完整，<br />也可以先保存下来</h3>
                <p>从草稿、图片到 AI 创作小结，让每一次表达都有继续的地方</p>
                <button type="button" class="door-text-link" @click="goProtected('/creative', '创作中心需要登录')">
                  打开创作中心
                  <el-icon><ArrowRight /></el-icon>
                </button>
              </div>
              <div class="door-creator-gallery">
                <div v-for="image in creatorImages" :key="image.order" :data-order="image.order">
                  <img
                    v-if="!imageErrors[`creator-${image.order}`]"
                    :src="image.url"
                    :alt="`创作过程展示 ${image.order}`"
                    @error="markImageError(`creator-${image.order}`)"
                  />
                  <div v-else class="door-image-fallback">
                    <el-icon><EditPen /></el-icon>
                    <span>创作海报暂时无法加载</span>
                  </div>
                </div>
              </div>
            </article>
          </div>
        </div>
      </section>

      <section v-if="userStore.isLoggedIn" class="door-section door-section--continue door-reveal">
        <div class="door-container">
          <div class="door-section__head">
            <div>
              <span class="door-kicker">CONTINUE</span>
              <h2>继续上次的事</h2>
            </div>
          </div>
          <div class="door-continue-grid">
            <article class="door-continue-card">
              <div class="door-continue-card__copy">
                <span class="door-card-eyebrow"><el-icon><EditPen /></el-icon> 创作中心</span>
                <div v-if="draftState.loading" class="door-inline-state">正在读取最近草稿...</div>
                <button v-else-if="draftState.error" type="button" class="door-inline-state is-error" @click="loadLatestDraft">{{ draftState.error }} · 重试</button>
                <template v-else>
                  <h3>{{ latestDraft ? draftTitle : '暂无草稿，开始一次新创作' }}</h3>
                  <p v-if="!latestDraft">一句话、一张图，也可以成为新帖子的开始</p>
                </template>
                <button type="button" class="door-text-link" @click="continueCreating">
                  {{ latestDraft ? '继续编辑' : '打开创作中心' }}
                  <el-icon><ArrowRight /></el-icon>
                </button>
              </div>
              <div class="door-continue-card__media">
                <img
                  v-if="draftCoverUrl && !imageErrors['continue-create']"
                  :src="draftCoverUrl"
                  alt="最近草稿的第一张图片"
                  @error="markImageError('continue-create')"
                />
                <div v-else class="door-image-fallback">
                  <el-icon><EditPen /></el-icon>
                  <span>暂无帖子配图</span>
                </div>
              </div>
            </article>

            <article class="door-continue-card">
              <div class="door-continue-card__copy">
                <span class="door-card-eyebrow"><el-icon><Trophy /></el-icon> 游戏中心</span>
                <div v-if="gameState.loading" class="door-inline-state">正在读取最近对局...</div>
                <button v-else-if="gameState.error" type="button" class="door-inline-state is-error" @click="loadLatestGame">{{ gameState.error }} · 重试</button>
                <template v-else>
                  <h3>{{ latestGameRecord ? gameTitle : '还没有对局记录，去大厅看看。' }}</h3>
                  <p v-if="!latestGameRecord">还没有对局记录，去大厅看看吧</p>
                </template>
                <button type="button" class="door-text-link" @click="router.push('/games')">
                  进入游戏大厅
                  <el-icon><ArrowRight /></el-icon>
                </button>
              </div>
              <div class="door-continue-card__media door-continue-card__media--game">
                <img
                  v-if="latestGameCoverUrl && !imageErrors['continue-game']"
                  :src="latestGameCoverUrl"
                  :alt="`${gameTitle}封面`"
                  @error="markImageError('continue-game')"
                />
                <div v-else class="door-image-fallback">
                  <el-icon><Trophy /></el-icon>
                  <span>暂无最近游戏封面</span>
                </div>
              </div>
            </article>
          </div>
        </div>
      </section>

      <section v-else class="door-register-cta door-reveal">
        <div class="door-container door-register-cta__inner">
          <div>
            <h2>喜欢这里，再加入也不迟</h2>
            <p>注册后可以评论、收藏、私信，也能保留自己的创作和游戏记录</p>
          </div>
          <button type="button" class="door-button door-button--primary" @click="router.push('/sign-up')">
            创建账号
            <el-icon><ArrowRight /></el-icon>
          </button>
        </div>
      </section>
    </main>

    <footer class="door-footer">
      <div class="door-container door-footer__inner">
        <div>
          <strong>{{ siteName }}</strong>
          <p>© 2026 Meng · 一个交友阔谈的地方</p>
        </div>
        <nav aria-label="页脚导航">
          <router-link to="/terms">用户协议</router-link>
          <router-link to="/privacy">隐私政策</router-link>
          <router-link to="/vip-agreement">会员协议</router-link>
          <a :href="SITE_ICP_URL" target="_blank" rel="noopener noreferrer">{{ SITE_ICP_NUMBER }}</a>
        </nav>
      </div>
    </footer>

    <button
      type="button"
      class="door-scroll-rocket"
      :class="{
        'is-visible': showScrollRocket,
        'is-launching': rocketLaunching,
      }"
      :aria-label="`回到顶部，当前进度 ${Math.round(scrollProgress * 100)}%`"
      @click="scrollToTopWithRocket"
    >
      <span class="door-scroll-rocket__track" aria-hidden="true">
        <span class="door-scroll-rocket__fill" :style="{ height: `${scrollProgress * 100}%` }" />
        <span
          class="door-scroll-rocket__ship"
          :style="{ top: `calc(${scrollProgress * 100}% - 14px)` }"
        >
          <Rocket :size="16" :stroke-width="2.2" />
        </span>
      </span>
    </button>

    <VipSubscribeDialog v-model="vipDialogVisible" />
  </div>
</template>

<script setup src="./DoorPortal.js"></script>
<style scoped lang="scss" src="@/views/DoorPortal.scss"></style>
<style lang="scss">
/* 门户页滚动在 document 上；须非 scoped，否则 html/body 选择器会被加上组件属性而失效 */
html:has(.door-page),
body:has(.door-page) {
  scrollbar-width: none;
  -ms-overflow-style: none;
}

html:has(.door-page)::-webkit-scrollbar,
body:has(.door-page)::-webkit-scrollbar {
  display: none;
  width: 0;
  height: 0;
}
</style>
