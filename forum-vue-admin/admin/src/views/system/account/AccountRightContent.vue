<template>
  <div class="account-right-content">
    <a-tabs v-model:active-key="activeTab">
      <a-tab-pane key="overview" title="工作台概览">
        <a-spin :loading="loading" class="account-right-content__spin">
          <a-descriptions v-if="summary" :column="1" size="large" bordered>
            <a-descriptions-item label="全站帖子（未删）">
              {{ summary.articleCount }}
            </a-descriptions-item>
            <a-descriptions-item label="注册会员">
              {{ summary.memberUserCount }}
            </a-descriptions-item>
            <a-descriptions-item label="全站用户（含管理员）">
              {{ summary.totalUserCount }}
            </a-descriptions-item>
            <a-descriptions-item label="累计互动（浏览30%+点赞40%+评论10%+收藏20%）">
              {{ summary.interactionCount }}
            </a-descriptions-item>
          </a-descriptions>
          <a-empty v-else description="暂无数据" />
          <a-typography-paragraph type="secondary" class="account-right-content__note">
            以上为当前时刻从服务端拉取的站点统计；刷新本页可更新数字。
          </a-typography-paragraph>
        </a-spin>
      </a-tab-pane>
      <a-tab-pane key="access" title="权限与说明">
        <a-typography-title :heading="6">菜单权限</a-typography-title>
        <p class="account-right-content__lead">
          左侧导航由角色绑定菜单决定；下列为当前账号后端返回的权限标识（节选展示）。
        </p>
        <a-spin :loading="permLoading">
          <div v-if="permissionTags.length" class="account-right-content__tags">
            <a-tag v-for="p in permissionTags" :key="p" size="small" class="perm-tag">{{ p }}</a-tag>
          </div>
          <a-empty v-else description="未返回细粒度权限（可能为超级管理员全量放行）" />
        </a-spin>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
import { getWorkbenchSummary } from '@/apis/dashboard/workbench'
import type { WorkbenchSummary } from '@/apis/dashboard/workbench'
import { useUserStore } from '@/stores'

defineOptions({ name: 'AccountRightContent' })

const userStore = useUserStore()
const activeTab = ref('overview')
const loading = ref(true)
const permLoading = ref(false)
const summary = ref<WorkbenchSummary | null>(null)

const permissionTags = computed(() => {
  const list = userStore.permissions || []
  return list.slice(0, 120)
})

onMounted(async () => {
  loading.value = true
  permLoading.value = true
  try {
    const res = await getWorkbenchSummary()
    summary.value = res?.data ?? null
  } catch {
    summary.value = null
  } finally {
    loading.value = false
    permLoading.value = false
  }
})
</script>

<style lang="scss" scoped>
.account-right-content {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 320px;
  overflow: hidden;
  background-color: var(--color-bg-1);
  border-radius: 8px;

  &__spin {
    width: 100%;
    padding: 16px 20px 20px;
  }

  &__note {
    margin-top: 14px;
    margin-bottom: 0;
  }

  &__lead {
    margin: 0 0 12px;
    font-size: 13px;
    color: var(--color-text-2);
  }

  &__tags {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    padding: 4px 20px 20px;
  }

  .perm-tag {
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}
</style>
