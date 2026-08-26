<template>
  <section class="meng-coin-center shell-page-scroll animate-fade-in">
    <div class="meng-coin-center__inner">
      <div v-if="pageError" class="meng-coin-center__state is-error">
        <span>{{ pageError }}</span>
        <el-button type="primary" @click="loadOverview">重新加载</el-button>
      </div>

      <template v-else>
        <section v-loading="overviewLoading" class="meng-coin-hero">
          <div class="meng-coin-balance-card">
            <div class="meng-coin-balance-card__title">
              <PawCoinIcon class="meng-coin-balance-card__coin" />
              <span>萌币中心</span>
            </div>
            <div class="meng-coin-balance-card__value">
              {{ formatNumber(overview.balance) }} <small>萌币</small>
            </div>
            <img class="meng-coin-balance-card__mascot" :src="mengCoinCenterImageUrl" alt="" />
          </div>

          <div class="meng-coin-trend-card">
            <div class="meng-coin-section-heading">
              <h1>{{ trendTitle }}</h1>
              <div class="meng-coin-week-switch">
                <el-button text circle :icon="ArrowLeft" aria-label="上一周" @click="changeTrendWeek(-1)" />
                <span>{{ trendDateRange }}</span>
                <el-button text circle :icon="ArrowRight" aria-label="下一周" @click="changeTrendWeek(1)" />
              </div>
            </div>
            <div class="meng-coin-trend-chart-wrap">
              <EChart class="meng-coin-trend-chart" :option="trendOption" />
              <div v-if="trendIsEmpty" class="meng-coin-trend-empty">这周还没有萌币变动哦</div>
            </div>
            <div class="meng-coin-trend-stats">
              <article class="is-income"><span><i />本月获得</span><b>+{{ formatNumber(overview.monthIncome) }}</b></article>
              <article class="is-expense"><span><i />本月消耗</span><b>-{{ formatNumber(overview.monthExpense) }}</b></article>
            </div>
          </div>
        </section>

        <section v-loading="overviewLoading" class="meng-coin-milestones-card">
          <div class="meng-coin-milestones-main">
            <div class="meng-coin-section-heading">
              <h2>萌币里程碑</h2>
              <span class="meng-coin-milestone-total"><PawCoinIcon />累计获得 <b>{{ formatNumber(overview.cumulativeIncome) }}</b> 萌币</span>
            </div>
            <div class="meng-coin-milestone-track">
              <i class="meng-coin-milestone-track__progress" :style="{ '--milestone-progress': milestoneProgress / 100 }" aria-hidden="true" />
              <article
                v-for="item in overview.milestones"
                :key="item.code"
                class="meng-coin-milestone"
                :class="`is-${item.status.toLowerCase()}`"
              >
                <div class="meng-coin-milestone__point">{{ formatCompact(item.threshold) }}</div>
                <strong>{{ item.title }}</strong>
                <button
                  type="button"
                  :disabled="item.status !== 'CLAIMABLE' || claimingCode === item.code"
                  @click="claimMilestone(item)"
                >
                  {{ milestoneActionLabel(item) }}
                </button>
              </article>
            </div>
          </div>

          <aside class="meng-coin-review-card">
            <div class="meng-coin-review-card__head">
              <div class="meng-coin-review-card__title"><el-icon><MagicStick /></el-icon>本月萌币回顾</div>
            </div>
            <div class="meng-coin-review-card__sources">
              <div class="meng-coin-review-card__source is-income">
                <span><el-icon><CaretTop /></el-icon>主要获得</span>
                <b>{{ primaryIncomeSource?.sourceLabel || '暂无' }}</b>
                <em>+{{ formatNumber(primaryIncomeSource?.amount) }}</em>
              </div>
              <div class="meng-coin-review-card__source is-expense">
                <span><el-icon><CaretBottom /></el-icon>主要消耗</span>
                <b>{{ primaryExpenseSource?.sourceLabel || '暂无' }}</b>
                <em>-{{ formatNumber(primaryExpenseSource?.amount) }}</em>
              </div>
            </div>
          </aside>
        </section>

        <section class="meng-coin-ledger-card">
          <div class="meng-coin-ledger-card__head">
            <div class="meng-coin-ledger-title">
              <h2><el-icon><Tickets /></el-icon>萌币流水</h2>
              <button type="button" class="meng-coin-chart-toggle" @click="toggleChart">
                <el-icon :size="17" aria-hidden="true"><PieChart /></el-icon>
                图表化
              </button>
            </div>
            <div class="meng-coin-ledger-controls">
              <span>收支类型</span>
              <el-radio-group v-model="logQuery.direction" size="small" @change="reloadLog">
                <el-radio-button label="ALL">全部</el-radio-button>
                <el-radio-button label="INCOME">获得</el-radio-button>
                <el-radio-button label="EXPENSE">消耗</el-radio-button>
              </el-radio-group>
              <span>来源</span>
              <el-select v-model="logQuery.sourceType" size="small" placeholder="全部来源" clearable @change="reloadLog">
                <el-option v-for="item in sourceOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <span>时间</span>
              <el-select v-model="logQuery.timeRange" size="small" @change="reloadLog">
                <el-option v-for="item in timeRangeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-button plain size="small" :icon="Refresh" @click="resetLogQuery">重置</el-button>
            </div>
          </div>

          <el-table v-loading="logLoading" :data="logRows" class="meng-coin-ledger-table" empty-text="该条件下暂无萌币流水">
            <el-table-column label="来源" min-width="250">
              <template #default="{ row }">
                <span class="meng-coin-ledger-source" :class="row.delta >= 0 ? 'is-income' : 'is-expense'">
                  <el-icon><component :is="sourceIcon(row.sourceType)" /></el-icon>
                  {{ sourceLabel(row) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="时间" min-width="180">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="变动" min-width="130" align="right">
              <template #default="{ row }"><strong :class="row.delta >= 0 ? 'is-income' : 'is-expense'">{{ row.delta >= 0 ? '+' : '' }}{{ formatNumber(row.delta) }}</strong></template>
            </el-table-column>
            <el-table-column prop="balanceAfter" label="余额" min-width="120" align="right">
              <template #default="{ row }">{{ formatNumber(row.balanceAfter) }}</template>
            </el-table-column>
          </el-table>
          <div class="meng-coin-ledger-pagination">
            <AppPagination
              v-model:current-page="logQuery.pageNum"
              :page-size="logQuery.pageSize"
              :total="logTotal"
              @current-change="loadLog"
            />
          </div>
        </section>

        <el-dialog
          v-model="chartVisible"
          title="萌币流水图表"
          width="720px"
          align-center
          destroy-on-close
          class="meng-coin-chart-dialog"
        >
          <div v-loading="chartLoading" class="meng-coin-ledger-chart-wrap">
            <EChart class="meng-coin-ledger-chart" :option="ledgerChartOption" />
            <div v-if="!chartLoading && chartIsEmpty" class="meng-coin-chart-empty">当前筛选条件下暂无萌币变动</div>
          </div>
        </el-dialog>
      </template>
    </div>
  </section>
</template>

<script setup>
import EChart from '@/components/common/EChart.vue'
import PawCoinIcon from '@/components/common/PawCoinIcon.vue'
import AppPagination from '@/components/common/AppPagination.vue'
import { PieChart } from '@element-plus/icons-vue'
import { usePointsWallet } from '@scripts/views/PointsWallet'

const {
  ArrowLeft,
  ArrowRight,
  CaretBottom,
  CaretTop,
  MagicStick,
  Refresh,
  Tickets,
  claimMilestone,
  claimingCode,
  changeTrendWeek,
  chartLoading,
  chartIsEmpty,
  chartVisible,
  formatCompact,
  formatNumber,
  formatTime,
  loadLog,
  loadOverview,
  ledgerChartOption,
  logLoading,
  logQuery,
  logRows,
  logTotal,
  mengCoinCenterImageUrl,
  milestoneActionLabel,
  milestoneProgress,
  overview,
  overviewLoading,
  pageError,
  primaryExpenseSource,
  primaryIncomeSource,
  reloadLog,
  resetLogQuery,
  sourceIcon,
  sourceLabel,
  sourceOptions,
  timeRangeOptions,
  trendDateRange,
  trendHasData,
  trendIsEmpty,
  trendOption,
  trendTitle,
  toggleChart,
} = usePointsWallet()
</script>

<style scoped src="@/assets/styles/points-wallet.css"></style>
