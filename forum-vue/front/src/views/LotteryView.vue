<template>
  <div v-loading="loading" class="lottery-page shell-page-scroll animate-fade-in">
    <div class="lottery-gacha-layout">
      <!-- 左栏 -->
      <aside class="lottery-col lottery-col--left">
        <section class="lottery-card lottery-pool-card">
          <button
            type="button"
            class="lottery-record-icon-btn"
            title="我的抽奖记录"
            aria-label="我的抽奖记录"
            @click="openHistoryDialog"
          >
            <img :src="recordIconUrl" alt="" />
          </button>
          <div class="lottery-pool-card__head">
            <h1 class="lottery-pool-card__title">
              {{ info.title || '积分幸运抽' }}
              <span class="lottery-pool-tag">{{ currentPoolTag }}</span>
            </h1>
          </div>
          <p v-if="poolScheduleText" class="lottery-pool-card__schedule">{{ poolScheduleText }}</p>
          <div class="lottery-pity">
            <div class="lottery-pity__row">
              <span>大保底进度</span>
              <strong>{{ pityCurrent }} / {{ pityThreshold }}</strong>
            </div>
            <div class="lottery-pity__track">
              <div class="lottery-pity__fill" :style="{ width: `${pityPercent}%` }" />
            </div>
          </div>
        </section>

        <section class="lottery-card lottery-feed-card">
          <h2 class="lottery-card__title">刚刚有人抽到了</h2>
          <div v-if="publicFeed.length" class="lottery-feed-list">
            <div
              v-for="(row, idx) in publicFeed"
              :key="`${row.nickname}-${row.prizeName}-${idx}`"
              class="lottery-feed-item"
            >
              <span class="lottery-feed-avatar">{{ row.avatarChar || '用' }}</span>
              <span class="lottery-feed-text">
                {{ maskFeedNickname(row.nickname) }} 获得了「{{ row.prizeName }}」
              </span>
            </div>
          </div>
          <p v-else class="lottery-empty-hint">暂无公开中奖动态</p>
          <div v-if="showPublicFeedPager" class="lottery-side-pager">
            <AppPagination
              v-model:current-page="publicFeedPage"
              size="small"
              :page-size="5"
              :total="publicFeedTotal"
              :pager-count="5"
              :show-jumper="false"
              @current-change="onPublicFeedPageChange"
            />
          </div>
        </section>

        <section class="lottery-card lottery-pick-card">
          <div class="lottery-card__title-row">
            <h2 class="lottery-card__title">选择卡池</h2>
            <span class="lottery-card__sub">1 积分 = 1 萌币</span>
          </div>
          <div v-if="activityList.length" class="lottery-pick-list">
            <button
              v-for="act in activityList"
              :key="act.id"
              type="button"
              class="lottery-pick-item"
              :class="{ 'is-active': selectedActivityId === act.id }"
              :disabled="!canSwitchActivity"
              @click="onSelectActivity(act.id)"
            >
              <span class="lottery-pick-dot" />
              <span class="lottery-pick-meta">
                <span class="lottery-pick-name">{{ act.title }}</span>
                <span class="lottery-pick-cost">{{ act.costPointsPerDraw ?? '—' }} 积分/次</span>
              </span>
              <span class="lottery-pick-badges">
                <span class="lottery-pick-badge lottery-pick-badge--type">{{ poolDurationLabel(act) }}</span>
                <span
                  v-if="isHotPool(act)"
                  class="lottery-pick-badge lottery-pick-badge--hot"
                >HOT</span>
              </span>
            </button>
          </div>
          <p v-else class="lottery-empty-hint">暂无开放中的卡池</p>
          <div class="lottery-side-pager">
            <AppPagination
              v-model:current-page="activityPage"
              size="small"
              :page-size="ACTIVITY_PAGE_SIZE"
              :total="activityTotal"
              :pager-count="5"
              :show-jumper="false"
              :hide-on-single-page="false"
              :disabled="!canSwitchActivity"
              @current-change="onActivityPageChange"
            />
          </div>
        </section>
      </aside>

      <!-- 中栏 -->
      <section class="lottery-col lottery-col--mid">
        <div class="lottery-card lottery-stage-card">
          <div class="lottery-draw-body" :class="{ 'is-busy': phase !== 'idle' }">
            <div class="lottery-stage-scene">
              <img
                class="lottery-stage-bg"
                :src="lotteryBackgroundUrl"
                alt=""
                draggable="false"
              >
              <video
                ref="gachaVideoRef"
                class="lottery-stage-gacha-src"
                :src="gachaVideoUrl"
                muted
                playsinline
                webkit-playsinline
                preload="auto"
                @loadeddata="onGachaVideoLoaded"
                @seeked="paintGachaFrame"
                @ended="onGachaVideoEnded"
                @error="onGachaVideoError"
              />
              <canvas
                ref="gachaCanvasRef"
                class="lottery-stage-gacha"
                aria-hidden="true"
              />
            </div>
            <div class="lottery-action-row">
              <button
                type="button"
                class="lottery-action-btn lottery-action-btn--single"
                :disabled="busy"
                @click="onSingle"
              >
                <template v-if="busy">
                  <span class="lottery-action-label">抽奖中</span>
                </template>
                <template v-else>
                  <span v-if="singleVoucherUsed > 0" class="lottery-action-voucher">券×{{ singleVoucherUsed }}</span>
                  <el-icon :size="18"><Coin /></el-icon>
                  <span class="lottery-action-label">单抽</span>
                  <span class="lottery-action-cost">
                    <template v-if="singleVoucherUsed > 0">
                      <s class="lottery-action-cost__old">{{ costPer }} 积分</s>
                      <strong>{{ singlePayPoints }} 积分</strong>
                    </template>
                    <template v-else>{{ singlePayPoints }} 积分</template>
                  </span>
                </template>
              </button>
              <button
                type="button"
                class="lottery-action-btn lottery-action-btn--ten"
                :disabled="busy"
                @click="onTen"
              >
                <template v-if="busy">
                  <span class="lottery-action-label">抽奖中</span>
                </template>
                <template v-else>
                  <span v-if="tenVoucherUsed > 0" class="lottery-action-voucher">券×{{ tenVoucherUsed }}</span>
                  <el-icon :size="18"><Grid /></el-icon>
                  <span class="lottery-action-label">十连</span>
                  <span class="lottery-action-cost">
                    <template v-if="tenVoucherUsed > 0">
                      <s class="lottery-action-cost__old">{{ costPer * 10 }} 积分</s>
                      <strong>{{ tenPayPoints }} 积分</strong>
                    </template>
                    <template v-else>{{ tenPayPoints }} 积分</template>
                  </span>
                </template>
              </button>
            </div>
          </div>
        </div>

        <div class="lottery-mid-bottom">
          <div class="lottery-mid-bottom__left">
            <section class="lottery-card lottery-product-card" aria-label="抽奖产物萌星辉">
              <div class="lottery-product">
                <div class="lottery-product__visual" aria-hidden="true">
                  <img class="lottery-product__icon" :src="mengXinghuiIconUrl" alt="" />
                </div>
                <div class="lottery-product__meta">
                  <div class="lottery-product__name-row">
                    <span class="lottery-product__name">萌星辉</span>
                    <span class="lottery-product__count">×{{ starlightBalance }}</span>
                  </div>
                </div>
                <div class="lottery-product__actions">
                  <button type="button" class="lottery-product__rules" @click="openStarlightRules">
                    星辉规则
                  </button>
                  <button type="button" class="lottery-product__shop" @click="goExchangeShop">
                    兑换商城
                  </button>
                </div>
              </div>
            </section>

            <section class="lottery-card lottery-task-card">
              <div class="lottery-card__title-row lottery-task-card__title-row">
                <div class="lottery-task-card__title-left">
                  <h2 class="lottery-card__title">本池专属任务</h2>
                  <span class="lottery-task-card__hold">持有抵扣券 {{ voucherBalance }}</span>
                </div>
                <span class="lottery-card__sub">1 张抵扣券 = {{ voucherOffset }} 积分</span>
              </div>
              <div v-if="pagedTasks.length" class="lottery-task-shell">
                <div class="lottery-task-list">
                  <div
                    v-for="task in pagedTasks"
                    :key="task.taskCode"
                    class="lottery-task-item"
                    :class="{ 'is-done': task.status === 'CLAIMED', 'is-ready': task.status === 'CLAIMABLE' }"
                  >
                    <span class="lottery-task-badge">
                      <el-icon v-if="task.status === 'CLAIMABLE' || task.status === 'CLAIMED'" :size="18">
                        <CircleCheckFilled />
                      </el-icon>
                      <el-icon v-else :size="16">
                        <component :is="taskIcon(task.taskCode)" />
                      </el-icon>
                    </span>
                    <div class="lottery-task-copy">
                      <span class="lottery-task-name">{{ task.title }}</span>
                      <span class="lottery-task-reward">×{{ task.voucherReward }} 抵扣券</span>
                    </div>
                    <button
                      type="button"
                      class="lottery-task-btn"
                      :disabled="task.status === 'CLAIMED' || claimingTaskCode === task.taskCode"
                      @click="onTaskAction(task)"
                    >
                      {{ taskActionLabel(task) }}
                    </button>
                  </div>
                </div>
                <div v-if="showTaskPager" class="lottery-side-pager lottery-task-pager">
                  <AppPagination
                    v-model:current-page="taskPage"
                    size="small"
                    :page-size="TASK_PAGE_SIZE"
                    :total="taskTotal"
                    :pager-count="5"
                    :show-jumper="false"
                    @current-change="onTaskPageChange"
                  />
                </div>
              </div>
              <p v-else class="lottery-empty-hint">本池暂无任务</p>
            </section>
          </div>

          <section class="lottery-card lottery-collect-card">
            <div class="lottery-card__title-row">
              <h2 class="lottery-card__title">幸运收集册</h2>
              <span class="lottery-card__sub">{{ collectOwnedCount }} / {{ COLLECT_TOTAL }}</span>
            </div>
            <div class="lottery-collect-grid">
              <button
                v-for="item in pagedCollectIcons"
                :key="item.id"
                type="button"
                class="lottery-collect-cell"
                :class="{ 'is-owned': isCollectOwned(item.id) }"
                :title="isCollectOwned(item.id) ? `已收集 #${item.id}` : `未收集 #${item.id}`"
              >
                <span class="lottery-collect-cell__emoji">{{ item.emoji }}</span>
                <span v-if="isCollectOwned(item.id)" class="lottery-collect-cell__mark" aria-hidden="true">✓</span>
              </button>
            </div>
            <div v-if="showCollectPager" class="lottery-side-pager lottery-collect-pager">
              <AppPagination
                v-model:current-page="collectPage"
                size="small"
                :page-size="COLLECT_PAGE_SIZE"
                :total="COLLECT_TOTAL"
                :pager-count="5"
                :show-jumper="false"
                @current-change="onCollectPageChange"
              />
            </div>
            <div class="lottery-collect-progress" aria-label="收集里程奖励">
              <div class="lottery-collect-progress__track">
                <div class="lottery-collect-progress__fill" :style="{ width: `${collectProgressPercent}%` }" />
                <div
                  v-for="ms in COLLECT_MILESTONES"
                  :key="ms.at"
                  class="lottery-collect-milestone"
                  :class="{
                    'is-reached': collectOwnedCount >= ms.at || ms.reachable,
                    'is-claimed': ms.claimed || isMilestoneClaimed(ms.at),
                  }"
                  :style="{ left: `${(ms.at / COLLECT_TOTAL) * 100}%` }"
                  :title="milestoneTitle(ms)"
                >
                  <span class="lottery-collect-milestone__dot" />
                  <span class="lottery-collect-milestone__label">{{ ms.label }}</span>
                </div>
              </div>
            </div>
          </section>
        </div>
      </section>

      <!-- 右栏 -->
      <aside class="lottery-col lottery-col--right">
        <section class="lottery-card lottery-prize-card">
          <div class="lottery-card__title-row">
            <h2 class="lottery-card__title">本期奖品</h2>
          </div>
          <div v-if="displayPrizes.length" class="lottery-prize-grid">
            <article
              v-for="(p, i) in displayPrizes"
              :key="i"
              class="lottery-prize-tile"
              :class="[rarityClass(p.rarity), { 'is-sold-out': isPrizeSoldOut(p) }]"
            >
              <div class="lottery-prize-tile__cover" aria-hidden="true">
                <img :src="resolvePrizeCover(p)" alt="" />
                <span
                  class="lottery-prize-tile__stock"
                  :title="isPrizeSoldOut(p) ? '已售罄' : (p.stockRemaining === -1 ? '不限量' : `剩余 ${p.stockRemaining}`)"
                >
                  {{ isPrizeSoldOut(p) ? '售罄' : formatStockText(p.stockRemaining) }}
                </span>
                <span v-if="p.jackpot" class="lottery-prize-tile__up">UP</span>
              </div>
              <div class="lottery-prize-tile__body">
                <span class="lottery-prize-tile__name" :title="p.name">{{ p.name }}</span>
              </div>
            </article>
          </div>
          <p v-else class="lottery-empty-hint">暂无奖品</p>
        </section>

        <section class="lottery-card lottery-chart-card">
          <div class="lottery-card__title-row">
            <h2 class="lottery-card__title">各个奖品的概率分布</h2>
          </div>
          <div class="lottery-chart-inner">
            <EChart class="lottery-chart" :option="pieOption" />
          </div>
        </section>
      </aside>
    </div>

    <el-dialog
      v-model="historyDialogVisible"
      title="我的抽奖记录"
      width="min(860px, 94vw)"
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
          row-key="id"
        >
          <el-table-column label="奖品" min-width="210" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="lottery-history-prize" :class="prizeTierClass(row.prizeType)">
                {{ row.prizeName }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="rewardDetail" label="奖励" min-width="130" show-overflow-tooltip />
          <el-table-column prop="costMethod" label="消耗方式" min-width="190" show-overflow-tooltip />
          <el-table-column prop="createTime" label="时间" width="140" />
        </el-table>
        <div v-if="historyTotal > historyPageSize" class="lottery-history-pager">
          <AppPagination
            v-model:current-page="historyPage"
            :page-size="historyPageSize"
            :total="historyTotal"
            @current-change="onHistoryPageChange"
          />
        </div>
      </div>
      <div v-else class="lottery-history-empty">
        <img class="lottery-history-empty-icon" :src="recordIconUrl" alt="" />
        <p>暂无抽奖记录</p>
        <p class="lottery-history-empty-sub">参与抽奖后，将按每次开奖结果分页展示。</p>
      </div>
    </el-dialog>

    <el-dialog
      v-model="resultDialogVisible"
      width="min(720px, 96vw)"
      class="lottery-dialog lottery-dialog--result"
      align-center
      append-to-body
      destroy-on-close
      :show-close="true"
      :close-on-click-modal="false"
      @closed="onResultDialogClosed"
    >
      <div class="lottery-result-art">
        <img class="lottery-result-art__bg" :src="lotteryPrizeUrl" alt="" draggable="false">
        <div
          class="lottery-result-art__body"
          :class="{
            'is-single': phase === 'single_result',
            'is-ten': phase === 'ten_result',
          }"
        >
          <template v-if="phase === 'single_result' && singleOutcome">
            <div
              class="lottery-result-chip"
              :class="prizeTierClass(singleOutcome.prizeType)"
            >
              <span class="lottery-result-chip__name">{{ formatResultLabel(singleOutcome) }}</span>
            </div>
          </template>
          <template v-else-if="phase === 'ten_result'">
            <div
              v-for="(item, i) in tenResults"
              :key="i"
              class="lottery-result-chip"
              :class="prizeTierClass(item.prizeType)"
            >
              <span class="lottery-result-chip__name">{{ formatResultLabel(item) }}</span>
            </div>
          </template>
        </div>
        <button type="button" class="lottery-result-art__confirm" @click="closeResultDialog">
          确定
        </button>
      </div>
    </el-dialog>

    <MengXinghuiShop
      v-model="shopDialogVisible"
      :balance="starlightBalance"
      @balance-change="onStarlightBalanceChange"
      @exchanged="onStarlightExchanged"
    />

    <el-dialog
      v-model="starlightRulesVisible"
      title="星辉规则"
      width="min(420px, 92vw)"
      class="lottery-dialog lottery-dialog--starlight-rules"
      align-center
      append-to-body
      destroy-on-close
    >
      <ul class="lottery-starlight-rules__list">
        <li><span class="rarity is-ssr">SSR</span><span>头奖 / 大奖</span><strong>+50</strong></li>
        <li><span class="rarity is-sr">SR</span><span>小奖 / VIP 天</span><strong>+15</strong></li>
        <li><span class="rarity is-r">R</span><span>积分 / 安慰奖</span><strong>+5</strong></li>
        <li><span class="rarity is-n">普通</span><span>谢谢参与等</span><strong>+1</strong></li>
      </ul>
    </el-dialog>
  </div>
</template>

<script setup src="@scripts/views/LotteryView.js"></script>

<style scoped src="@/assets/styles/lottery.css"></style>
<style src="@/assets/styles/lottery-overlays.css"></style>
