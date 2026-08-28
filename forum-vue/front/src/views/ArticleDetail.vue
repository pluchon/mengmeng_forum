<template>
  <div class="article-detail-route-root">
    <el-dialog
      v-model="dialogOpen"
      class="article-detail-modal article-detail-modal--no-top-title"
      :class="{
        'article-detail-modal--picture': article && !isVideoArticle,
        'article-detail-modal--video': article && isVideoArticle,
        'article-detail-modal--expand-prep': expandFromCardPrep,
      }"
      width="min(1280px, 96vw)"
      align-center
      append-to-body
      :z-index="3100"
      :close-on-click-modal="true"
      :show-close="false"
      :destroy-on-close="false"
      :before-close="handleBeforeClose"
      transition=""
      @closed="handleDialogClosed"
    >
      <div
        class="red-detail-page red-detail-page--modal"
        :class="{
          'red-detail-page--picture': article && !isVideoArticle,
          'red-detail-page--video': article && isVideoArticle,
        }"
      >
        <BorderGlow
          v-if="article"
          class="article-detail-card-glow"
          :animated="aiLoading"
          :edge-sensitivity="30"
          glow-color="320 84 72"
          background-color="#ffffff"
          :border-radius="16"
          :glow-radius="42"
          :glow-intensity="1.1"
          :cone-spread="28"
          :sweep-speed="100"
          :colors="['#f8b5d6', '#d8bcff', '#a3d7ff']"
        >
          <div
            class="red-detail-container"
            :class="{
              'red-detail-container--picture': !isVideoArticle,
              'red-detail-container--video': isVideoArticle,
            }"
          >
          <div class="media-section">
            <div class="media-stage">
              <div class="media-placeholder">
                <ArticleDetailVideo
                  v-if="isVideoArticle && articleVideoUrl && videoPlayerReady"
                  ref="detailVideoRef"
                  :key="articleVideoUrl"
                  :article-id="article.id"
                  :src="articleVideoUrl"
                  :hls-url="articleHlsUrl"
                  :transcode-status="articleVideoTranscodeStatus"
                  :poster="videoPosterUrl || undefined"
                  @playing="onDetailVideoPlaying"
                  @ended="replayDetailVideo"
                  @report-danmaku="reportDanmaku"
                />
                <div
                  v-else-if="isVideoArticle"
                  class="media-video-open-placeholder"
                  aria-hidden="true"
                />
                <el-image
                  v-else-if="mainDisplayImageUrl"
                  :src="mainDisplayImageUrl"
                  fit="contain"
                  class="media-gallery-main media-gallery-main--clickable"
                  :preview-src-list="[]"
                  @click.stop="openMainImagePreview"
                >
                  <template #error>
                    <div class="article-image-error" role="img" aria-label="图片加载失败">
                      <el-icon><PictureFilled /></el-icon>
                      <span>图片加载失败</span>
                    </div>
                  </template>
                </el-image>
                <div v-else class="cover-content">
                  <el-icon :size="120" color="rgba(0,0,0,0.03)"><PictureFilled /></el-icon>
                  <p class="media-empty-hint">{{ isVideoArticle ? '暂无视频' : '暂无相册图片' }}</p>
                </div>
                <button
                  v-if="showGalleryNavArrows"
                  type="button"
                  class="media-gallery-nav-btn media-gallery-nav-btn--prev"
                  :class="{ 'is-disabled': !galleryCanGoPrev }"
                  :disabled="!galleryCanGoPrev"
                  aria-label="上一张"
                  @click.stop="shiftGalleryIndex(-1)"
                >
                  <el-icon :size="18"><ArrowLeft /></el-icon>
                </button>
                <button
                  v-if="showGalleryNavArrows"
                  type="button"
                  class="media-gallery-nav-btn media-gallery-nav-btn--next"
                  :class="{ 'is-disabled': !galleryCanGoNext }"
                  :disabled="!galleryCanGoNext"
                  aria-label="下一张"
                  @click.stop="shiftGalleryIndex(1)"
                >
                  <el-icon :size="18"><ArrowRight /></el-icon>
                </button>
                <div
                  v-if="!isVideoArticle && (galleryPageLabel || mainDisplayImageUrl)"
                  class="media-page-tools"
                >
                  <button
                    v-if="mainDisplayImageUrl"
                    type="button"
                    class="media-download-btn"
                    aria-label="下载原图"
                    title="下载原图"
                    @click.stop="downloadCurrentGalleryImage"
                  >
                    <el-icon :size="16"><Download /></el-icon>
                  </button>
                  <div
                    v-if="galleryPageLabel"
                    class="media-page-badge"
                    aria-live="polite"
                  >
                    {{ galleryPageLabel }}
                  </div>
                </div>
              </div>
            </div>
            <div v-if="!isVideoArticle && articleGalleryUrls.length" class="media-gallery-panel">
              <div
                class="media-gallery-track"
                :class="{
                  'is-overflow': galleryStripOverflow,
                  'is-fade-left': galleryStripFadeLeft,
                  'is-fade-right': galleryStripFadeRight,
                }"
              >
                <button
                  v-if="galleryStripFadeLeft"
                  type="button"
                  class="media-gallery-scroll-btn media-gallery-scroll-btn--left"
                  aria-label="向左查看更多图片"
                  @click="scrollGalleryStripBy(-180)"
                >
                  <el-icon :size="16"><ArrowLeft /></el-icon>
                </button>
                <div ref="galleryStripRef" class="media-gallery-items" @scroll="onGalleryStripScroll">
                  <button
                    v-for="(url, gi) in articleGalleryUrls"
                    :key="gi + '-' + url"
                    type="button"
                    class="media-gallery-thumb"
                    :class="{ 'is-active': gi === activeGalleryIndex }"
                    @click="setActiveGalleryIndex(gi, true)"
                  >
                    <el-image :src="url" fit="cover" class="media-gallery-thumb-image">
                      <template #error>
                        <div class="article-image-error" role="img" aria-label="图片加载失败">
                          <el-icon><PictureFilled /></el-icon>
                        </div>
                      </template>
                    </el-image>
                  </button>
                </div>
                <button
                  v-if="galleryStripFadeRight"
                  type="button"
                  class="media-gallery-scroll-btn media-gallery-scroll-btn--right"
                  aria-label="向右查看更多图片"
                  @click="scrollGalleryStripBy(180)"
                >
                  <el-icon :size="16"><ArrowRight /></el-icon>
                </button>
              </div>
            </div>
          </div>

          <div class="info-section">
            <button
              type="button"
              class="article-detail-card-close"
              aria-label="关闭"
              @click="closeDetailDialog"
            >
              <el-icon :size="18"><Close /></el-icon>
            </button>
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
                    :src="author?.avatarUrl || defaultAvatar"                  />
                  <div class="author-name-group">
                    <div class="author-name-row">
                      <span class="nickname">{{ author?.nickname || '匿名用户' }}</span>
                      <button
                        v-if="!isOwner"
                        type="button"
                        class="article-detail-follow-btn"
                        :class="isFollowingAuthor ? 'article-detail-follow-btn--followed' : 'article-detail-follow-btn--follow'"
                        :disabled="followSaving"
                        @click.stop="toggleFollowAuthor"
                      >
                        {{ isFollowingAuthor ? '已关注' : '关注' }}
                        <span v-if="!isFollowingAuthor" class="article-detail-follow-plus" aria-hidden="true">+</span>
                      </button>
                    </div>
                    <span v-if="authorMetaText" class="author-meta">{{ authorMetaText }}</span>
                  </div>
                </div>
                <div v-else class="author-info author-info--static">
                  <UserAvatarVip
                    :size="40"
                    :src="author?.avatarUrl || defaultAvatar"                  />
                  <div class="author-name-group">
                    <div class="author-name-row">
                      <span class="nickname">{{ author?.nickname || '匿名用户' }}</span>
                    </div>
                    <span v-if="authorMetaText" class="author-meta">{{ authorMetaText }}</span>
                  </div>
                </div>
              </div>
              <div class="author-header-right">
                <span
                  v-if="isQuestion"
                  class="question-detail-status"
                  :class="questionStatusClass(article.questionStatus)"
                >
                  <span class="question-detail-status__dot" />
                  {{ questionStatusLabel(article.questionStatus) }}
                </span>
                <el-tag v-if="isOwner" size="small" type="danger" effect="dark">
                  你自己
                </el-tag>
              </div>
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

            <el-scrollbar
              ref="articleContentScrollRef"
              class="article-content-scroll article-content-scroll--hidden-bar"
            >
              <div class="article-body">
                <h1 class="content-title">{{ article.title }}</h1>
                <div
                  class="content-text-wrap"
                  :class="{ 'is-collapsed': shouldCollapseContent && !contentExpanded }"
                >
                  <div class="content-text" v-html="renderedContent"></div>
                </div>
                <div v-if="shouldCollapseContent" class="content-expand-wrap">
                  <button
                    type="button"
                    class="content-expand-btn"
                    @click="contentExpanded = !contentExpanded"
                  >
                    {{ contentExpanded ? '收起' : '点击展示全文' }}
                  </button>
                </div>

                <div v-if="articleTags.length" class="content-meta content-meta--tags-only">
                  <div class="article-detail-tags article-detail-tags--inline">
                    <span
                      v-for="t in articleTags"
                      :key="'at-' + t.id"
                      class="article-detail-tag article-detail-tag--hash"
                    >
                      #{{ t.name }}
                    </span>
                  </div>
                </div>

                <div class="post-action-bar">
                  <div
                    class="post-action-bar__left"
                    :class="{ 'is-triple-pressing': triplePressActive }"
                    :style="{ '--triple-progress': `${triplePressProgress * 360}deg` }"
                  >
                    <span
                      class="post-action-chip post-action-chip--readonly"
                      aria-label="浏览量"
                    >
                      <el-icon :size="15"><View /></el-icon>
                      <span>{{ formatCompactNumber(article?.visitCount) }}</span>
                    </span>
                    <button
                      type="button"
                      class="post-action-chip"
                      :class="{ 'is-active': isLiked }"
                      @pointerdown="startTriplePress"
                      @pointerup="finishTriplePress"
                      @pointercancel="cancelTriplePress"
                      @keydown.enter.prevent="handleLike"
                      @keydown.space.prevent="handleLike"
                      @contextmenu.prevent
                    >
                      <LikeCountIcon class="post-action-chip__icon" :filled="isLiked" />
                      <span>{{ article.likeCount || 0 }}</span>
                    </button>
                    <button
                      type="button"
                      class="post-action-chip"
                      :class="{ 'is-active': isFavorited }"
                      @click="toggleFavorite"
                    >
                      <el-icon :size="15"><CollectionTag /></el-icon>
                      <span>{{ article?.favoriteCount ?? 0 }}</span>
                    </button>
                    <button
                      v-if="articleMusic"
                      type="button"
                      class="post-action-chip post-action-chip--music"
                      :class="{ 'is-active': musicPlaying }"
                      :title="articleMusic.title"
                      @click="toggleArticleMusic"
                    >
                      <el-icon :size="15">
                        <VideoPause v-if="musicPlaying" />
                        <Headset v-else />
                      </el-icon>
                      <span class="post-action-chip__music-title">{{ articleMusic.title }}</span>
                      <span
                        v-if="musicPlaying"
                        class="post-action-chip__music-eq"
                        aria-hidden="true"
                      >
                        <span
                          v-for="bar in musicEqBars"
                          :key="`detail-eq-${bar}`"
                          class="post-action-chip__music-eq-bar"
                          :style="{ animationDelay: `${bar * 0.12}s` }"
                        />
                      </span>
                    </button>
                    <button
                      type="button"
                      class="post-action-chip"
                      :class="{ 'is-copied': shareCopied }"
                      @click="handleShare"
                    >
                      <span v-if="shareCopied">已复制</span>
                      <template v-else>
                        <el-icon :size="15"><Share /></el-icon>
                        <span>分享</span>
                      </template>
                    </button>
                  </div>
                  <audio
                    ref="musicAudioRef"
                    class="article-detail-music-audio"
                    preload="none"
                    @ended="onMusicEnded"
                  />
                  <div class="post-action-bar__right">
                    <button
                      v-if="canToggleQuestionResolved"
                      type="button"
                      class="question-resolve-toggle"
                      :class="{ 'is-resolved': questionResolved }"
                      :disabled="questionActionSaving"
                      @click="toggleQuestionResolved"
                    >
                      {{ questionResolveHint }}
                    </button>
                    <button
                      v-if="!isOwner && !isNotInterested"
                      type="button"
                      class="post-action-icon-btn"
                      aria-label="不感兴趣"
                      title="不感兴趣"
                      :disabled="notInterestedSaving"
                      @click="openNotInterestedDialog"
                    >
                      <svg class="post-action-heart-crack" viewBox="0 0 24 24" aria-hidden="true">
                        <path
                          fill="none"
                          stroke="currentColor"
                          stroke-width="1.7"
                          stroke-linecap="round"
                          stroke-linejoin="round"
                          d="M19.5 12.572 12 20l-2.2-2.2M12 20l-7.5-7.428A5 5 0 1 1 12 6.006a5 5 0 1 1 7.5 6.566"
                        />
                        <path
                          fill="none"
                          stroke="currentColor"
                          stroke-width="1.7"
                          stroke-linecap="round"
                          stroke-linejoin="round"
                          d="m12 6-.8 2.4 1.8 1.4-1.5 2.2L13 14"
                        />
                      </svg>
                    </button>
                    <button
                      v-if="!isOwner"
                      type="button"
                      class="post-action-icon-btn"
                      aria-label="举报帖子"
                      title="举报"
                      @click="reportArticle"
                    >
                      <el-icon :size="17"><Flag /></el-icon>
                    </button>
                  </div>
                </div>

                <div class="ai-summary-box animate-fade-up" :class="{ 'is-collapsed': aiSummaryCollapsed }">
                  <div class="ai-guide-header">
                    <div class="ai-guide-title">
                      <el-icon class="ai-guide-wand" :size="18"><MagicStick /></el-icon>
                      <span>AI 导读</span>
                    </div>
                    <div class="ai-guide-actions">
                      <button
                        type="button"
                        class="ai-summary-icon-btn"
                        :disabled="!aiSummaryCanExpand"
                        :title="aiSummaryCollapsed ? '展开导读' : '收起导读'"
                        @click="toggleAiSummaryCollapsed"
                      >
                        <el-icon :size="16" :class="{ 'is-flipped': aiSummaryCollapsed }"><ArrowUp /></el-icon>
                      </button>
                    </div>
                  </div>
                  <textarea
                    v-show="!aiSummaryCollapsed && (aiSummary || aiLoading)"
                    ref="aiSummaryAreaRef"
                    v-model="aiSummary"
                    readonly
                    class="ai-summary-textarea"
                    :class="{ 'is-hint': aiSummaryIsHint }"
                    :placeholder="aiLoading ? '正在生成摘要…' : ''"
                  />
                  <button
                    v-show="!aiSummaryCollapsed"
                    type="button"
                    class="ai-summary-icon-btn ai-summary-refresh-btn"
                    :disabled="!aiSummaryCanRegenerate || aiLoading"
                    title="重新生成导读"
                    @click="regenerateAiSummary"
                  >
                    <el-icon :size="16" :class="{ 'is-spinning': aiLoading }"><RefreshRight /></el-icon>
                  </button>
                </div>
              </div>

              <div class="comments-section-head">
                <span class="comments-section-head__line" aria-hidden="true" />
                <span class="comments-section-head__text">
                  {{ replyCountDisplay }} 条{{ isQuestion ? '回答' : '评论' }}
                </span>
                <span class="comments-section-head__line" aria-hidden="true" />
              </div>

              <div class="comments-list">
                <div
                  v-for="item in replies"
                  :key="item.articleReply.id"
                  class="comment-item"
                >
                  <div class="comment-floor">
                    <div
                      class="comment-floor-body"
                      :class="{ 'comment-floor-body--accepted': isAcceptedReply(item) }"
                    >
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
                          :src="item.user?.avatarUrl || defaultAvatar"                        />
                      </div>
                      <UserAvatarVip
                        v-else
                        :size="32"
                        :src="item.user?.avatarUrl || defaultAvatar"                      />
                      <div class="comment-main">
                        <div class="comment-user-row">
                          <div class="comment-user-left">
                            <span
                              v-if="item.user?.id"
                              class="comment-user-name comment-user-name--link"
                              role="link"
                              tabindex="0"
                              @click="goUserProfile(item.user.id)"
                              @keydown.enter.prevent="goUserProfile(item.user.id)"
                            >{{ item.user?.nickname || '匿名用户' }}</span>
                            <span v-else class="comment-user-name">{{ item.user?.nickname || '匿名用户' }}</span>
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
                          <span class="comment-user-meta">
                            {{ formatCommentMeta(item.articleReply.createTime, item.articleReply?.ipRegion) }}
                          </span>
                        </div>
                        <div class="comment-text">
                          <CommentExpandableText
                            :content="item.articleReply.content"
                            :render-html="renderCommentHtml"
                          />
                        </div>
                        <CommentReplyMediaDisplay
                          :media-list="item.mediaList"
                          @open-shop="openCommentShopDetail"
                        />
                        <div class="comment-actions">
                          <div class="comment-actions__left">
                            <button type="button" class="comment-action-btn" @click="toggleReplyLike(item)">
                              <LikeCountIcon class="comment-like-icon" :filled="item.liked" />
                              <span :class="{ 'is-liked': item.liked }">{{ item.articleReply.likeCount || 0 }}</span>
                            </button>
                            <button type="button" class="comment-action-btn" @click="startReplyToFloor(item)">
                              <el-icon :size="13"><ChatDotRound /></el-icon>
                              <span>{{ item.subReplyCount || 0 }}</span>
                            </button>
                            <button
                              v-if="canAcceptAnswer && !isArticleAuthorReply(item) && !isAcceptedReply(item)"
                              type="button"
                              class="comment-action-btn comment-action-btn--accept"
                              :disabled="questionActionSaving"
                              @click="acceptAnswer(item)"
                            >
                              采纳
                            </button>
                          </div>
                          <div class="comment-actions__right">
                            <span v-if="isAcceptedReply(item)" class="comment-accepted-tag">已采纳</span>
                            <button
                              v-if="!isOwnComment(item) && !isArticleAuthorReply(item)"
                              type="button"
                              class="comment-action-btn comment-action-btn--report"
                              aria-label="举报评论"
                              title="举报"
                              @click="reportReply(item)"
                            >
                              <el-icon :size="14"><Flag /></el-icon>
                            </button>
                          </div>
                        </div>
                      </div>
                    </div>
                    <SubReplyArea
                      class="comment-sub-replies"
                      :reply-id="item.articleReply.id"
                      :article-id="article.id"
                      :author-id="author?.id"
                      :refresh-token="subReplyRefreshTokens[item.articleReply.id] || 0"
                      :sub-reply-count="item.subReplyCount || 0"
                      :can-accept="canAcceptAnswer"
                      :accept-saving="questionActionSaving"
                      read-only
                      @reply="startReplyToSub"
                      @open-profile="goUserProfile"
                      @open-shop="openCommentShopDetail"
                      @report="reportSubReply"
                      @accept="acceptSubAnswer"
                    />
                  </div>
                </div>
                <el-empty
                  v-if="replies.length === 0"
                  :description="isQuestion ? '等待第一条认真回答' : '期待你的精彩评论'"
                  :image="emptyCommentIconUrl"
                  :image-size="120"
                />
                <div
                  v-if="replies.length > 0 && (replyHasMore || replyLoadingMore)"
                  ref="replyLoadMoreSentinelRef"
                  class="comment-load-more"
                  aria-live="polite"
                >
                  {{ replyLoadingMore ? '加载更多评论…' : '继续下滑加载更多' }}
                </div>
                <div
                  v-else-if="replies.length > 0 && replyTotal > replyPageSize"
                  class="comment-load-more comment-load-more--done"
                >
                  已加载全部评论
                </div>
              </div>
            </el-scrollbar>

            <div class="interaction-footer interaction-footer-stacked">
              <div v-if="replyTarget" class="reply-target-bar">
                <div class="reply-target-text">
                  <span class="reply-target-label">{{ replyTargetLabel }}</span>
                  <span v-if="replyTarget.contentPreview" class="reply-target-preview">
                    {{ replyTarget.contentPreview }}
                  </span>
                </div>
                <button type="button" class="reply-target-clear" @click="clearReplyTarget">取消</button>
              </div>
              <div
                v-if="replyPendingImages.length || replyPendingEmojis.length"
                class="reply-pending-media"
              >
                <div
                  v-for="(img, idx) in replyPendingImages"
                  :key="`img-${img.id || img.mediaUrl}`"
                  class="reply-pending-card"
                  :class="{
                    'is-uploading': img.pending,
                    'is-failed': img.failed && !img.pending,
                  }"
                >
                  <el-image :src="img.mediaUrl" fit="cover" class="reply-pending-thumb">
                    <template #error>
                      <div class="article-image-error" role="img" aria-label="图片加载失败">
                        <el-icon><PictureFilled /></el-icon>
                      </div>
                    </template>
                  </el-image>
                  <div v-if="img.pending" class="reply-pending-uploading" aria-hidden="true">
                    <span class="reply-pending-uploading-dot" />
                  </div>
                  <button
                    v-if="img.failed && !img.pending"
                    type="button"
                    class="reply-pending-retry"
                    title="重试上传"
                    aria-label="重试上传"
                    @click="retryPendingImage(idx)"
                  >
                    <el-icon :size="14"><RefreshRight /></el-icon>
                    <span>重试</span>
                  </button>
                  <button
                    type="button"
                    class="reply-pending-remove"
                    aria-label="移除"
                    @click="removePendingImage(idx)"
                  >
                    <el-icon><Close /></el-icon>
                  </button>
                </div>
                <div
                  v-for="(em, idx) in replyPendingEmojis"
                  :key="`em-${em.mediaUrl}`"
                  class="reply-pending-card reply-pending-card--emoji"
                >
                  <el-image :src="em.mediaUrl" fit="contain" class="reply-pending-thumb">
                    <template #error>
                      <div class="article-image-error" role="img" aria-label="图片加载失败">
                        <el-icon><PictureFilled /></el-icon>
                      </div>
                    </template>
                  </el-image>
                  <img :src="emojiPackIconUrl" alt="" class="reply-pending-emoji-badge" aria-hidden="true">
                  <button type="button" class="reply-pending-remove" aria-label="移除" @click="removePendingEmoji(idx)">
                    <el-icon><Close /></el-icon>
                  </button>
                </div>
              </div>
              <div class="comment-composer" :class="{ 'vip-comment-gold': isVipGold }">
                <button
                  type="button"
                  class="comment-upload-btn comment-upload-btn--plain"
                  title="上传图片"
                  aria-label="上传图片"
                  @click.stop="triggerReplyImagePick"
                >
                  <svg class="comment-upload-icon" viewBox="0 0 24 24" aria-hidden="true">
                    <rect
                      x="3"
                      y="5"
                      width="14"
                      height="14"
                      rx="2.2"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="1.7"
                    />
                    <circle cx="8.2" cy="10" r="1.35" fill="currentColor" />
                    <path
                      d="M5.2 17.2 9.1 13.4l2.7 2.7 2.1-2.1 3.1 3.2"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="1.7"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    />
                    <path
                      d="M17 3.5v6M14 6.5h6"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="1.7"
                      stroke-linecap="round"
                    />
                  </svg>
                </button>
                <div class="comment-input-wrap comment-input-full">
                  <el-input
                    v-model="replyContent"
                    type="textarea"
                    :autosize="{ minRows: 1, maxRows: 3 }"
                    :placeholder="replyPlaceholder"
                    class="red-input red-input-compact red-input-textarea"
                    @keydown.enter="onReplyKeydown"
                  />
                  <div class="comment-suffix-tools">
                    <el-popover
                      v-model:visible="replyEmojiPanelOpen"
                      placement="top-start"
                      :width="320"
                      trigger="click"
                      teleported
                      popper-class="comment-emoji-popper"
                      :z-index="6500"
                      @show="onReplyEmojiPopoverShow"
                    >
                      <template #reference>
                        <button
                          type="button"
                          class="comment-tool-btn comment-tool-btn--muted"
                          title="已购表情"
                          @click.stop
                        >
                          <img :src="emojiPackIconUrl" alt="" class="comment-emoji-pack-icon">
                        </button>
                      </template>
                      <div v-loading="emojiShopStore.myPacksLoading" class="comment-emoji-panel">
                        <div v-if="!replyVisiblePacks.length" class="comment-emoji-empty">暂无已购表情包</div>
                        <div v-else class="mc-emoji-purchased-layout">
                          <div class="mc-emoji-pack-body">
                            <div class="mc-emoji-grid mc-emoji-grid--pack mc-emoji-grid--scroll">
                              <el-image
                                v-for="(url, uidx) in (replySelectedPack?.imageUrls || [])"
                                :key="uidx"
                                :src="url"
                                fit="contain"
                                class="mc-emoji-thumb"
                                @click="addReplyShopEmoji(url)"
                              >
                                <template #error>
                                  <div class="article-image-error" role="img" aria-label="图片加载失败">
                                    <el-icon><PictureFilled /></el-icon>
                                  </div>
                                </template>
                              </el-image>
                            </div>
                          </div>
                          <div class="mc-emoji-pack-bar">
                            <button
                              v-if="replyPackBarCanScrollLeft"
                              type="button"
                              class="mc-emoji-pack-more"
                              aria-label="向左查看更多"
                              @click="scrollReplyPackBarLeft"
                            >
                              <el-icon><ArrowLeft /></el-icon>
                            </button>
                            <div
                              ref="replyPackBarRef"
                              class="mc-emoji-pack-bar-scroll"
                              @scroll="onReplyPackBarScroll"
                            >
                              <div
                                v-for="pack in replyVisiblePacks"
                                :key="pack.userEmojiId"
                                class="mc-emoji-pack-bar-item"
                              >
                                <button
                                  type="button"
                                  class="mc-emoji-pack-cover"
                                  :class="{ 'is-active': Number(replySelectedPack?.shopId) === Number(pack.shopId) }"
                                  :title="pack.name"
                                  @click="selectReplyPack(pack)"
                                >
                                  <el-image :src="pack.coverUrl || pack.imageUrls?.[0]" fit="cover" class="mc-emoji-pack-cover-image">
                                    <template #error>
                                      <div class="article-image-error" role="img" aria-label="图片加载失败">
                                        <el-icon><PictureFilled /></el-icon>
                                      </div>
                                    </template>
                                  </el-image>
                                </button>
                                <transition name="mc-pack-name">
                                  <span
                                    v-if="Number(replySelectedPack?.shopId) === Number(pack.shopId)"
                                    :key="pack.shopId"
                                    class="mc-emoji-pack-name"
                                  >{{ pack.name }}</span>
                                </transition>
                              </div>
                            </div>
                            <button
                              v-if="replyPackBarCanScrollRight"
                              type="button"
                              class="mc-emoji-pack-more"
                              aria-label="向右查看更多"
                              @click="scrollReplyPackBarRight"
                            >
                              <el-icon><ArrowRight /></el-icon>
                            </button>
                          </div>
                        </div>
                      </div>
                    </el-popover>
                  </div>
                </div>
                <button
                  type="button"
                  class="comment-send-btn"
                  :class="{ 'is-disabled': !canSubmitReply }"
                  :disabled="!canSubmitReply"
                  @click="submitReply"
                >
                  <el-icon :size="16"><Promotion /></el-icon>
                  <span>{{ replySubmitting ? '发送中' : '发送' }}</span>
                </button>
              </div>
              <input
                ref="replyImageInput"
                type="file"
                accept="image/jpeg,image/png,image/gif"
                multiple
                class="sr-only"
                @change="onReplyImageFileChange"
              >
            </div>
          </div>
          </div>
        </BorderGlow>

        <div v-else-if="loading" class="loading-state">
          <el-skeleton :rows="10" animated />
        </div>

        <div v-else class="article-not-found">
          <img
            class="article-not-found__img"
            :src="articleNotFoundImageUrl"
            alt="帖子不存在"
          >
          <p class="article-not-found__text">帖子好像去旅行了</p>
          <div class="article-not-found__actions">
            <button
              type="button"
              class="article-not-found__btn article-not-found__btn--primary"
              @click="reloadArticleDetail"
            >
              <el-icon :size="16"><RefreshRight /></el-icon>
              <span>重新加载</span>
            </button>
            <button
              type="button"
              class="article-not-found__btn article-not-found__btn--ghost"
              @click="browseOtherArticles"
            >
              <el-icon :size="16"><Compass /></el-icon>
              <span>看看别的帖子</span>
            </button>
          </div>
        </div>
      </div>
    </el-dialog>

    <el-image-viewer
      v-if="mainImagePreviewVisible"
      :url-list="imagePreviewList"
      :initial-index="activeGalleryIndex"
      teleported
      :z-index="13000"
      @close="closeMainImagePreview"
    />

    <TopTitleDialog
      v-model="favoriteDialogVisible"
      title="添加到收藏夹"
      confirm-text="确定"
      :show-close="false"
      :loading="favoriteSaving"
      width="420px"
      :z-index="4000"
      @confirm="confirmFavorite"
    >
      <el-form label-width="96px" class="favorite-dialog-form">
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
              :label="f.name"
              :value="f.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </TopTitleDialog>

    <TopTitleDialog
      v-model="notInterestedDialogVisible"
      title="不感兴趣"
      confirm-text="提交"
      :show-close="false"
      :loading="notInterestedSaving"
      :confirm-disabled="!notInterestedReasonCode"
      width="min(420px, 92vw)"
      :z-index="13000"
      @confirm="submitNotInterested"
    >
      <div class="not-interested-reason-list" role="radiogroup" aria-label="不感兴趣原因">
        <button
          v-for="reason in notInterestedReasons"
          :key="reason.code"
          type="button"
          class="app-dialog__chip"
          :class="{ 'is-active': notInterestedReasonCode === reason.code }"
          :disabled="notInterestedSaving"
          @click="notInterestedReasonCode = reason.code"
        >
          {{ reason.label }}
        </button>
      </div>
      <el-input
        v-model="notInterestedReasonDetail"
        class="not-interested-reason-detail"
        :disabled="notInterestedReasonCode !== 'OTHER'"
        maxlength="200"
        :rows="3"
        resize="none"
        type="textarea"
      />
    </TopTitleDialog>

    <ReportReasonDialog
      v-model:visible="contentReportDialogVisible"
      :title="contentReportDialogTitle"
      :submitting="contentReportSubmitting"
      @submit="submitContentReport"
    />
  </div>
</template>

<script setup src="./ArticleDetail.js"></script>

<style scoped src="@/assets/styles/article.css"></style>
<style scoped src="@/assets/styles/article-detail-owner.css"></style>
<style lang="scss" src="./ArticleDetail.scss"></style>
<style src="@/assets/styles/article-detail-modal-global.css"></style>
<style src="@/assets/styles/favorite-folder-select.css"></style>
