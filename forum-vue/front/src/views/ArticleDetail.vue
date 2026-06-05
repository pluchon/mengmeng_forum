<template>
  <div class="article-detail-route-root">
    <el-dialog
      v-model="dialogOpen"
      class="article-detail-modal article-detail-modal--no-top-title"
      width="min(1180px, 96vw)"
      align-center
      append-to-body
      :z-index="3100"
      :close-on-click-modal="true"
      :show-close="false"
      :destroy-on-close="false"
      @closed="handleDialogClosed"
    >
      <div class="red-detail-page red-detail-page--modal">
        <div v-if="article" class="red-detail-container">
          <div class="media-section">
            <div class="media-stage">
              <div class="media-placeholder" :style="{ background: detailCoverBg }">
                <video
                  v-if="isVideoArticle && articleVideoUrl"
                  ref="detailVideoRef"
                  :key="articleVideoUrl"
                  class="media-video-main"
                  :src="articleVideoUrl"
                  autoplay
                  controls
                  controlslist="nodownload noplaybackrate noremoteplayback"
                  disablepictureinpicture
                  playsinline
                  preload="auto"
                  @contextmenu.prevent
                  @ended="replayDetailVideo"
                />
                <el-image
                  v-else-if="activeGalleryUrl"
                  :src="activeGalleryUrl"
                  fit="contain"
                  class="media-gallery-main"
                  :preview-src-list="articleGalleryUrls"
                  :initial-index="activeGalleryIndex"
                  preview-teleported
                  :z-index="5200"
                />
                <div v-else class="cover-content">
                  <el-icon :size="120" color="rgba(0,0,0,0.03)"><PictureFilled /></el-icon>
                  <p class="media-empty-hint">{{ isVideoArticle ? '暂无视频' : '暂无相册图片' }}</p>
                </div>
              </div>
            </div>
            <div v-if="articleGalleryUrls.length" class="media-gallery-panel">
              <div class="media-gallery-label">笔记相册</div>
              <div
                class="media-gallery-track"
                :class="{
                  'is-overflow': galleryStripOverflow,
                  'is-fade-left': galleryStripFadeLeft,
                  'is-fade-right': galleryStripFadeRight,
                }"
              >
                <div ref="galleryStripRef" class="media-gallery-items" @scroll="onGalleryStripScroll">
                  <button
                    v-for="(url, gi) in articleGalleryUrls"
                    :key="gi + '-' + url"
                    type="button"
                    class="media-gallery-thumb"
                    :class="{ 'is-active': gi === activeGalleryIndex }"
                    @click="setActiveGalleryIndex(gi)"
                  >
                    <img :src="url" alt="">
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div class="info-section">
            <div class="author-header">
              <div class="author-header-left">
                <div
                  v-if="author?.id"
                  class="author-info"
                  role="link"
                  tabindex="0"
                  @click="goAuthorProfile"
                  @keydown.enter.prevent="goAuthorProfile"
                >
                  <UserAvatarVip
                    :size="40"
                    :src="author?.avatarUrl || defaultAvatar"
                    :vip-tier="Number(author?.vipTier) || 0"
                    :vip-expire-at="author?.vipExpireAt"
                  />
                  <span class="nickname">{{ author?.nickname }}</span>
                </div>
                <div v-else class="author-info author-info--static">
                  <UserAvatarVip
                    :size="40"
                    :src="author?.avatarUrl || defaultAvatar"
                    :vip-tier="Number(author?.vipTier) || 0"
                    :vip-expire-at="author?.vipExpireAt"
                  />
                  <span class="nickname">{{ author?.nickname }}</span>
                </div>
                <button
                  v-if="isOwner"
                  type="button"
                  class="likers-list-icon-btn"
                  aria-label="查看点赞用户"
                  @click="showLikersDialog = true"
                >
                  <img :src="likersMenuListIconUrl" alt="" class="likers-list-icon-img" />
                </button>
                <el-tag v-if="isOwner" size="small" type="danger" effect="dark" style="margin-left: 4px">
                  你自己
                </el-tag>
              </div>
              <el-button
                circle
                text
                class="author-header-close"
                aria-label="关闭"
                @click="closeDetailDialog"
              >
                <el-icon :size="20"><Close /></el-icon>
              </el-button>
            </div>

            <el-alert
              v-if="ownerAuditNotice"
              class="owner-audit-alert"
              :title="ownerAuditNotice.title"
              :type="ownerAuditNotice.type"
              :closable="false"
              show-icon
            >
              <template #default>
                <p class="owner-audit-desc">{{ ownerAuditNotice.description }}</p>
                <el-button size="small" type="primary" @click="$router.push(ownerAuditNotice.path)">
                  {{ ownerAuditNotice.buttonText }}
                </el-button>
              </template>
            </el-alert>

            <el-scrollbar class="article-content-scroll">
              <div class="article-body">
                <h1 class="content-title">{{ article.title }}</h1>
                <div class="content-text" v-html="renderedContent"></div>

                <div class="ai-summary-box animate-fade-up">
                  <div class="ai-guide-header">
                    <div class="ai-guide-title">
                      <el-icon class="ai-guide-wand" :size="18"><MagicStick /></el-icon>
                      <span>AI智能导读</span>
                    </div>
                    <el-button
                      size="small"
                      class="ai-guide-gen-btn"
                      :loading="aiLoading"
                      :disabled="aiLoading"
                      @click="loadAiSummary"
                    >
                      生成摘要
                    </el-button>
                  </div>
                  <textarea
                    ref="aiSummaryAreaRef"
                    v-model="aiSummary"
                    readonly
                    class="ai-summary-textarea"
                    :class="{ 'is-hint': aiSummaryIsHint }"
                    :placeholder="aiLoading ? '正在生成摘要…' : '点击「生成摘要」获取 AI 智能导读'"
                  />
                </div>

                <div class="content-meta">
                  <span class="content-meta__time">{{ formatForumDateTimeShanghai(article.createTime) }}</span>
                  <span
                    v-for="t in articleTags"
                    :key="'at-' + t.id"
                    class="article-detail-tag"
                    :class="`article-detail-tag--${t.colorKey || 'sky'}`"
                  >
                    {{ t.name }}
                  </span>
                </div>
              </div>

              <el-divider content-position="left">共 {{ replyCountDisplay }} 条评论</el-divider>

              <div class="comments-list">
                <div v-for="item in replies" :key="item.articleReply.id" class="comment-item">
                  <div
                    v-if="item.user?.id"
                    class="comment-avatar-link"
                    role="link"
                    tabindex="0"
                    @click="goUserProfile(item.user.id)"
                    @keydown.enter.prevent="goUserProfile(item.user.id)"
                  >
                    <UserAvatarVip
                      :size="32"
                      :src="item.user?.avatarUrl || defaultAvatar"
                      :vip-tier="Number(item.user?.vipTier) || 0"
                      :vip-expire-at="item.user?.vipExpireAt"
                    />
                  </div>
                  <UserAvatarVip
                    v-else
                    :size="32"
                    :src="item.user?.avatarUrl || defaultAvatar"
                    :vip-tier="Number(item.user?.vipTier) || 0"
                    :vip-expire-at="item.user?.vipExpireAt"
                  />
                  <div class="comment-main">
                    <div class="comment-user comment-user-row">
                      <span
                        v-if="item.user?.id"
                        class="comment-user-name comment-user-name--link"
                        role="link"
                        tabindex="0"
                        @click="goUserProfile(item.user.id)"
                        @keydown.enter.prevent="goUserProfile(item.user.id)"
                      >{{ item.user?.nickname }}</span>
                      <span v-else>{{ item.user?.nickname }}</span>
                      <el-tag
                        v-if="author?.id != null && item.user?.id != null && Number(item.user.id) === Number(author.id)"
                        size="small"
                        type="danger"
                        effect="plain"
                        class="up-tag"
                      >
                        UP
                      </el-tag>
                    </div>
                    <div class="comment-text" v-html="item.articleReply.content"></div>
                    <div class="comment-footer">
                      <span class="time">{{ formatForumDateTimeShanghai(item.articleReply.createTime) }}</span>
                    </div>
                    <SubReplyArea
                      :reply-id="item.articleReply.id"
                      :article-id="article.id"
                      :read-only="true"
                    />
                  </div>
                </div>
                <el-empty
                  v-if="replies.length === 0"
                  description="期待你的精彩评论"
                  :image="emptyCommentIconUrl"
                  :image-size="120"
                />
              </div>
            </el-scrollbar>

            <div class="interaction-footer interaction-footer-stacked">
              <div
                class="comment-input-wrap comment-input-full"
                :class="{ 'vip-comment-gold': isVipGold }"
              >
                <el-input
                  v-model="replyContent"
                  placeholder="说点什么…"
                  class="red-input red-input-tall"
                  @keyup.enter="submitReply"
                >
                  <template #suffix>
                    <img
                      :src="sendIconUrl"
                      alt=""
                      class="detail-plain-svg detail-plain-svg--send"
                      :class="{ 'is-disabled': !replyContent.trim() }"
                      role="button"
                      tabindex="0"
                      aria-label="发送"
                      @click="submitReply"
                      @keydown.enter.prevent="submitReply"
                    />
                  </template>
                </el-input>
              </div>
              <div class="action-btns action-btns-row">
                <el-button class="action-item" :class="{ active: isLiked }" @click="handleLike">
                  <el-icon :size="24">
                    <svg v-if="isLiked" viewBox="0 0 1024 1024" width="24" height="24"><path d="M512 896C512 896 160 621.1 160 372.4c0-111.4 89.2-201.8 199.3-201.8 62.7 0 118.8 28.7 152.7 72.8 33.9-44.1 90-72.8 152.7-72.8 110 0 199.3 90.4 199.3 201.8 0 248.7-352 523.6-352 523.6z" fill="#ff2442"/></svg>
                    <svg v-else viewBox="0 0 1024 1024" width="24" height="24"><path d="M512 896C512 896 160 621.1 160 372.4c0-111.4 89.2-201.8 199.3-201.8 62.7 0 118.8 28.7 152.7 72.8 33.9-44.1 90-72.8 152.7-72.8 110 0 199.3 90.4 199.3 201.8 0 248.7-352 523.6-352 523.6z" fill="none" stroke="currentColor" stroke-width="64"/></svg>
                  </el-icon>
                  <span class="count">{{ article.likeCount }}</span>
                </el-button>
                <el-button class="action-item" :class="{ active: isFavorited }" @click="toggleFavorite">
                  <el-icon :size="24"><CollectionTag /></el-icon>
                  <span class="count">{{ article?.favoriteCount ?? 0 }}</span>
                </el-button>
                <el-button class="action-item">
                  <el-icon :size="24"><ChatDotRound /></el-icon>
                  <span class="count">{{ replyCountDisplay }}</span>
                </el-button>
                <el-button class="action-item">
                  <el-icon :size="24"><Share /></el-icon>
                </el-button>
              </div>
            </div>
          </div>
        </div>

        <div v-else class="loading-state">
          <el-skeleton :rows="10" animated />
        </div>
      </div>
    </el-dialog>

    <el-dialog
      v-model="showLikersDialog"
      title="最新点赞"
      width="350px"
      append-to-body
      :z-index="4000"
      :show-close="false"
      class="red-dialog"
      @open="fetchLikers"
    >
      <div v-loading="loadingLikers" class="likers-list">
        <div v-for="user in latestLikers" :key="user.id" class="liker-item">
          <router-link :to="`/profile/${user.id}`" class="liker-link" @click="showLikersDialog = false">
            <UserAvatarVip
              :size="40"
              :src="user.avatarUrl || defaultAvatar"
              :vip-tier="Number(user.vipTier) || 0"
              :vip-expire-at="user.vipExpireAt"
            />
            <span class="liker-name">{{ user.nickname }}</span>
          </router-link>
        </div>
        <el-empty v-if="!loadingLikers && latestLikers.length === 0" description="暂无点赞" />
      </div>
    </el-dialog>

    <el-dialog
      v-model="favoriteDialogVisible"
      title="收藏到"
      width="420px"
      append-to-body
      :z-index="4000"
      class="red-dialog"
      @open="loadFavoriteFolders"
    >
      <el-form label-width="96px">
        <el-form-item label="选择收藏夹">
          <el-select
            v-model="selectedFolderId"
            placeholder="默认收藏夹"
            style="width: 100%"
            clearable
            filterable
            :loading="favoriteFoldersLoading"
            :teleported="true"
            popper-class="favorite-folder-select-popper"
          >
            <el-option
              v-for="f in favoriteFolders"
              :key="'folder-' + f.id"
              :label="f.name + (f.isDefault === 1 ? '（默认）' : '')"
              :value="f.id"
            />
          </el-select>
          <div style="margin-top: 6px; font-size: 12px; color: #86909c">
            不选则会收藏到默认收藏夹
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="favoriteDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="favoriteSaving" @click="confirmFavorite">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import { useArticleDetail } from '@scripts/views/ArticleDetail'

const {
  ChatDotRound,
  Close,
  CollectionTag,
  PictureFilled,
  Share,
  SubReplyArea,
  MagicStick,
  aiLoading,
  aiSummary,
  aiSummaryAreaRef,
  aiSummaryIsHint,
  article,
  articleTags,
  activeGalleryIndex,
  activeGalleryUrl,
  articleGalleryUrls,
  articleVideoUrl,
  detailVideoRef,
  isVideoArticle,
  replayDetailVideo,
  author,
  closeDetailDialog,
  confirmFavorite,
  defaultAvatar,
  detailCoverBg,
  dialogOpen,
  emptyCommentIconUrl,
  fetchLikers,
  galleryStripFadeLeft,
  galleryStripFadeRight,
  galleryStripOverflow,
  galleryStripRef,
  goAuthorProfile,
  goUserProfile,
  handleDialogClosed,
  favoriteDialogVisible,
  favoriteFolders,
  favoriteFoldersLoading,
  favoriteSaving,
  handleLike,
  isLiked,
  isOwner,
  isFavorited,
  isVipGold,
  latestLikers,
  likersMenuListIconUrl,
  loadAiSummary,
  loading,
  loadingLikers,
  loadFavoriteFolders,
  onGalleryStripScroll,
  ownerAuditNotice,
  renderedContent,
  replies,
  sendIconUrl,
  replyContent,
  replyCountDisplay,
  selectedFolderId,
  setActiveGalleryIndex,
  showLikersDialog,
  submitReply,
  toggleFavorite,
  formatForumDateTimeShanghai,
} = useArticleDetail()
</script>

<style scoped src="@/assets/styles/article.css"></style>
<style scoped src="@/assets/styles/article-detail-owner.css"></style>
<style src="@/assets/styles/article-detail-modal-global.css"></style>
<style src="@/assets/styles/favorite-folder-select.css"></style>
