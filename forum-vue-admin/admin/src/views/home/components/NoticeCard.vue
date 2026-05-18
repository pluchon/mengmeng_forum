<template>
  <a-card title="通知公告" :bordered="false" :body-style="{ paddingTop: 0 }" class="g-base-card notice-card">
    <template #extra>
      <a-link @click="goNoticeManage">更多</a-link>
    </template>

    <a-spin :loading="loading" style="width: 100%">
      <a-empty v-if="!loading && displayList.length === 0" description="暂无已发布公告" />

      <div v-else class="notice-card__list">
        <div v-for="(item, index) in displayList" :key="item.id" class="notice-card__item">
          <div class="notice-card__date" :class="`date--${index % 2 === 0 ? 'blue' : 'orange'}`">
            <div class="date-week">{{ weekLabel(item.updateTime) }}</div>
            <div class="date-day">{{ dayNum(item.updateTime) }}</div>
          </div>

          <div class="notice-card__content">
            <div class="notice-card__header">
              <div class="notice-card__header-left">
                <p class="notice-card__title g-line-1">{{ item.title }}</p>
                <p class="notice-card__desc g-line-1">{{ item.subtitle || '—' }}</p>
              </div>

              <div class="notice-card__header-right">
                <a-tag v-if="item.pinTop === 1" color="orangered" size="small">置顶</a-tag>
              </div>
            </div>

            <div class="notice-card__footer">
              <span class="notice-card__author">论坛公告</span>
              <span class="notice-card__time">{{ item.updateTime }}</span>
            </div>
          </div>
        </div>
      </div>
    </a-spin>
  </a-card>
</template>

<script lang="ts" setup>
import type { NoticePreviewItem } from '@/apis/dashboard/workbench'
import Dayjs from 'dayjs'
import { useRouter } from 'vue-router'

const props = withDefaults(
  defineProps<{
    items: NoticePreviewItem[]
    loading?: boolean
  }>(),
  {
    items: () => [],
    loading: false
  }
)

const router = useRouter()

const displayList = computed(() => props.items ?? [])

const weekDays = ['日', '一', '二', '三', '四', '五', '六']

function weekLabel(timeStr: string) {
  const d = Dayjs(timeStr)
  if (!d.isValid())
    return '—'
  return `周${weekDays[d.day()]}`
}

function dayNum(timeStr: string) {
  const d = Dayjs(timeStr)
  if (!d.isValid())
    return '—'
  return Number(d.format('D'))
}

function goNoticeManage() {
  router.push('/content/notice')
}
</script>

<style lang="scss" scoped>
.notice-card {
  &__item {
    box-sizing: border-box;
    display: flex;
    padding: 8px 0;
    overflow: hidden;
    font-size: 12px;
    cursor: pointer;
  }

  &__date {
    display: flex;
    flex-shrink: 0;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    width: 56px;
    height: 56px;
    border-radius: 8px;

    &.date--blue {
      color: #377DFF;
      background-color: rgb(55 125 255 / 10%);
    }

    &.date--orange {
      color: #FAAD14;
      background-color: rgb(250 173 20 / 15%);
    }

    .date-week {
      font-size: 12px;
      font-weight: 500;
    }

    .date-day {
      font-size: 18px;
      font-weight: 600;
      line-height: 1.2;
    }
  }

  &__content {
    display: flex;
    flex: 1;
    flex-direction: column;
    margin-left: 16px;
    overflow: hidden;
  }

  &__header {
    display: flex;
  }

  &__header-left {
    flex: 1;
    overflow: hidden;
  }

  &__header-right {
    display: flex;
    flex-direction: column;
    flex-shrink: 0;
    gap: 4px;
    align-items: flex-end;
  }

  &__title {
    font-size: 14px;
    font-weight: 500;
    line-height: 1.5;
    color: var(--color-text-1);
  }

  &__desc {
    margin: 8px 0;
    font-size: 13px;
    color: var(--color-text-2);
  }

  &__footer {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    align-items: center;
  }

  &__author,
  &__time {
    color: var(--color-text-4);
    white-space: nowrap;
  }
}
</style>
