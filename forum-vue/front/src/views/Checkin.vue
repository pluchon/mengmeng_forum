<template>
  <div v-loading="loading" class="checkin-page shell-page-scroll">
    <div class="checkin-inner">
      <div
        v-if="status && nextRewardText && rewardTipVisible"
        class="checkin-reward-tip"
        role="status"
      >
        <p class="checkin-reward-tip-text">{{ nextRewardText }}</p>
        <button type="button" class="checkin-reward-tip-close" aria-label="关闭提示" @click="dismissRewardTip">
          ×
        </button>
      </div>

      <el-card class="checkin-hero-card" shadow="never">
        <div class="checkin-hero-grid">
          <div class="checkin-hero-visual" aria-hidden="true">
            <img :src="heroImageUrl" alt="" class="checkin-hero-img" />
          </div>
          <div class="checkin-hero-calendar">
            <div class="checkin-cal-toolbar">
              <div class="checkin-cal-toolbar-left">
                <el-button
                  class="checkin-icon-btn"
                  circle
                  plain
                  aria-label="上一月"
                  @click="prevMonth"
                >
                  <img :src="iconPrevUrl" alt="" class="checkin-toolbar-icon" />
                </el-button>
                <span class="checkin-cal-title">{{ calendarTitle }}</span>
                <el-button
                  class="checkin-icon-btn"
                  circle
                  plain
                  aria-label="下一月"
                  @click="nextMonth"
                >
                  <img :src="iconNextUrl" alt="" class="checkin-toolbar-icon" />
                </el-button>
              </div>
              <div class="checkin-cal-toolbar-right">
                <el-button
                  class="checkin-icon-btn"
                  circle
                  plain
                  aria-label="回到今天"
                  @click="goTodayCalendar"
                >
                  <img :src="iconTodayUrl" alt="" class="checkin-toolbar-icon" />
                </el-button>
                <el-button
                  class="checkin-icon-btn"
                  circle
                  plain
                  aria-label="查看签到记录"
                  @click="openLogDrawer"
                >
                  <img :src="iconLogUrl" alt="" class="checkin-toolbar-icon" />
                </el-button>
                <el-button
                  v-if="status && !status.todaySigned"
                  type="primary"
                  round
                  class="checkin-toolbar-checkin-btn"
                  :loading="submitting"
                  @click="handleCheckin"
                >
                  立即签到
                </el-button>
                <el-button
                  v-else-if="status?.todaySigned"
                  round
                  class="checkin-toolbar-checkin-btn checkin-toolbar-checkin-btn--done"
                  disabled
                >
                  今日已签
                </el-button>
              </div>
            </div>

            <div class="checkin-calendar-wrap">
              <el-calendar v-model="calendarDate" class="checkin-cal-no-header">
                <template #date-cell="{ data }">
                  <div
                    :class="[
                      'checkin-cell',
                      { 'is-other': data.type === 'prev-month' || data.type === 'next-month' },
                      { 'is-signed': isSignedDay(data.day) },
                      { 'is-today': isTodayCell(data.day) },
                      { 'is-today-signed': isTodayCell(data.day) && status?.todaySigned },
                    ]"
                  >
                    <span class="checkin-cell-day">{{ data.day.split('-').slice(2).join('-') }}</span>
                    <span
                      v-if="data.type === 'current-month' && cellPointsForDay(data.day) != null"
                      class="checkin-cell-points"
                    >
                      <span class="checkin-cell-points-num">+{{ cellPointsForDay(data.day) }}</span>
                      <PawCoinIcon class="checkin-cell-paw" />
                    </span>
                    <img
                      v-if="showSignedOverlay(data)"
                      :src="signedTodayIconUrl"
                      alt=""
                      class="checkin-cell-signed-overlay"
                    />
                  </div>
                </template>
              </el-calendar>
            </div>
          </div>
        </div>
      </el-card>

      <el-card v-if="status" class="checkin-stats-panel" shadow="never">
        <div class="checkin-stats-grid">
          <div class="checkin-stat-mini">
            <img :src="statCardBgUrl" alt="" class="checkin-stat-mini-bg" />
            <div class="checkin-stat-mini-mask" />
            <div class="checkin-stat-mini-content">
              <button
                type="button"
                class="checkin-stat-corner-btn"
                aria-label="查看签到趋势"
                @click="openTrendOverlay"
              >
                <img :src="iconTrendUrl" alt="" class="checkin-stat-corner-icon" />
              </button>
              <div class="checkin-stat-mini-row">
                <span class="checkin-stat-mini-label">萌币（累计）</span>
                <span class="checkin-stat-mini-value">
                  <PawCoinIcon class="checkin-stat-mini-paw" />
                  {{ status.totalPoints ?? 0 }}
                </span>
              </div>
            </div>
          </div>
          <div class="checkin-stat-mini">
            <img :src="statCardBgUrl" alt="" class="checkin-stat-mini-bg" />
            <div class="checkin-stat-mini-mask" />
            <div class="checkin-stat-mini-content">
              <div class="checkin-stat-mini-row">
                <span class="checkin-stat-mini-label">连续签到</span>
                <span class="checkin-stat-mini-value">{{ status.streakDays ?? 0 }} 天</span>
              </div>
            </div>
          </div>
          <div class="checkin-stat-mini">
            <img :src="statCardBgUrl" alt="" class="checkin-stat-mini-bg" />
            <div class="checkin-stat-mini-mask" />
            <div class="checkin-stat-mini-content">
              <div class="checkin-stat-mini-row">
                <span class="checkin-stat-mini-label">累计签到</span>
                <span class="checkin-stat-mini-value">{{ status.totalDays ?? 0 }} 天</span>
              </div>
            </div>
          </div>
        </div>
      </el-card>
    </div>

    <teleport to="body">
      <div
        v-if="trendOverlayVisible"
        class="checkin-trend-overlay"
        @click.self="closeTrendOverlay"
      >
        <div
          class="checkin-trend-card"
          role="dialog"
          aria-modal="true"
          aria-labelledby="checkin-trend-title"
          @click.stop
        >
          <div class="checkin-trend-head">
            <div>
              <h2 id="checkin-trend-title" class="checkin-trend-title">萌币累计趋势</h2>
              <p class="checkin-trend-sub">仅统计签到流水（基础分 + 连签奖励），不含站内消费</p>
            </div>
            <el-button text circle type="primary" :icon="Close" aria-label="关闭" @click="closeTrendOverlay" />
          </div>
          <div v-loading="trendLoading" class="checkin-trend-chart-wrap">
            <EChart v-if="trendChartOption" class="checkin-trend-echart" :option="trendChartOption" />
          </div>
        </div>
      </div>
    </teleport>

    <el-drawer v-model="logDrawer" title="签到记录" size="440px" class="checkin-log-drawer" destroy-on-close>
      <el-table v-loading="logLoading" :data="logRows" stripe size="small" empty-text="暂无记录">
        <el-table-column label="签到日期" min-width="178">
          <template #default="{ row }">
            <div>{{ formatCheckinLogInstantShanghai(row) }}</div>
            <div class="checkin-log-sub">归属日 {{ formatCheckinLogDateOnly(row.checkinDate) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="萌币" min-width="88">
          <template #default="{ row }">
            +{{ (row.points || 0) + (row.bonusPoints || 0) }}
            <span v-if="row.bonusPoints > 0" class="text-muted">（含奖励 {{ row.bonusPoints }}）</span>
          </template>
        </el-table-column>
        <el-table-column prop="streakDays" label="当时连签" width="96" />
      </el-table>
      <div v-if="logTotal > logPageSize" style="margin-top: 16px; display: flex; justify-content: center">
        <el-pagination
          v-model:current-page="logPage"
          layout="prev, pager, next"
          :total="logTotal"
          :page-size="logPageSize"
          small
          @current-change="onLogPageChange"
        />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { Close } from '@element-plus/icons-vue'
import EChart from '@/components/common/EChart.vue'
import PawCoinIcon from '@/components/common/PawCoinIcon.vue'
import { formatCheckinLogDateOnly, formatCheckinLogInstantShanghai } from '@/utils/datetime'
import { useCheckin } from '@scripts/views/Checkin'

const {
  calendarDate,
  calendarTitle,
  cellPointsForDay,
  closeTrendOverlay,
  dismissRewardTip,
  goTodayCalendar,
  handleCheckin,
  heroImageUrl,
  iconLogUrl,
  iconNextUrl,
  iconPrevUrl,
  iconTodayUrl,
  iconTrendUrl,
  isSignedDay,
  isTodayCell,
  loading,
  logDrawer,
  logLoading,
  logPage,
  logPageSize,
  logRows,
  logTotal,
  nextRewardText,
  onLogPageChange,
  openLogDrawer,
  openTrendOverlay,
  prevMonth,
  nextMonth,
  rewardTipVisible,
  showSignedOverlay,
  signedTodayIconUrl,
  statCardBgUrl,
  status,
  submitting,
  trendChartOption,
  trendLoading,
  trendOverlayVisible,
} = useCheckin()
</script>

<style scoped src="@/assets/styles/checkin.css"></style>
<style scoped>
.text-muted {
  color: #86909c;
  font-size: 12px;
}

.checkin-log-sub {
  margin-top: 2px;
  font-size: 11px;
  color: #86909c;
  line-height: 1.3;
}
</style>
