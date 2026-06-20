<template>
  <div class="sub-reply-wrap">
    <div class="sub-reply-toolbar">
      <button v-if="total > 0" type="button" class="sub-expand-inline" @click="toggle">
        <el-icon class="sub-expand-icon"><component :is="expanded ? CaretTop : CaretBottom" /></el-icon>
        <span>{{ expanded ? '收起回复' : `展开 ${total} 条回复` }}</span>
      </button>
      <span v-else class="sub-no-nested">暂无楼中楼回复</span>
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
                  <template v-if="sub.replyUserNickname">
                    <el-text type="info" size="small">回复</el-text>
                    <el-text type="primary" size="small">@{{ sub.replyUserNickname }}</el-text>
                  </template>
                  <el-text size="small" class="sub-text-content">: {{ sub.subReply.content }}</el-text>
                </el-space>
              </div>
              <div class="sub-item-meta">
                <el-text type="info" size="small">{{ sub.subReply.createTime }}</el-text>
                <IpRegionLabel :region="sub.subReply?.ipRegion" />
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

    <div v-if="!readOnly" class="sub-input-box" :class="{ 'vip-sub-input-gold': vipGoldFocus }">
      <el-input
        ref="subInputRef"
        v-model="inputContent"
        :placeholder="replyTarget ? '回复 @' + replyTarget.postUser?.nickname : '写下你的回复...'"
        size="small"
        class="sub-input"
        @keyup.enter="submitSub"
      >
        <template v-if="replyTarget" #prefix>
          <el-tag size="small" closable type="info" @close="replyTarget = null">
            @{{ replyTarget.postUser?.nickname }}
          </el-tag>
        </template>
        <template #suffix>
          <img
            :src="sendIconUrl"
            alt=""
            class="sub-plain-svg sub-plain-svg--send"
            :class="{ 'is-disabled': !inputContent.trim() || submitting }"
            role="button"
            tabindex="0"
            aria-label="发送"
            @click="submitSub"
            @keydown.enter.prevent="submitSub"
          />
        </template>
      </el-input>
    </div>
  </div>
</template>

<script setup>
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import IpRegionLabel from '@/components/common/IpRegionLabel.vue'
import sendIconUrl from '@/assets/svg/发送.svg?url'
import { useSubReplyArea } from '@scripts/components/article/SubReplyArea'

const props = defineProps({
  replyId: { type: [Number, String], required: true },
  articleId: { type: [Number, String], required: true },
  /** 会员：楼中楼输入框在获得焦点时显示金色描边 */
  vipGoldFocus: { type: Boolean, default: false },
  /** 只读展示历史楼中楼，不显示回复输入框 */
  readOnly: { type: Boolean, default: false },
})

const {
  CaretBottom,
  CaretTop,
  defaultAvatar,
  expanded,
  inputContent,
  loadSubs,
  openReplyTo,
  page,
  pageSize,
  replyTarget,
  setReplyTarget,
  subInputRef,
  subList,
  submitSub,
  submitting,
  toggle,
  total,
  goProfile,
} = useSubReplyArea(props)

defineExpose({ openReplyTo })
</script>

<style scoped src="@/assets/styles/article.css"></style>

<style scoped>
.sub-plain-svg {
  display: inline-block;
  vertical-align: middle;
  cursor: pointer;
  user-select: none;
}

.sub-plain-svg--send {
  width: 16px;
  height: 16px;
  margin-right: 2px;
  opacity: 0.85;
}

.sub-plain-svg--send:hover:not(.is-disabled) {
  opacity: 1;
}

.sub-plain-svg--send.is-disabled {
  opacity: 0.28;
  cursor: default;
  pointer-events: none;
}

.sub-item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}

.sub-user-link {
  cursor: pointer;
}
</style>
