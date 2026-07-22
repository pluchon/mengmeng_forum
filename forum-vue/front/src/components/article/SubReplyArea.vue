<template>
  <div class="sub-reply-wrap">
    <div class="sub-reply-toolbar">
      <button v-if="total > 0" type="button" class="sub-expand-inline" @click="toggle">
        <el-icon class="sub-expand-icon"><component :is="expanded ? CaretTop : CaretBottom" /></el-icon>
        <span>{{ expanded ? '收起回复' : `展开 ${total} 条回复` }}</span>
      </button>
    </div>

    <el-collapse-transition>
      <div v-if="expanded && total > 0" class="sub-nested-panel">
        <div v-for="sub in subList" :key="sub.subReply.id" class="sub-item">
          <el-row :gutter="12" justify="start">
            <el-col :span="1.5">
              <span class="sub-user-link" role="button" tabindex="0" @click="goProfile(sub.postUser?.id)">
                <UserAvatarVip
                  :size="24"
                  :src="sub.postUser?.avatarUrl || defaultAvatar"
                  :vip-tier="Number(sub.postUser?.vipTier) || 0"
                  :vip-expire-at="sub.postUser?.vipExpireAt"
                />
              </span>
            </el-col>
            <el-col :span="21">
              <div class="sub-msg-body">
                <el-space wrap :size="4">
                  <el-text
                    type="primary"
                    strong
                    size="small"
                    class="sub-user-link"
                    @click="goProfile(sub.postUser?.id)"
                  >{{ sub.postUser?.nickname }}</el-text>
                  <template v-if="shouldShowReplyMention(sub)">
                    <el-text type="info" size="small">回复</el-text>
                    <el-text type="primary" size="small">@{{ sub.replyUserNickname }}</el-text>
                  </template>
                  <el-text size="small" class="sub-text-content">: {{ sub.subReply.content }}</el-text>
                </el-space>
                <CommentReplyMediaDisplay
                  :media-list="sub.mediaList"
                  @open-shop="(id) => emit('open-shop', id)"
                />
              </div>
              <div class="sub-item-meta">
                <div class="sub-item-meta-left">
                  <el-text type="info" size="small">{{ sub.subReply.createTime }}</el-text>
                  <IpRegionLabel :region="sub.subReply?.ipRegion" />
                </div>
                <div class="sub-item-meta-actions">
                  <button type="button" class="comment-action-btn" @click="toggleSubLike(sub)">
                    <LikeCountIcon class="comment-like-icon" :filled="sub.liked" />
                    <span :class="{ 'is-liked': sub.liked }">{{ sub.subReply.likeCount || 0 }}</span>
                  </button>
                  <button type="button" class="comment-action-btn" @click="emitReply(sub)">
                    <el-icon><ChatDotRound /></el-icon>
                    <span>回复</span>
                  </button>
                </div>
              </div>
            </el-col>
          </el-row>
        </div>

        <el-empty v-if="subList.length === 0" description="暂无回复" :image-size="40" />

        <el-pagination
          v-if="total > pageSize"
          v-model:current-page="page"
          :total="total"
          :page-size="pageSize"
          layout="prev, pager, next"
          small
          hide-on-single-page
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
