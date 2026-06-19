<template>
  <a-card :title="cardTitle" :bordered="false" class="g-base-card trend-card">
    <VCharts :option="option" :theme="theme" autoresize :style="{ width: '100%', height: '120px' }" />
  </a-card>
</template>

<script lang="ts" setup>
import type { EChartsOption } from 'echarts'
import { graphic } from 'echarts'
import VCharts from 'vue-echarts'
import { useTheme } from '@/hooks'

defineOptions({ name: 'TrendChart' })

const props = withDefaults(
  defineProps<{
    /** 近 24 个整点标签，如 14:00 */
    categories?: string[]
    /** 各整点抽奖次数（单抽+十连每抽计 1） */
    draws?: number[]
    /** 当前趋势对应的活动标题 */
    activityTitle?: string
  }>(),
  {
    categories: () => [],
    draws: () => [],
    activityTitle: ''
  }
)

const { isDark } = useTheme()

const cardTitle = computed(() => {
  const t = (props.activityTitle ?? '').trim()
  return t ? `抽奖活动的趋势分析（${t}）` : '抽奖活动的趋势分析'
})

const option = computed<EChartsOption>(() => {
  const xData = props.categories?.length ? props.categories : ['--']
  const yData = props.draws?.length ? props.draws : [0]
  const lastIdx = Math.max(0, xData.length - 1)
  return {
    backgroundColor: 'transparent',
    grid: {
      left: 0,
      right: 0,
      top: 8,
      bottom: 0,
      containLabel: true
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line', lineStyle: { color: '#1890ff', type: 'dashed' } }
    },
    xAxis: {
      type: 'category',
      data: xData,
      boundaryGap: false,
      axisLine: {
        show: false
      },
      axisTick: { show: false },
      axisLabel: {
        formatter(value: string, idx: number) {
          if (idx === lastIdx) return ''
          return value
        }
      }
    },
    yAxis: {
      type: 'value',
      splitLine: {
        show: false
      },
      axisLine: { show: false }
    },
    series: [
      {
        name: '抽奖次数',
        type: 'line',
        data: yData,
        smooth: true,
        showSymbol: false,
        lineStyle: {
          width: 2,
          color: '#1890ff',
          shadowBlur: 10,
          shadowColor: 'rgba(24, 144, 255, 0.4)',
          shadowOffsetY: 3
        },
        areaStyle: {
          color: new graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(24, 144, 255, 0.2)' },
            { offset: 1, color: 'rgba(24, 144, 255, 0)' }
          ])
        }
      }
    ]
  }
})

const theme = computed(() => (isDark.value ? 'dark' : undefined))
</script>
