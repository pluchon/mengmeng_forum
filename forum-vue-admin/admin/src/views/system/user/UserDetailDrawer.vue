<template>
  <a-drawer v-model:visible="visible" title="用户详情" width="auto">
    <a-descriptions :column="{ xs: 1, sm: 1, md: 2 }" bordered size="large">
      <a-descriptions-item label="用户名">
        {{ user?.username }}
      </a-descriptions-item>
      <a-descriptions-item label="昵称">
        {{ user?.nickname }}
      </a-descriptions-item>
      <a-descriptions-item label="类型">
        {{ userTypeText }}
      </a-descriptions-item>
      <a-descriptions-item label="状态">
        <a-tag v-if="user?.status === '1'" color="green">
          正常
        </a-tag>
        <a-tag v-else color="orangered">
          禁言
        </a-tag>
      </a-descriptions-item>
      <a-descriptions-item label="性别">
        <span v-if="user?.gender === '1'">男</span>
        <span v-else-if="user?.gender === '2'">女</span>
        <span v-else>保密</span>
      </a-descriptions-item>
      <a-descriptions-item label="手机号">
        {{ user?.phone || '—' }}
      </a-descriptions-item>
      <a-descriptions-item label="邮箱">
        {{ user?.email || '—' }}
      </a-descriptions-item>
      <a-descriptions-item label="创建时间">
        {{ user?.createTime }}
      </a-descriptions-item>
      <a-descriptions-item label="管理员标签" :span="2">
        {{ user?.description || '—' }}
      </a-descriptions-item>
    </a-descriptions>
  </a-drawer>
</template>

<script lang="ts" setup>
import type * as T from '@/apis/system/user'
import { baseAPI } from '@/apis/system/user'
import { isVipActive, vipTierLabel } from '@/utils/vip'

const visible = ref(false)
const userId = ref('')
const user = ref<T.ListItem | null>()

const userTypeText = computed(() => {
  const u = user.value
  if (!u)
    return '—'
  if (u.forumAdmin)
    return '管理员'
  if (isVipActive(u.vipTier, u.vipExpireAt)) {
    const tier = vipTierLabel(u.vipTier)
    return tier ? `会员用户(${tier})` : '会员用户'
  }
  return '普通用户'
})

async function getDetail() {
  const res = await baseAPI.getDetail({ id: userId.value })
  user.value = res.data
}

async function open(id: string) {
  userId.value = id
  visible.value = true
  await getDetail()
}

defineExpose({ open })
</script>
