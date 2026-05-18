<template>
  <a-card title="数据概览" :bordered="false" class="g-base-card overview-card">
    <a-spin :loading="loading" style="width: 100%">
      <a-row :gutter="[14, 14]">
        <a-col v-for="(item, index) in statistics" :key="index" :xs="12" :sm="12" :md="6">
          <div class="overview-card__item">
            <a-statistic :title="item.title" :value="item.value" :precision="0" animation />
          </div>
        </a-col>
      </a-row>

      <div class="overview-card__ai">
        <div class="overview-card__ai-head">
          <span class="overview-card__ai-title">AI 调用趋势 · 文本模型</span>
          <a-radio-group v-model="granularity" type="button" size="small">
            <a-radio value="day">日</a-radio>
            <a-radio value="week">周</a-radio>
            <a-radio value="month">月</a-radio>
          </a-radio-group>
        </div>
        <VCharts
          v-if="hasTextSeries"
          :option="textOption"
          :theme="theme"
          autoresize
          class="overview-card__chart"
        />
        <a-empty v-else description="暂无文本模型调用统计" />
      </div>

      <div class="overview-card__ai overview-card__ai--image">
        <div class="overview-card__ai-head">
          <span class="overview-card__ai-title">AI 调用趋势 · 生图模型</span>
        </div>
        <VCharts
          v-if="hasImageSeries"
          :option="imageOption"
          :theme="theme"
          autoresize
          class="overview-card__chart"
        />
        <a-empty v-else description="暂无生图模型调用统计" />
      </div>
    </a-spin>
  </a-card>
</template>

<script lang="ts" setup>
import type { AiChartPayload, AiUsageTrends, AiUsageTrendsBundle, WorkbenchSummary } from '@/apis/dashboard/workbench'
import { useTheme } from '@/hooks'
import type { EChartsOption } from 'echarts'
import VCharts from 'vue-echarts'

const props = withDefaults(
  defineProps<{
    summary: WorkbenchSummary | null
    loading?: boolean
  }>(),
  {
    summary: null,
    loading: false
  }
)

const { isDark } = useTheme()

const granularity = ref<'day' | 'week' | 'month'>('day')

const statistics = computed(() => {
  const s = props.summary
  return [
    { title: '注册会员', value: Number(s?.memberUserCount) || 0 },
    { title: '全站用户', value: Number(s?.totalUserCount) || 0 },
    { title: '全站帖子', value: Number(s?.articleCount) || 0 },
    { title: '累计互动', value: Number(s?.interactionCount) || 0 }
  ]
})

function emptyTrends(): AiUsageTrends {
  return { day: { categories: [], series: [] }, week: { categories: [], series: [] }, month: { categories: [], series: [] } }
}

function normalizeBundle(raw: WorkbenchSummary['aiUsageTrends'] | undefined): AiUsageTrendsBundle {
  if (!raw)
    return { text: emptyTrends(), image: emptyTrends() }
  if ('text' in raw && raw.text)
    return raw
  const legacy = raw as unknown as AiUsageTrends
  if (legacy?.day)
    return { text: legacy, image: emptyTrends() }
  return { text: emptyTrends(), image: emptyTrends() }
}

function pickChart(trends: AiUsageTrends): AiChartPayload {
  if (granularity.value === 'week')
    return trends.week ?? { categories: [], series: [] }
  if (granularity.value === 'month')
    return trends.month ?? { categories: [], series: [] }
  return trends.day ?? { categories: [], series: [] }
}

const trendsBundle = computed(() => normalizeBundle(props.summary?.aiUsageTrends))

const textPayload = computed(() => pickChart(trendsBundle.value.text))
const imagePayload = computed(() => pickChart(trendsBundle.value.image))

const hasTextSeries = computed(() => (textPayload.value.series?.length ?? 0) > 0)
const hasImageSeries = computed(() => (imagePayload.value.series?.length ?? 0) > 0)

const lineColors = ['#165dff', '#00b42a', '#ff7d00', '#722ed1', '#14c9c9', '#f5319d', '#d4537e']

const theme = computed(() => (isDark.value ? 'dark' : undefined))

function buildOption(pl: AiChartPayload): EChartsOption {
  const categories = pl.categories ?? []
  const seriesList = pl.series ?? []
  const textColor = isDark.value ? 'rgba(255,255,255,0.55)' : 'rgba(0,0,0,0.45)'
  return {
    backgroundColor: 'transparent',
    color: lineColors,
    grid: {
      left: 4,
      right: 8,
      top: 28,
      bottom: 4,
      containLabel: true
    },
    legend: {
      type: 'scroll',
      top: 0,
      textStyle: { color: textColor }
    },
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: categories,
      boundaryGap: false,
      axisLine: { lineStyle: { color: textColor } },
      axisLabel: { color: textColor }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { type: 'dashed', opacity: 0.35 } },
      axisLabel: { color: textColor }
    },
    series: seriesList.map((s, i) => ({
      name: s.name,
      type: 'line' as const,
      smooth: true,
      showSymbol: categories.length <= 14,
      data: s.data ?? [],
      lineStyle: { width: 2 },
      emphasis: { focus: 'series' as const },
      color: lineColors[i % lineColors.length]
    }))
  }
}

const textOption = computed(() => buildOption(textPayload.value))
const imageOption = computed(() => buildOption(imagePayload.value))
</script>

<style lang="scss" scoped>
:deep(.arco-statistic-content .arco-statistic-value) {
  line-height: 1;
  color: rgb(var(--primary-6));
}

.overview-card {
  background-color: var(--color-bg-1);

  &__item {
    box-sizing: border-box;
    padding: var(--padding);
    background-color: var(--color-fill-1);
    border-radius: 8px;
  }

  &__ai {
    margin-top: 16px;
    padding-top: 8px;
    border-top: 1px solid var(--color-border-1);

    &--image {
      margin-top: 12px;
    }
  }

  &__ai-head {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
  }

  &__ai-title {
    font-size: 14px;
    font-weight: 500;
    color: var(--color-text-1);
  }

  &__chart {
    width: 100%;
    height: 240px;
  }
}
</style>
