<template>
  <a-drawer v-model:visible="visible" title="论坛用户详情" width="480px" unmount-on-close>
    <a-spin :loading="loading" style="width: 100%">
      <template v-if="user">
        <a-space align="start" style="margin-bottom: 16px">
          <AdminVipAvatar
            :src="user.avatarUrl"
            :vip-tier="user.vipTier"
            :vip-expire-at="user.vipExpireAt"
            :size="64"
            :fallback-text="(user.nickname || user.username || '?').slice(0, 1)"
          />
          <div>
            <div style="font-size: 16px; font-weight: 600">
              {{ user.nickname || user.username }}
            </div>
            <div style="color: var(--color-text-3); font-size: 13px">
              @{{ user.username }}
            </div>
          </div>
        </a-space>
        <a-descriptions :column="1" bordered size="large">
          <a-descriptions-item label="用户 ID">{{ user.id }}</a-descriptions-item>
          <a-descriptions-item label="发帖数">{{ user.articleCount ?? 0 }}</a-descriptions-item>
          <a-descriptions-item label="积分">{{ user.points ?? 0 }}</a-descriptions-item>
          <a-descriptions-item label="VIP 信息">{{ vipInfoText }}</a-descriptions-item>
          <a-descriptions-item label="禁言">
            <a-tag v-if="user.state === 1" color="red">禁言</a-tag>
            <a-tag v-else color="green">正常</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="管理员">
            <a-tag v-if="user.isAdmin === 1" color="orangered">是</a-tag>
            <a-tag v-else>否</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="注册时间">{{ user.createTime || '—' }}</a-descriptions-item>
        </a-descriptions>
      </template>
    </a-spin>
  </a-drawer>
</template>

<script setup lang="ts">
import type { ForumMemberPreview } from '@/apis/content/member'
import { getForumMemberPreview } from '@/apis/content/member'
import AdminVipAvatar from '@/components/AdminVipAvatar.vue'
import { formatVipInfo } from '@/utils/vip'

const props = defineProps<{ userId: string | null }>()
const visible = defineModel<boolean>('visible', { default: false })
const loading = ref(false)
const user = ref<ForumMemberPreview | null>(null)
const vipInfoText = computed(() => user.value ? formatVipInfo(user.value.vipTier, user.value.vipExpireAt) : '—')

watch(() => [visible.value, props.userId] as const, async ([v, uid]) => {
  if (!v || !uid) {
    if (!v) user.value = null
    return
  }
  loading.value = true
  try {
    const res = await getForumMemberPreview({ userId: uid })
    user.value = res?.data ?? null
  } catch {
    user.value = null
  } finally {
    loading.value = false
  }
})
</script>
