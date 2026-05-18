<template>
  <div class="points-wallet-page shell-page-scroll animate-fade-in">
    <div class="points-wallet-inner">
      <section v-loading="wallet.loading" class="points-wallet-card points-summary-card">
        <img :src="summaryCardBgUrl" alt="" class="points-summary-bg" />
        <div class="points-summary-mask" />
        <div class="points-summary-inner">
          <div class="points-summary-main">
            <div class="points-summary-label">当前余额</div>
            <div class="points-summary-balance">
              <span class="points-summary-num">{{ wallet.balance }}</span>
              <span class="points-summary-unit">积分</span>
            </div>
          </div>
          <div class="points-summary-side">
            <div class="points-summary-side-col">
              <span class="points-summary-side-label">累计签到入账</span>
              <span class="points-summary-side-value is-income">+{{ wallet.totalCheckinPoints }}</span>
            </div>
            <div class="points-summary-side-col">
              <span class="points-summary-side-label">商城累计消费</span>
              <span class="points-summary-side-value is-spend">{{ spendDisplay }}</span>
            </div>
          </div>
        </div>
      </section>

      <section class="points-wallet-card points-chart-panel">
        <div class="points-panel-body">
          <div class="points-panel-head">
            <div class="points-panel-head-left">
              <el-icon class="points-panel-head-icon"><DataLine /></el-icon>
              <span class="points-panel-head-title">{{ chartMonthTitle }}</span>
            </div>
            <div class="points-month-nav">
              <button
                v-if="canGoPrevMonth"
                type="button"
                class="points-month-nav-btn"
                :disabled="dailyLoading"
                aria-label="上一月"
                @click="prevChartMonth"
              >
                <img :src="iconPrevUrl" alt="" class="points-month-nav-icon" />
              </button>
              <button
                v-if="canGoNextMonth"
                type="button"
                class="points-month-nav-btn"
                :disabled="dailyLoading"
                aria-label="下一月"
                @click="nextChartMonth"
              >
                <img :src="iconNextUrl" alt="" class="points-month-nav-icon" />
              </button>
            </div>
          </div>

          <div class="points-chart-body">
            <div class="points-chart-stats">
              <div class="points-chart-stat-card">
                <div class="points-chart-stat-label">入账合计</div>
                <div class="points-chart-stat-value is-income">+{{ periodInTotal }}</div>
              </div>
              <div class="points-chart-stat-card">
                <div class="points-chart-stat-label">消费合计</div>
                <div class="points-chart-stat-value is-spend">-{{ periodOutTotal }}</div>
              </div>
            </div>
            <div class="points-chart-divider" aria-hidden="true" />
            <div class="points-chart-right">
              <div class="points-chart-type-tabs">
                <button
                  v-for="t in CHART_TYPES"
                  :key="t.id"
                  type="button"
                  class="points-chart-type-tab"
                  :class="{ 'is-active': chartType === t.id }"
                  @click="setChartType(t.id)"
                >
                  {{ t.label }}
                </button>
              </div>
              <div v-loading="dailyLoading" class="points-chart-area">
                <EChart
                  v-if="chartOption"
                  :key="chartMonthKey"
                  not-merge
                  class="points-chart-echart"
                  :option="chartOption"
                />
                <div v-else-if="!dailyLoading" class="points-chart-empty">本月暂无变动</div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="points-wallet-card points-log-panel">
        <div class="points-panel-body">
          <div class="points-panel-head">
            <div class="points-panel-head-left">
              <el-icon class="points-panel-head-icon"><List /></el-icon>
              <span class="points-panel-head-title">积分明细</span>
            </div>
            <el-popover v-model:visible="filterVisible" placement="bottom-end" :width="200" trigger="click">
              <template #reference>
                <button type="button" class="points-filter-btn">
                  <span>筛选</span>
                  <el-icon><Filter /></el-icon>
                </button>
              </template>
              <div class="points-filter-menu">
                <button
                  v-for="opt in SOURCE_OPTIONS"
                  :key="String(opt.value)"
                  type="button"
                  class="points-filter-option"
                  :class="{ 'is-active': filterSourceType === opt.value }"
                  @click="applyFilter(opt.value)"
                >
                  {{ opt.label }}
                </button>
              </div>
            </el-popover>
          </div>

          <ul v-loading="logLoading && logRows.length === 0" class="points-log-list">
            <li v-for="row in logRows" :key="row.id" class="points-log-item">
              <div class="points-log-icon-wrap" :class="`is-${logIconMeta(row).tone}`">
                <el-icon><component :is="logIconMeta(row).icon" /></el-icon>
              </div>
              <div class="points-log-main">
                <div class="points-log-title">{{ row.remark || '积分变动' }}</div>
                <div class="points-log-time">{{ formatLogTime(row.createTime) }}</div>
              </div>
              <div class="points-log-amount-col">
                <div class="points-log-delta" :class="logRowClass(row)">
                  {{ row.delta >= 0 ? '+' : '' }}{{ row.delta }}
                </div>
                <div class="points-log-balance">余额 {{ row.balanceAfter }}</div>
              </div>
            </li>
          </ul>

          <p v-if="!logLoading && logRows.length === 0" class="points-log-empty">暂无积分明细</p>

          <div v-if="hasMoreLogs" class="points-log-more">
            <button
              type="button"
              class="points-log-more-btn"
              :disabled="logLoading"
              aria-label="加载更多"
              @click="loadMoreLogs"
            >
              <el-icon><ArrowDown /></el-icon>
            </button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import EChart from '@/components/common/EChart.vue'
import { usePointsWallet } from '@scripts/views/PointsWallet'

const {
  ArrowDown,
  CHART_TYPES,
  DataLine,
  Filter,
  List,
  SOURCE_OPTIONS,
  applyFilter,
  canGoNextMonth,
  canGoPrevMonth,
  chartMonthKey,
  chartMonthTitle,
  chartOption,
  chartType,
  dailyLoading,
  filterSourceType,
  filterVisible,
  formatLogTime,
  hasMoreLogs,
  iconNextUrl,
  iconPrevUrl,
  loadMoreLogs,
  logIconMeta,
  logLoading,
  logRowClass,
  logRows,
  nextChartMonth,
  periodInTotal,
  periodOutTotal,
  prevChartMonth,
  setChartType,
  spendDisplay,
  summaryCardBgUrl,
  wallet,
} = usePointsWallet()
</script>

<style scoped src="@/assets/styles/points-wallet.css"></style>
<style scoped>
.points-filter-menu {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.points-filter-option {
  width: 100%;
  text-align: left;
  padding: 8px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  font-size: 13px;
  font-weight: 600;
  color: #4e5969;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.points-filter-option:hover {
  background: #f2f3f5;
}

.points-filter-option.is-active {
  background: rgba(107, 76, 255, 0.1);
  color: #6b4cff;
}
</style>
