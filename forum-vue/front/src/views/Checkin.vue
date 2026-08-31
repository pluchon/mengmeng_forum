<template>
  <div v-loading="loading" class="checkin-page shell-page-scroll">
    <div class="checkin-board">
      <div class="checkin-board__combo">
      <aside class="checkin-hero" aria-hidden="true">
        <img :src="heroImageUrl" alt="" class="checkin-hero__img" />
      </aside>

      <section class="checkin-main">
        <header class="checkin-main__toolbar">
          <div class="checkin-main__title-wrap">
            <h1 class="checkin-main__title">本月签到日历</h1>
            <div class="checkin-main__month-nav">
              <button type="button" class="checkin-nav-btn" aria-label="上一月" @click="prevMonth">
                <img :src="iconPrevUrl" alt="" />
              </button>
              <span class="checkin-main__month">{{ calendarTitle }}</span>
              <button type="button" class="checkin-nav-btn" aria-label="下一月" @click="nextMonth">
                <img :src="iconNextUrl" alt="" />
              </button>
            </div>
          </div>
          <div class="checkin-main__actions">
            <button type="button" class="checkin-log-btn" @click="openLogDrawer">
              <img :src="iconLogUrl" alt="" class="checkin-log-btn__icon" />
              <span>签到记录</span>
            </button>
            <button
              v-if="status && !status.todaySigned"
              type="button"
              class="checkin-do-btn"
              :disabled="submitting"
              @click="handleCheckin"
            >
              {{ submitting ? '签到中…' : '立即签到' }}
            </button>
            <button
              v-else
              type="button"
              class="checkin-do-btn checkin-do-btn--done"
              disabled
            >
              今日已签
            </button>
          </div>
        </header>

        <div class="checkin-cal">
          <div class="checkin-cal__weekdays">
            <span v-for="w in WEEKDAY_LABELS" :key="w">{{ w }}</span>
          </div>
          <div class="checkin-cal__grid">
            <div
              v-for="cell in calendarCells"
              :key="cell.key"
              class="checkin-cal__cell"
              :class="{
                'is-empty': cell.empty,
                'is-signed': cell.signed,
                'is-today': cell.today,
                'is-future': !cell.empty && !cell.signed && !cell.today && isFutureDay(cell.date),
              }"
            >
              <template v-if="!cell.empty">
                <span class="checkin-cal__day">{{ cell.dayNumber }}</span>
                <div class="checkin-cal__rewards">
                  <span
                    v-if="cell.signed"
                    class="checkin-cal__pill checkin-cal__pill--ok"
                  >
                    <span class="checkin-cal__check">✓</span>
                    +{{ cell.points }}
                  </span>
                  <span
                    v-else
                    class="checkin-cal__pill"
                    :class="cell.today ? 'checkin-cal__pill--today' : 'checkin-cal__pill--muted'"
                  >
                    <PawCoinIcon class="checkin-cal__coin" />
                    +{{ cell.points }}
                  </span>
                  <span v-if="cell.surpriseDay" class="checkin-cal__surprise">惊喜奖励</span>
                </div>
              </template>
            </div>
          </div>
        </div>

        <div class="checkin-week">
          <div class="checkin-week__head">
            <strong>每周签到统计</strong>
            <span>本月已签到 {{ monthSignedDays }} 天</span>
          </div>
          <div v-if="monthLoadFailed" class="checkin-week__failed">
            统计数据没能加载出来，稍后再试
          </div>
          <EChart v-else class="checkin-week__chart" :option="weekChartOption" />
        </div>
      </section>
      </div>

      <aside class="checkin-side">
        <div class="checkin-streak">
          <h2 class="checkin-side__title">连续签到奖励</h2>
          <div
            v-for="item in streakRewards"
            :key="item.streakDays"
            class="checkin-streak__item"
            :class="item.achieved ? 'is-done' : 'is-pending'"
          >
            <div class="checkin-streak__body">
              <strong>{{ item.title }}</strong>
              <span>{{ item.subtitle }}</span>
            </div>
            <span v-if="item.achieved" class="checkin-streak__mark" aria-label="已达成">✓</span>
            <span v-else class="checkin-streak__left">还差 {{ item.daysLeft }} 天</span>
          </div>
        </div>

        <div class="checkin-makeup">
          <div class="checkin-makeup__row">
            <strong>补签</strong>
            <span>持有 {{ status?.makeupCardCount ?? 0 }} 张补签卡</span>
          </div>
          <div class="checkin-makeup__row checkin-makeup__row--bottom">
            <span>自动补最近一次漏签</span>
            <button
              type="button"
              class="checkin-makeup__link"
              :disabled="makeupSubmitting"
              @click="openMakeupConfirm"
            >
              {{ makeupSubmitting ? '补签中…' : '去补签 ›' }}
            </button>
          </div>
        </div>

        <div class="checkin-tip">
          <strong>补签小贴士</strong>
          <ul class="checkin-tip__list">
            <li>消耗一张补签卡，补签的这一天计入连续签到</li>
            <li>补签当天不发放惊喜奖励</li>
            <li>不可自选日期，自动选择离当前最近未签日期</li>
          </ul>
        </div>

        <div class="checkin-stats">
          <div class="checkin-stats__row">
            <PawCoinIcon class="checkin-stats__icon" />
            <span class="checkin-stats__label">累计萌币</span>
            <strong class="checkin-stats__value">{{ formatPoints(status?.totalPoints) }}</strong>
          </div>
          <div class="checkin-stats__row">
            <span class="checkin-stats__dot checkin-stats__dot--flame" aria-hidden="true" />
            <span class="checkin-stats__label">连续签到</span>
            <strong class="checkin-stats__value">{{ status?.streakDays ?? 0 }} 天</strong>
          </div>
          <div class="checkin-stats__row">
            <span class="checkin-stats__dot checkin-stats__dot--cal" aria-hidden="true" />
            <span class="checkin-stats__label">累计签到</span>
            <strong class="checkin-stats__value">{{ status?.totalDays ?? 0 }} 天</strong>
          </div>
        </div>
      </aside>
    </div>

    <el-dialog
      v-model="logDrawer"
      title="签到记录"
      width="min(780px, 94vw)"
      class="checkin-log-dialog"
      align-center
      append-to-body
      destroy-on-close
    >
      <div class="checkin-log-shell">
        <div class="checkin-log-table-wrap">
          <el-table
            v-loading="logLoading"
            class="checkin-log-table"
            :data="logRows"
            stripe
            size="small"
            height="420"
            :empty-text="logLoadFailed ? '记录没能加载出来，稍后再试' : '暂无记录'"
          >
            <el-table-column label="签到时间" min-width="168" align="center" header-align="center">
              <template #default="{ row }">
                <div>{{ formatCheckinLogInstantShanghai(row) }}</div>
              </template>
            </el-table-column>
            <el-table-column label="归属日" width="112" align="center" header-align="center">
              <template #default="{ row }">{{ row.attributionDate ? formatCheckinLogDateOnly(row.attributionDate) : '' }}</template>
            </el-table-column>
            <el-table-column prop="checkinType" label="签到类型" width="96" align="center" header-align="center" />
            <el-table-column label="萌币" width="72" align="center" header-align="center">
              <template #default="{ row }">+{{ row.points || 0 }}</template>
            </el-table-column>
            <el-table-column prop="streakDays" label="当时连签" width="88" align="center" header-align="center" />
            <el-table-column
              prop="surpriseLabel"
              label="惊喜奖励"
              width="100"
              align="center"
              header-align="center"
              show-overflow-tooltip
            />
          </el-table>
        </div>
        <div class="checkin-log-pager">
          <AppPagination
            v-model:current-page="logPage"
            :total="logTotal"
            :page-size="logPageSize"
            :hide-on-single-page="false"
            @current-change="onLogPageChange"
          />
        </div>
      </div>
    </el-dialog>

    <el-dialog
      v-model="makeupConfirmVisible"
      width="420px"
      class="checkin-makeup-confirm"
      align-center
      destroy-on-close
      :append-to-body="false"
    >
      <template #header>
        <span class="checkin-makeup-confirm__title-spacer" aria-hidden="true" />
      </template>
      <div class="checkin-makeup-confirm__body">
        <span class="checkin-makeup-confirm__icon" aria-hidden="true">!</span>
        <p class="checkin-makeup-confirm__text">是否确认补签最近的未签日？</p>
      </div>
      <template #footer>
        <div class="checkin-makeup-confirm__footer">
          <span class="checkin-makeup-confirm__note">若未签日为惊喜日，不发放惊喜奖励</span>
          <button
            type="button"
            class="checkin-makeup-confirm__ok"
            :disabled="makeupSubmitting"
            @click="confirmMakeup"
          >
            {{ makeupSubmitting ? '补签中…' : '确认' }}
          </button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import EChart from '@/components/common/EChart.vue'
import PawCoinIcon from '@/components/common/PawCoinIcon.vue'
import AppPagination from '@/components/common/AppPagination.vue'
import { formatCheckinLogDateOnly, formatCheckinLogInstantShanghai } from '@/utils/datetime'
import { useCheckin } from '@scripts/views/Checkin'

const {
  WEEKDAY_LABELS,
  calendarCells,
  calendarTitle,
  confirmMakeup,
  formatPoints,
  handleCheckin,
  heroImageUrl,
  iconLogUrl,
  iconNextUrl,
  iconPrevUrl,
  isFutureDay,
  loading,
  logDrawer,
  logLoading,
  logPage,
  logPageSize,
  logRows,
  logTotal,
  makeupConfirmVisible,
  makeupSubmitting,
  monthSignedDays,
  nextMonth,
  onLogPageChange,
  openLogDrawer,
  openMakeupConfirm,
  prevMonth,
  status,
  streakRewards,
  submitting,
  weekChartOption,
  monthLoadFailed,
  logLoadFailed,
} = useCheckin()
</script>

<style scoped src="@/assets/styles/checkin.css"></style>
<style src="@/assets/styles/checkin-overlays.css"></style>
