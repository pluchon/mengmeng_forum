<template>
  <div class="g-page home">
    <a-row :gutter="[14, 14]">
      <a-col v-bind="leftColProps">
        <WorkCard class="g-mb" :summary="workbench"></WorkCard>

        <TrendCard
          class="g-mb"
          :categories="lotteryTrend.categories"
          :draws="lotteryTrend.draws"
          :activity-title="lotteryTrend.activityTitle"
        ></TrendCard>
        <OverviewCard class="g-mb" :summary="workbench" :loading="workbenchLoading"></OverviewCard>
      </a-col>
      <a-col v-bind="rightColProps">
        <NoticeCard :items="noticePreview" :loading="workbenchLoading"></NoticeCard>
        <UserFeedbackCard />
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import type { NoticeRow } from '@/apis/content/notice'
import { getNoticeList } from '@/apis/content/notice'
import { getWorkbenchSummary } from '@/apis/dashboard/workbench'
import type { AiUsageTrends, AiUsageTrendsBundle, LotteryDrawTrend, NoticePreviewItem, WorkbenchSummary } from '@/apis/dashboard/workbench'
import { useUserStore } from '@/stores'
import NoticeCard from './components/NoticeCard.vue'
import UserFeedbackCard from './components/UserFeedbackCard.vue'
import OverviewCard from './components/OverviewCard.vue'
import TrendCard from './components/TrendCard.vue'
import WorkCard from './components/WorkCard.vue'

defineOptions({ name: 'Home' })

const userStore = useUserStore()
const workbench = ref<WorkbenchSummary | null>(null)
const workbenchLoading = ref(true)

const noticePreview = computed<NoticePreviewItem[]>(() => workbench.value?.noticePreview ?? [])

const lotteryTrend = computed<LotteryDrawTrend>(() => {
  const d = workbench.value?.lotteryDrawTrend
  return {
    activityId: d?.activityId != null ? Number(d.activityId) : null,
    activityTitle: d?.activityTitle ?? '',
    categories: Array.isArray(d?.categories) ? d.categories : [],
    draws: Array.isArray(d?.draws) ? d.draws.map(n => Number(n) || 0) : []
  }
})

function emptyAiUsageTrends(): AiUsageTrendsBundle {
  const emptyTrends = (): AiUsageTrends => ({
    day: { categories: [], series: [] },
    week: { categories: [], series: [] },
    month: { categories: [], series: [] }
  })
  return { text: emptyTrends(), image: emptyTrends() }
}

/** 工作台未带 noticePreview 时（旧版后端），从公告分页接口补全已发布公告 */
async function loadPublishedNoticePreview(): Promise<NoticePreviewItem[]> {
  const lr = await getNoticeList({ pageNum: 1, pageSize: 24 })
  const pack = lr?.data
  const rows = (!Array.isArray(pack) ? pack?.records : pack) as NoticeRow[] | undefined
  const list = Array.isArray(rows) ? rows : []
  return list
    .filter(r => r.publishState === 1 && r.deleteState === 0)
    .slice(0, 8)
    .map(r => ({
      id: Number(r.id),
      title: r.title,
      subtitle: r.subtitle ?? null,
      updateTime: r.updateTime,
      pinTop: r.pinTop === 1 ? 1 : 0,
      noticeKind: r.noticeKind
    }))
}

onMounted(async () => {
  try {
    const res = await getWorkbenchSummary()
    if (res?.data) {
      const d = res.data as Partial<WorkbenchSummary>
      let np = d.noticePreview
      if (!Array.isArray(np)) {
        np = await loadPublishedNoticePreview()
      }
      workbench.value = {
        articleCount: Number(d.articleCount) || 0,
        totalUserCount: Number(d.totalUserCount) || 0,
        memberUserCount: Number(d.memberUserCount) || 0,
        interactionCount: Number(d.interactionCount) || 0,
        nickname: d.nickname ?? '',
        avatar: d.avatar ?? '',
        noticePreview: np,
        aiUsageTrends: d.aiUsageTrends ?? emptyAiUsageTrends(),
        lotteryDrawTrend: d.lotteryDrawTrend
      }
      userStore.patchDisplayProfile({
        nickname: workbench.value.nickname,
        avatar: workbench.value.avatar
      })
    } else {
      workbench.value = null
    }
  } catch {
    workbench.value = null
  } finally {
    workbenchLoading.value = false
  }
})

const leftColProps = { xs: 24, sm: 24, md: 24, lg: 16, xl: 17, xxl: 17 }
const rightColProps = { xs: 24, sm: 24, md: 24, lg: 8, xl: 7, xxl: 7 }
</script>

<style lang="scss" scoped>
.home {
  .backtop-icon {
    cursor: pointer;
  }
}
</style>
