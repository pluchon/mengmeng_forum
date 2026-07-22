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
      :before-close="handleBeforeClose"
      transition=""
      @closed="handleDialogClosed"
    >
      <div class="red-detail-page red-detail-page--modal">
        <div v-if="article" class="red-detail-container">
          <div class="media-section">
            <div class="media-stage">
              <div class="media-placeholder">
                <ArticleDetailVideo
                  v-if="isVideoArticle && articleVideoUrl"
                  :key="articleVideoUrl"
                  :article-id="article.id"
                  :src="articleVideoUrl"
                  @ended="replayDetailVideo"
                />
                <el-image
                  v-else-if="mainDisplayImageUrl"
                  :src="mainDisplayImageUrl"
                  fit="contain"
                  class="media-gallery-main"
                  :preview-src-list="imagePreviewList"
                  :initial-index="activeGalleryIndex"
                  preview-teleported
                  :z-index="5200"
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
              </div>
            </div>
            <div v-if="articleGalleryUrls.length" class="media-gallery-panel">
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
                    <el-image :src="url" fit="cover" class="media-gallery-thumb-image">
                      <template #error>
                        <div class="article-image-error" role="img" aria-label="图片加载失败">
                          <el-icon><PictureFilled /></el-icon>
                        </div>
                      </template>
                    </el-image>
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
                  v-if="!isOwner && author?.id"
                  type="button"
                  class="article-detail-follow-btn"
                  :class="isFollowingAuthor ? 'article-detail-follow-btn--followed' : 'article-detail-follow-btn--follow'"
                  :disabled="followSaving"
                  @click.stop="toggleFollowAuthor"
                >
                  {{ isFollowingAuthor ? '已关注' : '关注' }}
                </button>
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
                <button
                  v-if="canCloseQuestion"
                  type="button"
                  class="question-close-action"
                  :disabled="questionActionSaving"
                  @click="closeCurrentQuestion"
                >
                  关闭问题
                </button>
                <el-tag v-if="isOwner" size="small" type="danger" effect="dark">
                  你自己
                </el-tag>
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

            <el-scrollbar class="article-content-scroll article-content-scroll--hidden-bar">
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
                    v-for="t in visibleArticleTags"
                    :key="'at-' + t.id"
                    class="article-detail-tag"
                    :class="`article-detail-tag--${t.colorKey || 'sky'}`"
                  >
                    {{ t.name }}
                  </span>
                  <button
                    v-if="hiddenArticleTagCount > 0 || tagsExpanded"
                    type="button"
                    class="article-tags-toggle"
                    @click="toggleArticleTags"
                  >
                    {{ tagsExpanded ? '< 收起' : `> 展开 +${hiddenArticleTagCount}` }}
                  </button>
                </div>
              </div>

              <el-divider content-position="left">
                共 {{ replyCountDisplay }} 条{{ isQuestion ? '回答' : '评论' }}
              </el-divider>

              <div class="comments-list">
                <div
                  v-for="item in replies"
                  :key="item.articleReply.id"
                  class="comment-item"
                >
                  <div
                    class="comment-floor"
                    :class="{ 'comment-floor--accepted': isAcceptedReply(item) }"
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
                      <span v-if="isAcceptedReply(item)" class="comment-accepted-tag">最佳答案</span>
                      </div>
                      <div class="comment-text" v-html="renderCommentHtml(item.articleReply.content)"></div>
                      <CommentReplyMediaDisplay
                        :media-list="item.mediaList"
                        @open-shop="openCommentShopDetail"
                      />
                      <div class="comment-footer">
                        <div class="comment-footer-left">
                          <span class="time">{{ formatForumDateTimeShanghai(item.articleReply.createTime) }}</span>
                          <IpRegionLabel :region="item.articleReply?.ipRegion" />
                        </div>
                        <div class="comment-footer-actions">
                        <button
                          v-if="canAcceptAnswer && !isAcceptedReply(item)"
                          type="button"
                          class="comment-action-btn comment-action-btn--accept"
                          :disabled="questionActionSaving"
                          @click="acceptAnswer(item)"
                        >
                          采纳
                        </button>
                        <button type="button" class="comment-action-btn" @click="toggleReplyLike(item)">
                          <LikeCountIcon class="comment-like-icon" :filled="item.liked" />
                          <span :class="{ 'is-liked': item.liked }">{{ item.articleReply.likeCount || 0 }}</span>
                        </button>
                        <button type="button" class="comment-action-btn" @click="startReplyToFloor(item)">
                          <el-icon><ChatDotRound /></el-icon>
                          <span>回复 {{ item.subReplyCount || 0 }}</span>
                        </button>
                        </div>
                      </div>
                    </div>
                  </div>
                  <div class="comment-sub-replies">
                    <SubReplyArea
                      :reply-id="item.articleReply.id"
                      :article-id="article.id"
                      :refresh-token="subReplyRefreshTokens[item.articleReply.id] || 0"
                      :sub-reply-count="item.subReplyCount || 0"
                      read-only
                      @reply="startReplyToSub"
                      @open-shop="openCommentShopDetail"
                    />
                  </div>
                </div>
                <el-empty
                  v-if="replies.length === 0"
                  :description="isQuestion ? '等待第一条认真回答' : '期待你的精彩评论'"
                  :image="emptyCommentIconUrl"
                  :image-size="120"
                />
              </div>
            </el-scrollbar>

            <div class="interaction-footer interaction-footer-stacked">
              <div v-if="isQuestionClosed" class="question-closed-notice">
                <span>问题已关闭</span>
                <small>仍可点赞、收藏和查看已有回答</small>
              </div>
              <template v-else>
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
                  :key="`img-${img.mediaUrl}`"
                  class="reply-pending-card"
                >
                  <el-image :src="img.mediaUrl" fit="cover" class="reply-pending-thumb">
                    <template #error>
                      <div class="article-image-error" role="img" aria-label="图片加载失败">
                        <el-icon><PictureFilled /></el-icon>
                      </div>
                    </template>
                  </el-image>
                  <button type="button" class="reply-pending-remove" aria-label="移除" @click="removePendingImage(idx)">
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
                  class="comment-upload-btn"
                  title="上传图片"
                  aria-label="上传图片"
                  @click.stop="triggerReplyImagePick"
                >
                  <el-icon><Picture /></el-icon>
                </button>
                <div class="comment-input-wrap comment-input-full">
                <el-input
                  v-model="replyContent"
                  :placeholder="replyPlaceholder"
                  class="red-input red-input-tall"
                  @keyup.enter="submitReply"
                >
                  <template #suffix>
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
                  </template>
                </el-input>
                </div>
                <button
                  type="button"
                  class="comment-send-btn"
                  :class="{ 'is-disabled': !canSubmitReply }"
                  :disabled="!canSubmitReply"
                  @click="submitReply"
                >
                  发送
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
              </template>
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
                <el-button
                  class="action-item share-action-btn"
                  :class="{ 'share-action-btn--copied': shareCopied }"
                  @click="handleShare"
                >
                  <span v-if="shareCopied" class="share-copied-text">已复制</span>
                  <el-icon v-else :size="24"><Share /></el-icon>
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
      v-model="favoriteDialogVisible"
      title="添加到收藏夹"
      width="420px"
      append-to-body
      :z-index="4000"
      class="red-dialog favorite-dialog"
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
              :label="f.name"
              :value="f.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button
          type="primary"
          class="favorite-dialog-confirm"
          :loading="favoriteSaving"
          @click="confirmFavorite"
        >确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup src="./ArticleDetail.js"></script>

<style scoped src="@/assets/styles/article.css"></style>
<style scoped src="@/assets/styles/article-detail-owner.css"></style>
<style lang="scss" src="./ArticleDetail.scss"></style>
<style src="@/assets/styles/article-detail-modal-global.css"></style>
<style src="@/assets/styles/favorite-folder-select.css"></style>
