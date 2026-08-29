<template>
  <div class="sub-reply-wrap">
    <div v-if="total > 0" class="sub-reply-toolbar">
      <button type="button" class="sub-expand-inline" @click="toggle">
        <el-icon class="sub-expand-icon"><component :is="expanded ? CaretTop : CaretBottom" /></el-icon>
        <span>{{ expanded ? '收起回复' : `展开 ${total} 条回复` }}</span>
      </button>
    </div>

    <el-collapse-transition>
      <div v-if="expanded && total > 0" class="sub-nested-panel">
        <div
          v-for="sub in subList"
          :key="sub.subReply.id"
          class="sub-item"
          :class="{ 'sub-item--accepted': !!sub.accepted }"
        >
          <div class="sub-item-head">
            <div class="sub-item-author">
              <div
                v-if="sub.postUser?.id"
                class="sub-item-avatar-link"
                role="link"
                tabindex="0"
                @click="emitProfile(sub.postUser.id)"
                @keydown.enter.prevent="emitProfile(sub.postUser.id)"
              >
                <UserAvatarVip
                  :size="20"
                  :src="sub.postUser?.avatarUrl || defaultAvatar"                />
              </div>
              <UserAvatarVip
                v-else
                :size="20"
                :src="sub.postUser?.avatarUrl || defaultAvatar"              />
              <span
                class="sub-user-link"
                :title="sub.postUser?.nickname || '用户'"
                role="link"
                tabindex="0"
                @click="emitProfile(sub.postUser?.id)"
                @keydown.enter.prevent="emitProfile(sub.postUser?.id)"
              >{{ compactNickname(sub.postUser?.nickname) }}</span>
              <el-tag
                v-if="isAuthorReply(sub)"
                size="small"
                type="danger"
                effect="plain"
                class="up-tag sub-up-tag"
              >
                UP
              </el-tag>
              <template v-if="shouldShowReplyMention(sub)">
                <span class="sub-reply-to"> 回复 </span>
                <span class="sub-reply-target" :title="sub.replyUserNickname">@{{ compactNickname(sub.replyUserNickname) }}</span>
              </template>
            </div>
            <span v-if="resolveIpRegion(sub)" class="sub-item-meta-ip">
              {{ resolveIpRegion(sub) }}
            </span>
          </div>
          <div class="sub-item-content">
            <span v-if="isViolated(sub)" class="sub-violated-placeholder">该回复因违规已屏蔽</span>
            <CommentExpandableText v-else :content="sub.subReply.content" />
          </div>
          <CommentReplyMediaDisplay
            v-if="!isViolated(sub)"
            :media-list="sub.mediaList"
            @open-shop="(id) => emit('open-shop', id)"
          />
          <div class="sub-item-actions">
            <div class="sub-item-actions__left">
              <button type="button" class="comment-action-btn" @click="toggleSubLike(sub)">
                <LikeCountIcon class="comment-like-icon" :filled="sub.liked" />
                <span :class="{ 'is-liked': sub.liked }">{{ sub.subReply.likeCount || 0 }}</span>
              </button>
              <button type="button" class="comment-action-btn" @click="emitReply(sub)">
                <el-icon :size="12"><ChatDotRound /></el-icon>
                <span>回复</span>
              </button>
              <button
                v-if="canAccept && !isAuthorReply(sub) && !sub.accepted"
                type="button"
                class="comment-action-btn comment-action-btn--accept"
                :disabled="acceptSaving"
                @click="emitAccept(sub)"
              >
                采纳
              </button>
            </div>
            <div class="sub-item-actions__right">
              <span v-if="sub.accepted" class="sub-accepted-tag">已采纳</span>
              <span class="sub-item-actions__time">{{ formatSubTime(sub.subReply?.createTime) }}</span>
              <button
                v-if="!isOwnSub(sub) && !isAuthorReply(sub)"
                type="button"
                class="comment-action-btn comment-action-btn--report"
                aria-label="举报回复"
                title="举报"
                @click="emitReport(sub)"
              >
                <el-icon :size="13"><Flag /></el-icon>
              </button>
            </div>
          </div>
        </div>

        <el-empty v-if="subList.length === 0" description="暂无回复" :image-size="40" />

        <AppPagination
          v-model:current-page="page"
          size="small"
          :total="total"
          :page-size="pageSize"
          :pager-count="5"
          :show-jumper="false"
          class="sub-pager"
          @current-change="loadSubs"
        />
      </div>
    </el-collapse-transition>
  </div>
</template>

<script setup src="@scripts/components/article/SubReplyArea.js"></script>

<style scoped src="@/assets/styles/article.css"></style>
<style scoped lang="scss" src="./SubReplyArea.scss"></style>
