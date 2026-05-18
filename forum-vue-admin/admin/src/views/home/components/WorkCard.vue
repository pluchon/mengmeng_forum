<template>
  <a-card title="工作台" :bordered="false" size="medium" class="g-base-card work-card">
    <a-row align="center" wrap :gutter="[{ xs: 0, sm: 14, md: 14, lg: 14, xl: 14, xxl: 14 }, 16]"
      class="work-card__content">
      <a-col :xs="24" :sm="24" :md="14" :lg="16" :xl="16" :xxl="18">
        <a-space size="medium">
          <a-avatar :size="68">
            <img :src="userStore.avatar" />
          </a-avatar>
          <div class="work-card__welcome">
            <p class="work-card__hello">
              <span>{{ goodTimeText() }}！{{ userStore.name }}，欢迎进入论坛管理后台。</span>
            </p>
          </div>
        </a-space>
      </a-col>

      <a-col :xs="24" :sm="24" :md="10" :lg="8" :xl="8" :xxl="6">
        <a-row justify="end" align="center">
          <a-statistic :value="articleCount" :value-from="0" :start="true" animation :precision="0">
            <template #title>
              <a-space>
                <GiSvgIcon name="icon-num"></GiSvgIcon>
                <span>帖子数</span>
              </a-space>
            </template>
          </a-statistic>
        </a-row>
      </a-col>
    </a-row>
  </a-card>
</template>

<script setup lang="ts">
import type { WorkbenchSummary } from '@/apis/dashboard/workbench'
import { useUserStore } from '@/stores'
import { goodTimeText } from '@/utils'

const props = defineProps<{
  /** 由首页统一请求工作台接口后传入 */
  summary: WorkbenchSummary | null
}>()

const userStore = useUserStore()

const articleCount = computed(() => Number(props.summary?.articleCount) || 0)
</script>

<style lang="scss" scoped>
:deep(.arco-statistic-title) {
  margin-bottom: 0;
}

.work-card {
  &__content {
    padding: 8px 20px;
  }

  &__welcome {
    margin: 8px 0;
    line-height: 1.38;
    color: var(--color-text-3);
  }

  &__hello {
    margin-bottom: 6px;
    font-size: 1.25rem;
    color: var(--color-text-1);
  }
}
</style>
