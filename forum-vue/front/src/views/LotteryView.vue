<template>
  <div v-loading="loading" class="lottery-page shell-page-scroll animate-fade-in">
    <div class="lottery-inner">
      <header class="lottery-topbar">
        <div class="lottery-title-block">
          <el-icon :size="26"><Trophy /></el-icon>
          <h1 class="lottery-title">积分幸运抽奖</h1>
        </div>
      </header>

      <div class="lottery-scroll-body">
      <div class="lottery-main-grid">
        <!-- 左：抽奖状态 -->
        <aside class="lottery-glass lottery-info-col">
          <button
            type="button"
            class="lottery-record-icon-btn"
            aria-label="我的抽奖记录"
            title="我的抽奖记录"
            @click="openHistoryDialog"
          >
            <img :src="recordIconUrl" alt="" />
          </button>

          <div class="lottery-info-main">
            <h2 class="lottery-info-heading">抽奖状态</h2>

            <div class="lottery-pity-row">
              <span>硬保底</span>
              <strong>{{ hardPityRemaining <= 0 ? (jackpotAvailable ? '下次必得' : '头奖已发完') : `剩余 ${hardPityRemaining} 抽` }}</strong>
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

        <!-- 中：抽奖操作 -->
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
                  <span v-if="item.grantPoints > 0" class="lottery-ten-result-chip__pts">+{{ item.grantPoints }} 积分</span>
                </div>
              </div>
              <button type="button" class="lottery-draw-confirm-btn" @click="resetRound">确定</button>
            </div>

            <div v-if="phase === 'idle'" class="lottery-draw-idle-wrap">
              <h3 class="lottery-draw-strategy-title">抽奖</h3>
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
                  <span class="play-copy">
                    <span class="play-label">单抽</span>
                    <span class="play-cost">{{ costPer }} 积分</span>
                  </span>
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
                  <span class="play-copy">
                    <span class="play-label">十连</span>
                    <span class="play-cost">{{ tenCost }} 积分</span>
                  </span>
                </button>
              </div>
            </div>
          </div>
        </section>

        <!-- 右：核心规则 -->
        <aside class="lottery-glass lottery-cost-col">
          <div class="lottery-cost-head">核心规则</div>
          <div class="lottery-core-rules">
            <div class="lottery-core-rule">
              <span>抽取消耗</span>
              <strong>{{ costPer }} / {{ tenCost }} 积分</strong>
            </div>
            <div class="lottery-core-rule">
              <span>十连保底</span>
              <strong>至少 1 件稀有档</strong>
            </div>
            <div class="lottery-core-rule">
              <span>硬保底</span>
              <strong>{{ info.hardPityThreshold ?? 50 }} 抽未出必得</strong>
            </div>
          </div>
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
              class="lottery-pool-chip lottery-prize-chip"
              :class="{ jackpot: p.jackpot }"
            >
              <span class="chip-name lottery-prize-chip__name">{{ p.name }}</span>
              <span class="chip-meta lottery-prize-chip__stock" :class="{ scarce: poolStockScarce(p.stockRemaining) }">{{
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
        </div>
      </div>
      </div>
    </div>

    <el-dialog
      v-model="historyDialogVisible"
      title="我的抽奖记录"
      width="min(620px, 92vw)"
      class="lottery-dialog lottery-dialog--history"
      align-center
      append-to-body
      destroy-on-close
    >
      <div v-if="historyLoading || historyTableRows.length">
        <el-table
          v-loading="historyLoading"
          class="lottery-history-table"
          :data="historyTableRows"
          size="small"
          stripe
          border
        >
          <el-table-column type="index" label="#" width="48" />
          <el-table-column prop="kind" label="抽奖类型" width="88" />
          <el-table-column prop="prizeName" label="奖品" min-width="140" show-overflow-tooltip />
          <el-table-column prop="rewardDetail" label="奖励" min-width="120" show-overflow-tooltip />
          <el-table-column prop="createTime" label="时间" width="140" />
        </el-table>
        <div v-if="historyTotal > historyPageSize" class="lottery-history-pager">
          <el-pagination
            v-model:current-page="historyPage"
            :page-size="historyPageSize"
            :total="historyTotal"
            layout="prev, pager, next"
            small
            background
            @current-change="onHistoryPageChange"
          />
        </div>
      </div>
      <div v-else class="lottery-history-empty">
        <img class="lottery-history-empty-icon" :src="recordIconUrl" alt="" />
        <p>暂无抽奖记录</p>
        <p class="lottery-history-empty-sub">参与单抽或十连后，将在此处分页展示历史结果。</p>
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

<script setup src="@scripts/views/LotteryView.js"></script>

<style scoped src="@/assets/styles/lottery.css"></style>
<style scoped src="@/assets/styles/lottery-stage.css"></style>
<style src="@/assets/styles/lottery-overlays.css"></style>
