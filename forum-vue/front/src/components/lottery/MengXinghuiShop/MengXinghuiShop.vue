<template>
  <el-dialog
    v-model="visible"
    width="min(1000px, 94vw)"
    align-center
    append-to-body
    destroy-on-close
    :show-close="false"
    class="mxh-shop-dialog"
    modal-class="mxh-shop-modal"
  >
    <div class="mxh-shop">
      <header class="mxh-shop__header">
        <div class="mxh-shop__title-group">
          <img class="mxh-shop__brand-icon" :src="mengXinghuiIconUrl" alt="" aria-hidden="true" />
          <h2 class="mxh-shop__title">萌星辉兑换商城</h2>
        </div>
        <button type="button" class="mxh-shop__close" aria-label="关闭" @click="closeShop">×</button>
      </header>

      <div class="mxh-shop__tabs">
        <button
          v-for="tab in CATEGORIES"
          :key="tab.key"
          type="button"
          class="mxh-shop__tab"
          :class="{ 'is-active': category === tab.key }"
          @click="onCategory(tab.key)"
        >
          {{ tab.label }}
        </button>
        <div class="mxh-shop__balance" aria-label="萌星辉余额">
          <img :src="mengXinghuiIconUrl" alt="" />
          <span>萌星辉 {{ localBalance.toLocaleString('zh-CN') }}</span>
        </div>
      </div>

      <div class="mxh-shop__body">
        <div class="mxh-shop__content">
          <div v-if="loading" class="mxh-shop__state mxh-shop__state--loading" aria-busy="true">
            <span class="mxh-shop__spinner" aria-hidden="true" />
            <span>加载中...</span>
          </div>
          <div v-else-if="error" class="mxh-shop__state mxh-shop__state--error">
            <span>{{ error }}</span>
            <button type="button" class="mxh-shop__retry" @click="loadItems">重试</button>
          </div>
          <div v-else-if="!items.length" class="mxh-shop__empty">
            <img class="mxh-shop__empty-img" :src="emptyShopItemUrl" alt="" />
            <p class="mxh-shop__empty-text">该分类暂无商品</p>
          </div>
          <div v-else class="mxh-shop__grid">
          <article v-for="item in items" :key="item.id" class="mxh-shop-card">
            <div class="mxh-shop-card__media" aria-hidden="true">
              <img
                v-if="isQuotaResetItem(item)"
                class="mxh-shop-card__cover"
                :src="quotaResetCoverUrl"
                alt=""
              />
              <img
                v-else-if="isLotteryVoucherItem(item)"
                class="mxh-shop-card__cover"
                :src="voucherCoverUrl"
                alt=""
              />
              <img
                v-else-if="isMakeupCardItem(item)"
                class="mxh-shop-card__cover"
                :src="makeupCardCoverUrl"
                alt=""
              />
              <span v-else class="mxh-shop-card__placeholder" />
            </div>
            <div class="mxh-shop-card__name-row">
              <span class="mxh-shop-card__name">{{ item.name }}</span>
              <span v-if="item.tag" class="mxh-shop-card__tag">{{ item.tag }}</span>
            </div>
            <div class="mxh-shop-card__meta">
              <span class="mxh-shop-card__price">
                <img :src="mengXinghuiIconUrl" alt="" />
                {{ item.priceStarlight }}
              </span>
              <span class="mxh-shop-card__stock">{{ stockText(item) }}</span>
            </div>
            <button
              type="button"
              class="mxh-shop-card__btn"
              :disabled="exchangingId != null || isSoldOut(item)"
              @click="onExchange(item)"
            >
              {{ exchangingId === item.id ? '兑换中...' : isSoldOut(item) ? '已售罄' : '兑换' }}
            </button>
          </article>
        </div>
        </div>
      </div>

      <footer class="mxh-shop__footer">
        <button type="button" class="mxh-shop__hint-btn" @click="openRules">星辉规则</button>
        <div class="mxh-shop__pager">
          <AppPagination
            :current-page="pageNum"
            size="small"
            :total="total"
            :page-size="SHOP_PAGE_SIZE"
            :pager-count="5"
            :show-jumper="false"
            :hide-on-single-page="false"
            @current-change="goPage"
          />
        </div>
        <button type="button" class="mxh-shop__history-link" @click="openHistory">兑换记录</button>
      </footer>
    </div>

    <el-dialog
      v-model="rulesVisible"
      title="星辉规则"
      width="min(420px, 92vw)"
      class="mxh-rules-dialog"
      align-center
      append-to-body
      destroy-on-close
    >
      <ul class="mxh-rules__list">
        <li><span class="rarity is-ssr">SSR</span><span>头奖 / 大奖</span><strong>+50</strong></li>
        <li><span class="rarity is-sr">SR</span><span>小奖 / VIP 天</span><strong>+15</strong></li>
        <li><span class="rarity is-r">R</span><span>积分 / 安慰奖</span><strong>+5</strong></li>
        <li><span class="rarity is-n">普通</span><span>谢谢参与等</span><strong>+1</strong></li>
      </ul>
    </el-dialog>

    <el-dialog
      v-model="historyVisible"
      width="min(520px, 92vw)"
      title="兑换记录"
      append-to-body
      destroy-on-close
      align-center
      class="mxh-history-dialog"
    >
      <div v-loading="historyLoading" class="mxh-history-body">
        <div class="mxh-history-content">
          <div v-if="historyError" class="mxh-shop__state mxh-shop__state--error">
            <span>{{ historyError }}</span>
            <button type="button" class="mxh-shop__retry" @click="loadHistory">重试</button>
          </div>
          <div v-else-if="!historyLoading && !historyRecords.length" class="mxh-shop__state">暂无兑换记录</div>
          <ul v-else-if="historyRecords.length" class="mxh-history-list">
            <li v-for="row in historyRecords" :key="row.id" class="mxh-history-list__item">
              <strong class="mxh-history-list__name">{{ row.itemName }}</strong>
              <time class="mxh-history-list__time">{{ formatTime(row.createTime) }}</time>
              <span class="mxh-history-list__price">
                <img :src="mengXinghuiIconUrl" alt="" />
                -{{ row.pricePaid }}
              </span>
            </li>
          </ul>
        </div>
        <div class="mxh-history-pager">
          <AppPagination
            size="small"
            :total="historyTotal"
            :page-size="HISTORY_PAGE_SIZE"
            :current-page="historyPage"
            :pager-count="5"
            :show-jumper="false"
            :disabled="historyLoading"
            @current-change="onHistoryPageChange"
          />
        </div>
      </div>
    </el-dialog>
  </el-dialog>
</template>

<script setup src="./MengXinghuiShop.js"></script>
<style lang="scss" src="./MengXinghuiShop.scss"></style>
