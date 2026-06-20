<template>
  <div v-loading="loading" class="lottery-page shell-page-scroll animate-fade-in">
    <div class="lottery-inner">
      <div class="lottery-demo-banner" role="alert">
        <el-icon class="lottery-demo-banner__icon" :size="20"><WarningFilled /></el-icon>
        <div class="lottery-demo-banner__body">
          <strong class="lottery-demo-banner__title">演示说明</strong>
          <p class="lottery-demo-banner__text">{{ LOTTERY_DEMO_NOTICE }}</p>
        </div>
      </div>

      <header class="lottery-topbar">
        <div class="lottery-title-block">
          <el-icon :size="26"><Trophy /></el-icon>
          <h1 class="lottery-title">积分幸运抽奖</h1>
        </div>
      </header>

      <div class="lottery-scroll-body">
      <div class="lottery-main-grid">
        <!-- 左：积分与保底 -->
        <aside class="lottery-glass lottery-info-col">
          <button
            type="button"
            class="lottery-record-icon-btn"
            aria-label="我的抽奖记录"
            title="我的抽奖记录"
            @click="historyDialogVisible = true"
          >
            <img :src="recordIconUrl" alt="" />
          </button>

          <div class="lottery-info-main">
            <div class="lottery-info-label">当前积分余额</div>
            <div class="lottery-info-value">{{ info.balance ?? '—' }}</div>
            <div class="lottery-info-sub">
              距攒够一次十连还差 <strong>{{ ptsToTen }}</strong> 积分
            </div>
            <div class="lottery-progress-bg">
              <div class="lottery-progress-fill" :style="{ width: tenProgressPct + '%' }" />
            </div>
            <div class="lottery-progress-hint">{{ info.balance ?? 0 }} / {{ tenCost }}</div>

            <div class="lottery-pity-row">
              <span>
                神秘大奖硬保底：
                <template v-if="hardPityRemaining <= 0">下次抽取必得神秘大奖档（若无库存则回落普通池）</template>
                <template v-else>还差 {{ hardPityRemaining }} 抽触发必得</template>
              </span>
              <el-tooltip placement="top" effect="dark" :show-after="200">
                <template #content>
                  <div class="lottery-tooltip-inner">
                    累计未中神秘大奖达到 {{ info.hardPityThreshold ?? 50 }} 抽后强制命中；
                    十连若前 9 抽均无稀有档（大奖/周边/VIP），第 10 抽仅在稀有子池抽取。
                  </div>
                </template>
                <span class="lottery-pity-info-wrap">
                  <el-icon :size="14"><InfoFilled /></el-icon>
                </span>
              </el-tooltip>
            </div>

            <div v-if="activityList.length" class="lottery-activity-picker">
              <button
                v-for="act in sidebarActivities"
                :key="act.id"
                type="button"
                class="lottery-activity-pick-card"
                :class="{ 'is-active': selectedActivityId === act.id }"
                :disabled="!canSwitchActivity"
                @click="onSelectActivity(act.id)"
              >
                <span class="lottery-activity-pick-card__title">{{ act.title }}</span>
              </button>
              <button
                v-if="hasMoreActivities"
                type="button"
                class="lottery-activity-more-btn"
                :disabled="!canSwitchActivity"
                aria-label="查看更多活动"
                title="查看更多活动"
                @click="openActivitySwitch"
              >
                <el-icon><ArrowDown /></el-icon>
              </button>
            </div>
          </div>
        </aside>

        <!-- 中：抽奖操作 / 洗牌 + 说明（与左右列同高） -->
        <section class="lottery-glass lottery-draw-stack lottery-draw-col">
          <div class="lottery-draw-body" :class="{ 'is-busy': phase !== 'idle' }">
            <div
              v-if="phase === 'single_shuffle' || phase === 'ten_shuffle'"
              class="lottery-draw-spin-panel"
              aria-live="polite"
            >
              <div class="lottery-gacha-spin" :class="{ 'lottery-gacha-spin--ten': phase === 'ten_shuffle' }">
                <div class="lottery-gacha-orbit" />
                <div class="lottery-gacha-orbit lottery-gacha-orbit--reverse" />
                <div class="lottery-gacha-core">
                  <el-icon :size="28"><Trophy /></el-icon>
                </div>
                <span
                  v-for="n in 6"
                  :key="n"
                  class="lottery-gacha-spark"
                  :style="{ '--spark-i': n }"
                />
              </div>
              <p class="lottery-gacha-label">{{ phase === 'ten_shuffle' ? '十连抽取中…' : '单抽进行中…' }}</p>
            </div>

            <div v-else-if="phase === 'single_result'" class="lottery-draw-result">
              <p class="lottery-draw-result-heading">恭喜获得</p>
              <div
                class="lottery-draw-result-prize"
                :class="{ 'is-jackpot': singleOutcome?.jackpot }"
              >
                <span class="lottery-draw-result-name">{{ formatOutcome(singleOutcome) }}</span>
                <span v-if="singleOutcome?.grantPoints > 0" class="lottery-draw-result-points">
                  +{{ singleOutcome.grantPoints }} 积分
                </span>
              </div>
              <button type="button" class="lottery-draw-confirm-btn" @click="resetRound">确定</button>
            </div>

            <div v-else-if="phase === 'ten_result'" class="lottery-draw-result lottery-draw-result--ten">
              <p class="lottery-draw-result-heading">十连结果</p>
              <div class="lottery-ten-result-grid">
                <div
                  v-for="(item, i) in tenResults"
                  :key="i"
                  class="lottery-ten-result-chip"
                  :class="{ 'is-jackpot': item.jackpot }"
                >
                  <span class="lottery-ten-result-chip__name">{{ formatOutcome(item) }}</span>
                  <span v-if="item.grantPoints > 0" class="lottery-ten-result-chip__pts">+{{ item.grantPoints }}</span>
                </div>
              </div>
              <button type="button" class="lottery-draw-confirm-btn" @click="resetRound">确定</button>
            </div>

            <div v-if="phase === 'idle'" class="lottery-draw-idle-wrap">
              <h3 class="lottery-draw-strategy-title">请选择你的策略</h3>
              <div class="lottery-compact-actions">
                <button
                  type="button"
                  class="lottery-play-card lottery-play-card--single"
                  :disabled="busy"
                  @click="onSingle"
                >
                  <span class="play-icon" aria-hidden="true">
                    <el-icon><Coin /></el-icon>
                  </span>
                  <span class="play-label">单抽</span>
                </button>
                <button
                  type="button"
                  class="lottery-play-card lottery-play-card--ten"
                  :disabled="busy"
                  @click="onTen"
                >
                  <span class="play-icon" aria-hidden="true">
                    <el-icon><Grid /></el-icon>
                  </span>
                  <span class="play-label">十连</span>
                </button>
              </div>
            </div>
          </div>
        </section>

        <!-- 右：消耗与规则摘要 -->
        <aside class="lottery-glass lottery-cost-col">
          <div class="lottery-cost-head">消耗说明</div>
          <div class="lottery-cost-row">
            <span class="lottery-cost-name">单次消耗</span>
            <span class="lottery-cost-val">{{ costPer }} 积分</span>
          </div>
          <div class="lottery-cost-row">
            <span class="lottery-cost-name">十连消耗</span>
            <span class="lottery-cost-val">{{ tenCost }} 积分</span>
          </div>
          <div class="lottery-cost-row">
            <span class="lottery-cost-name">十连保底（稀有）</span>
            <span class="lottery-cost-val lottery-cost-val--ok">至少 1 件稀有档</span>
          </div>
          <div class="lottery-cost-row">
            <span class="lottery-cost-name">神秘大奖硬保底</span>
            <span class="lottery-cost-val lottery-cost-val--ok">{{ info.hardPityThreshold ?? 50 }} 抽未出必得</span>
          </div>
          <ul class="lottery-cost-rules">
            <li v-for="(line, i) in costRuleHints" :key="i">{{ line }}</li>
          </ul>
        </aside>
      </div>

      <div class="lottery-bottom-grid">
        <div class="lottery-glass lottery-pool-section">
          <div class="lottery-section-title">
            <el-icon :size="18"><Present /></el-icon>
            奖池一览
          </div>
          <div class="lottery-pool-grid">
            <div
              v-for="(p, i) in info.prizes || []"
              :key="i"
              class="lottery-pool-chip"
              :class="{ jackpot: p.jackpot }"
            >
              <span class="chip-name">{{ p.name }}</span>
              <span class="chip-weight">{{ formatPrizePercent(p) }}</span>
              <span class="chip-meta" :class="{ scarce: poolStockScarce(p.stockRemaining) }">{{
                poolStockHint(p.stockRemaining)
              }}</span>
            </div>
          </div>
        </div>

        <div class="lottery-glass lottery-chart-section">
          <div class="lottery-chart-block">
            <div class="lottery-chart-head">
              <el-icon :size="17"><TrendCharts /></el-icon>
              奖池概率分布
            </div>
            <div class="lottery-chart-inner">
              <EChart class="lottery-chart" :option="pieOption" />
              <p class="lottery-chart-hint">售罄档位会自动剔除，其余奖品概率按比例重算。</p>
            </div>
          </div>
          <div class="lottery-chart-block">
            <div class="lottery-chart-head">
              <el-icon :size="17"><TrendCharts /></el-icon>
              近期开奖热度（当前活动）
            </div>
            <div class="lottery-chart-inner">
              <EChart class="lottery-chart lottery-chart--bar" :option="barOption" />
              <p class="lottery-chart-hint">统计全站玩家在当前活动下的中奖次数。</p>
            </div>
          </div>
        </div>
      </div>

      <div class="lottery-surprise-row lottery-glass">
        <button type="button" class="lottery-surprise-cta" @click="focusSurprisePreview">
          点我看看
        </button>
        <div
          ref="surprisePreviewRef"
          class="lottery-surprise-preview"
          :class="{ 'lottery-surprise-preview--claimed': info.lotterySurpriseClaimed }"
          role="button"
          tabindex="0"
          @click="openSurprisePhase1"
          @keydown.enter.prevent="openSurprisePhase1"
        >
          <img :src="surpriseTeaserImg" alt="" @error="onSurpriseImgError" />
          <div class="lottery-surprise-mask" />
          <span class="lottery-surprise-hint">
            {{ info.lotterySurpriseClaimed ? '已领取' : '限领一次 · 点图领取积分' }}
          </span>
        </div>
      </div>
      </div>
    </div>

    <el-dialog
      v-model="historyDialogVisible"
      title="我的抽奖记录"
      width="min(440px, 92vw)"
      class="lottery-dialog lottery-dialog--history"
      align-center
      append-to-body
      destroy-on-close
    >
      <div v-if="(info.recentDraws || []).length">
        <el-table
          class="lottery-history-table"
          :data="historyTableRows"
          size="small"
          stripe
          border
        >
          <el-table-column type="index" label="#" width="48" />
          <el-table-column prop="kind" label="抽奖类型" width="88" />
          <el-table-column prop="prizeName" label="奖品" min-width="140" show-overflow-tooltip />
        </el-table>
      </div>
      <div v-else class="lottery-history-empty">
        <img class="lottery-history-empty-icon" :src="recordIconUrl" alt="" />
        <p>暂无抽奖记录</p>
        <p class="lottery-history-empty-sub">参与单抽或十连后，将在此处展示近期结果摘要。</p>
      </div>
    </el-dialog>

    <el-dialog
      v-model="activitySwitchVisible"
      title="全部活动"
      width="min(400px, 92vw)"
      class="lottery-dialog lottery-dialog--activity"
      align-center
      append-to-body
      destroy-on-close
    >
      <ul class="lottery-activity-list">
        <li
          v-for="act in activityList"
          :key="act.id"
          class="lottery-activity-item"
          :class="{ 'lottery-activity-item--active': selectedActivityId === act.id }"
          role="button"
          tabindex="0"
          @click="onSelectActivityFromDialog(act.id)"
          @keydown.enter.prevent="onSelectActivityFromDialog(act.id)"
        >
          <div class="lottery-activity-item__cover">
            <img
              :src="act.coverImageUrl || activityCoverFallback"
              :alt="act.title"
              loading="lazy"
              @error="onActivityCoverError"
            />
          </div>
          <div class="lottery-activity-item__meta">
            <span class="lottery-activity-item__title">{{ act.title }}</span>
            <span class="lottery-activity-item__cost">{{ act.costPointsPerDraw ?? '—' }} 积分/次</span>
          </div>
        </li>
      </ul>
    </el-dialog>

    <el-dialog
      v-model="surprisePhaseVisible"
      :title="surpriseDialogTitle"
      :show-close="surprisePhase < 3"
      width="min(380px, 90vw)"
      class="lottery-dialog lottery-dialog--surprise"
      align-center
      append-to-body
      @closed="resetSurpriseFlow"
    >
      <div v-if="surprisePhase === 1" class="lottery-surprise-dialog-text">确认要点吗？</div>
      <div v-else-if="surprisePhase === 2" class="lottery-surprise-dialog-text">真的真的要点吗？</div>
      <div v-else-if="surprisePhase === 3" class="lottery-surprise-reward-wrap">
        <div class="lottery-surprise-reward-photo">
          <img :src="surpriseRewardImg" alt="" @error="onSurpriseImgError" />
          <div class="lottery-surprise-reward-banner">恭喜你获得{{ PAGE_SURPRISE_POINTS }} 积分！</div>
        </div>
        <p class="lottery-surprise-reward-note">积分已入账，可在积分钱包明细中查看。</p>
      </div>

      <template #footer>
        <div v-if="surprisePhase === 1" class="lottery-dialog-footer">
          <el-button round @click="surprisePhaseVisible = false">算了</el-button>
          <el-button type="warning" round @click="surprisePhase = 2">确认</el-button>
        </div>
        <div v-else-if="surprisePhase === 2" class="lottery-dialog-footer">
          <el-button round @click="surprisePhase = 1">我再想想</el-button>
          <el-button type="danger" round :loading="surpriseClaimBusy" @click="confirmSurpriseClaim">点就点</el-button>
        </div>
        <div v-else-if="surprisePhase === 3" class="lottery-dialog-footer lottery-dialog-footer--solo">
          <el-button type="primary" round @click="surprisePhaseVisible = false">收下好心情</el-button>
        </div>
      </template>
    </el-dialog>

    <teleport to="body">
      <transition name="fade">
        <div
          v-if="jackpotOverlay"
          class="jackpot-overlay lottery-jackpot-light"
          @click.self="jackpotOverlay = false"
        >
          <div class="jackpot-card lottery-jackpot-card">
            <div class="burst" />
            <h2>头奖降临</h2>
            <p>{{ jackpotOverlayText }}</p>
            <el-button type="primary" round size="large" @click="jackpotOverlay = false">收下喜悦</el-button>
          </div>
        </div>
      </transition>
    </teleport>
  </div>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import {
  Trophy,
  Present,
  TrendCharts,
  InfoFilled,
  WarningFilled,
  ArrowDown,
  Coin,
  Grid,
} from '@element-plus/icons-vue'
import { LOTTERY_DEMO_NOTICE } from '@/constants/site'
import { ref, reactive, onMounted, computed, nextTick } from 'vue'
import {
  getLotteryActivities,
  getLotteryInfo,
  lotteryDraw,
  claimLotterySurpriseBonus,
} from '@/api/lottery'
import { usePointsWalletStore } from '@/stores/pointsWallet'
import EChart from '@/components/common/EChart.vue'
import recordIconUrl from '@/assets/svg/抽奖记录.svg?url'
import { clientOssUrl } from '@/utils/clientOss'

const VISIBLE_ACTIVITY_LIMIT = 3

const surpriseTeaserImg = clientOssUrl('抽奖惊喜.webp')
const surpriseRewardImg = clientOssUrl('抽奖.webp')

const CHART_PALETTE = ['#9ca3af', '#34d399', '#a78bfa', '#f59e0b', '#f97316', '#ec4899', '#60a5fa', '#d4537e']

/** 与后端 Constant.POINTS_LOTTERY_PAGE_SURPRISE_AMOUNT 一致（展示文案） */
const PAGE_SURPRISE_POINTS = 200

const loading = ref(true)
const busy = ref(false)
const surpriseClaimBusy = ref(false)

const activityCoverFallback = surpriseTeaserImg

const costRuleHints = computed(() => [
  '单次消耗积分参与抽奖',
  '积分奖即时到账，其它奖品以站内通知为准',
  '概率按活动权重动态计算（售罄档位自动剔除并重算）',
  '十连 Soft：至少 1 件稀有档（大奖/周边/VIP）',
  `累计 ${info.hardPityThreshold ?? 50} 抽未出神秘大奖则下一次必出神秘大奖档`,
])

const activityList = ref([])
const selectedActivityId = ref(null)
const activitySwitchVisible = ref(false)

const info = reactive({
  activityId: null,
  title: '',
  description: '',
  costPointsPerDraw: 30,
  balance: 0,
  prizes: [],
  pityDrawsSinceJackpot: 0,
  hardPityThreshold: 50,
  prizeWinHeat: [],
  recentDraws: [],
  lotterySurpriseClaimed: false,
})

const historyDialogVisible = ref(false)
const historyTableRows = computed(() =>
  (info.recentDraws || []).map((r) => ({
    kind: r.multiDraw === 1 ? '十连' : '单抽',
    prizeName: (r.prizeName != null && String(r.prizeName).trim() !== '' ? String(r.prizeName).trim() : null) || '—',
  })),
)

const surprisePhaseVisible = ref(false)
const surprisePhase = ref(1)
const surprisePreviewRef = ref(null)

const surpriseDialogTitle = computed(() => {
  if (surprisePhase.value === 3) return '惊喜降临'
  return '温馨提示'
})

function openSurprisePhase1() {
  if (info.lotterySurpriseClaimed) {
    ElMessage.info('彩蛋积分已经领取过了～')
    return
  }
  surprisePhase.value = 1
  surprisePhaseVisible.value = true
}

async function confirmSurpriseClaim() {
  if (surpriseClaimBusy.value) return
  surpriseClaimBusy.value = true
  try {
    const res = await claimLotterySurpriseBonus()
    if (res.code !== 0 || !res.data) return
    const d = res.data
    if (d.alreadyClaimed) {
      info.lotterySurpriseClaimed = true
      ElMessage.info('已经领取过了～')
      surprisePhaseVisible.value = false
      return
    }
    if (d.granted && typeof d.balanceAfter === 'number') {
      info.balance = d.balanceAfter
      info.lotterySurpriseClaimed = true
      await pointsWallet.refresh()
      await loadInfo({ silent: true })
      surprisePhase.value = 3
    }
  } catch {
    /* request 已提示 */
  } finally {
    surpriseClaimBusy.value = false
  }
}

function resetSurpriseFlow() {
  surprisePhase.value = 1
}

function focusSurprisePreview() {
  nextTick(() => {
    surprisePreviewRef.value?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  })
}

function onSurpriseImgError(e) {
  const el = e?.target
  if (el && el.src !== surpriseTeaserImg) {
    el.src = surpriseTeaserImg
  }
}

/** idle | single_shuffle | single_result | ten_shuffle | ten_result */
const phase = ref('idle')

const SHUFFLE_MIN_MS = 1000

function delay(ms) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms)
  })
}

const costPer = computed(() => info.costPointsPerDraw ?? 30)
const tenCost = computed(() => costPer.value * 10)

const ptsToTen = computed(() => Math.max(0, tenCost.value - (info.balance ?? 0)))

const tenProgressPct = computed(() => {
  const need = tenCost.value
  if (need <= 0) return 0
  return Math.min(100, Math.round(((info.balance ?? 0) / need) * 100))
})

const hardPityRemaining = computed(() => {
  const th = info.hardPityThreshold ?? 50
  const cur = info.pityDrawsSinceJackpot ?? 0
  return Math.max(0, th - cur)
})

const sidebarActivities = computed(() => {
  const list = activityList.value
  if (list.length <= VISIBLE_ACTIVITY_LIMIT) return list
  const selId = selectedActivityId.value
  const selected = list.find((a) => a.id === selId)
  const rest = list.filter((a) => a.id !== selId)
  const merged = selected ? [selected, ...rest] : list
  return merged.slice(0, VISIBLE_ACTIVITY_LIMIT)
})

const hasMoreActivities = computed(() => activityList.value.length > VISIBLE_ACTIVITY_LIMIT)

const canSwitchActivity = computed(() => phase.value === 'idle' && !busy.value)

function openActivitySwitch() {
  if (!canSwitchActivity.value || activityList.value.length <= VISIBLE_ACTIVITY_LIMIT) return
  activitySwitchVisible.value = true
}

function onActivityCoverError(e) {
  const el = e?.target
  if (el && el.src !== activityCoverFallback) {
    el.src = activityCoverFallback
  }
}

async function loadActivities() {
  try {
    const res = await getLotteryActivities()
    if (res.code === 0 && Array.isArray(res.data)) {
      activityList.value = res.data
    }
  } catch {
    /* request 已提示 */
  }
}

async function onSelectActivity(id) {
  if (!canSwitchActivity.value) return
  if (selectedActivityId.value === id) return
  selectedActivityId.value = id
  await loadInfo({ activityId: id, silent: true })
}

async function onSelectActivityFromDialog(id) {
  await onSelectActivity(id)
  activitySwitchVisible.value = false
}

function formatPrizePercent(p) {
  const prizes = info.prizes || []
  const total = prizes.reduce((s, x) => s + (x.weight ?? 0), 0)
  if (!total) return '概率 —'
  const pct = (((p.weight ?? 0) / total) * 100).toFixed(2)
  return `概率 ${pct}%`
}

const pieOption = computed(() => {
  const prizes = info.prizes || []
  const filtered = prizes.filter((p) => (p.weight ?? 0) > 0)
  const total = filtered.reduce((s, p) => s + (p.weight ?? 0), 0)
  const data = filtered.map((p, i) => ({
    name: p.name,
    value: p.weight ?? 0,
    itemStyle: { color: CHART_PALETTE[i % CHART_PALETTE.length] },
  }))
  return {
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        const pct =
          total > 0 ? ((((params.value ?? 0) / total) * 100).toFixed(2)) : '0.00'
        return `${params.name}<br/>概率: ${pct}%`
      },
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: 'rgba(0,0,0,0.08)',
      textStyle: { color: '#4e5969', fontSize: 12 },
    },
    series: [
      {
        type: 'pie',
        radius: ['34%', '62%'],
        avoidLabelOverlap: true,
        label: { color: '#4e5969', fontSize: 11 },
        labelLine: { lineStyle: { color: 'rgba(0,0,0,0.15)' } },
        data,
      },
    ],
  }
})

const barOption = computed(() => {
  const rows = info.prizeWinHeat || []
  const names = rows.map((r) => r.prizeName ?? '')
  const vals = rows.map((r) => Number(r.winCount ?? 0))
  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: 'rgba(0,0,0,0.08)',
      textStyle: { color: '#4e5969', fontSize: 12 },
    },
    grid: { left: 10, right: 10, top: 22, bottom: names.some((n) => n.length > 5) ? 40 : 28 },
    xAxis: {
      type: 'category',
      data: names.length ? names : ['暂无数据'],
      axisLabel: { color: '#86909c', fontSize: 10, rotate: names.length > 6 ? 28 : 0 },
      axisLine: { lineStyle: { color: 'rgba(0,0,0,0.12)' } },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#86909c', fontSize: 10 },
      splitLine: { lineStyle: { color: 'rgba(0,0,0,0.06)' } },
    },
    series: [
      {
        type: 'bar',
        data: names.length ? vals : [0],
        barMaxWidth: 28,
        itemStyle: {
          color: '#d4537e',
          borderRadius: [4, 4, 0, 0],
        },
      },
    ],
  }
})

const singleOutcome = ref(null)
const tenResults = ref([])
const jackpotOverlay = ref(false)
const jackpotOverlayText = ref('')

const pointsWallet = usePointsWalletStore()

function poolStockHint(sr) {
  if (sr === -1) return '不限量'
  return `剩余 ${sr}`
}

function poolStockScarce(sr) {
  return sr !== -1 && sr > 0 && sr <= 40
}

function formatOutcome(row) {
  if (!row) return ''
  const detail = row.rewardDetail ? String(row.rewardDetail).trim() : ''
  if (detail) {
    const name = row.prizeName ? String(row.prizeName).trim() : '神秘大奖'
    return `${name}：${detail}`
  }
  if (row.prizeName) return row.prizeName
  return '谢谢参与'
}

async function loadInfo(opts = {}) {
  const silent = opts.silent === true
  const activityId = opts.activityId ?? selectedActivityId.value
  if (!silent) loading.value = true
  try {
    const res = await getLotteryInfo(activityId)
    if (res.code === 0 && res.data) {
      Object.assign(info, res.data)
      if (res.data.activityId != null) {
        selectedActivityId.value = res.data.activityId
      }
    }
  } catch {
    // request.js 已对 HTTP 错误弹出提示；此处避免 mounted 钩子未捕获 Promise 拒绝
  } finally {
    if (!silent) loading.value = false
  }
}

function resetRound() {
  phase.value = 'idle'
  singleOutcome.value = null
  tenResults.value = []
  loadInfo({ silent: true })
}

function maybeJackpot(rows) {
  const hit = rows.find((r) => r.jackpot)
  if (hit) {
    jackpotOverlayText.value = formatOutcome(hit)
    jackpotOverlay.value = true
  }
}

async function syncAfterDraw(res) {
  if (typeof res?.data?.balanceAfter === 'number') {
    info.balance = res.data.balanceAfter
  }
  if (typeof res?.data?.pityDrawsSinceJackpot === 'number') {
    info.pityDrawsSinceJackpot = res.data.pityDrawsSinceJackpot
  }
  await pointsWallet.refresh()
  await loadInfo({ silent: true })
}

async function onSingle() {
  const bal = Number(info.balance ?? 0)
  if (bal < costPer.value) {
    ElMessage.warning(`积分不足，单抽需要 ${costPer.value} 积分`)
    return
  }
  busy.value = true
  phase.value = 'single_shuffle'
  singleOutcome.value = null
  try {
    const [res] = await Promise.all([
      lotteryDraw(1, selectedActivityId.value),
      delay(SHUFFLE_MIN_MS),
    ])
    if (res.code !== 0 || !res.data?.results?.length) {
      phase.value = 'idle'
      return
    }
    singleOutcome.value = res.data.results[0]
    await syncAfterDraw(res)
    phase.value = 'single_result'
    const text = formatOutcome(singleOutcome.value)
    if (singleOutcome.value?.rewardDetail || singleOutcome.value?.grantPoints > 0) {
      ElMessage.success(`恭喜获得：${text}`)
    }
    if (singleOutcome.value?.jackpot) {
      jackpotOverlayText.value = text
      jackpotOverlay.value = true
    }
  } finally {
    busy.value = false
  }
}

async function onTen() {
  const bal = Number(info.balance ?? 0)
  if (bal < tenCost.value) {
    ElMessage.warning(`积分不足，十连需要 ${tenCost.value} 积分`)
    return
  }
  busy.value = true
  phase.value = 'ten_shuffle'
  tenResults.value = []
  try {
    const [res] = await Promise.all([
      lotteryDraw(10, selectedActivityId.value),
      delay(SHUFFLE_MIN_MS + 500),
    ])
    if (res.code !== 0 || !res.data?.results?.length) {
      phase.value = 'idle'
      return
    }
    tenResults.value = res.data.results
    await syncAfterDraw(res)
    phase.value = 'ten_result'
    setTimeout(() => maybeJackpot(res.data.results), 300)
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    await loadActivities()
    await loadInfo()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped src="@/assets/styles/lottery.css"></style>
<style scoped src="@/assets/styles/lottery-stage.css"></style>
<style src="@/assets/styles/lottery-overlays.css"></style>
