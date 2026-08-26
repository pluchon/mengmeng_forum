<template>
  <el-dialog
    v-model="visible"
    class="vip-subscribe-dialog"
    width="1200px"
    align-center
    destroy-on-close
    :show-close="false"
    :close-on-click-modal="true"
    append-to-body
  >
    <!-- 画布尺寸对齐 UI/vip/vip.html：主体 1200×672，安全区绝对定位 -->
    <div class="vip-canvas" v-loading="loading">
      <img class="vip-canvas__bg" :src="vipBgUrl" alt="" draggable="false" />

      <button type="button" class="vip-canvas__close" aria-label="关闭" @click="close">
        <span aria-hidden="true">×</span>
      </button>

      <!-- 会员方案内容安全区：left 260 top 53 w 575 -->
      <div class="vip-safe">
        <h2 class="vip-safe__title">加入萌部落社区大家庭</h2>

        <div class="vip-plans">
          <article
            v-for="plan in planCards"
            :key="plan.code"
            class="vip-plan"
            :class="[
              `vip-plan--${plan.code}`,
              { 'is-selected': selectedCode === plan.code, 'is-display-only': plan.tier === 0 },
            ]"
            @click="selectPlan(plan)"
          >
            <div class="vip-plan__visual">
              <img
                class="vip-plan__visual-img"
                :src="planVisualUrl(plan.code)"
                alt=""
                draggable="false"
              />
              <span class="vip-plan__visual-mask" aria-hidden="true" />
              <span
                v-if="plan.code === 'free' && (plan.buttonState === 'current' || plan.buttonState === 'owned')"
                class="vip-plan__status"
              >当前方案</span>
              <span
                v-else-if="plan.code === 'pro'"
                class="vip-plan__status"
              >大众常选</span>
              <span
                v-else-if="plan.code === 'max'"
                class="vip-plan__status"
              >尊享特权</span>
            </div>

            <div class="vip-plan__name-row">
              <div class="vip-plan__name">{{ plan.name }}</div>
              <span
                v-if="plan.tier > 0 && plan.buttonState === 'subscribe' && plan.firstPurchaseEligible && plan.cash.save"
                class="vip-plan__save"
              >直降 {{ formatYuan(plan.cash.save) }}元</span>
            </div>

            <div class="vip-plan__price">
              <template v-if="plan.tier === 0">
                <span class="vip-plan__price-num">¥0</span>
                <span class="vip-plan__price-cycle">/ 月</span>
              </template>
              <template v-else-if="plan.buttonState === 'subscribe' && plan.firstPurchaseEligible">
                <div class="vip-plan__promo">
                  <s class="vip-plan__origin">¥{{ formatYuan(plan.cash.original) }}</s>
                  <span class="vip-plan__first">¥{{ formatYuan(plan.cash.firstMonth) }}</span>
                  <span class="vip-plan__first-tag">首月</span>
                </div>
              </template>
              <template v-else>
                <span class="vip-plan__price-num">¥{{ formatYuan(plan.cash.original) }}</span>
                <span class="vip-plan__price-cycle">/ 月</span>
              </template>
            </div>

            <div class="vip-plan__divider" />

            <ul class="vip-plan__feats">
              <li v-for="(text, idx) in plan.features" :key="idx">{{ text }}</li>
            </ul>

            <button
              type="button"
              class="vip-plan__btn"
              :disabled="plan.buttonState === 'current' || plan.buttonState === 'owned'"
              v-if="plan.tier > 0"
              @click.stop="selectPlan(plan)"
            >
              {{
                plan.buttonState === 'current' || plan.buttonState === 'owned'
                  ? (plan.buttonState === 'current' ? '当前方案' : plan.buttonLabel)
                  : (plan.tier === 0 ? '当前方案' : `选择 ${plan.name}`)
              }}
            </button>
          </article>
        </div>

        <div class="vip-commons">
          <div
            v-for="item in commonsItems"
            :key="item.key"
            class="vip-commons__item"
            :style="{ left: item.left }"
          >
            <svg class="vip-commons__icon" viewBox="0 0 14 14" aria-hidden="true">
              <path fill-rule="evenodd" :fill="item.icon.fill" :d="item.icon.d" />
            </svg>
            <div class="vip-commons__text">
              <strong>{{ item.title }}</strong>
              <span>{{ item.desc }}</span>
            </div>
          </div>
          <span class="vip-commons__soon">即将开放</span>
        </div>
      </div>

      <!-- 会员支付岛：left 914 top 78 w 248 h 530 -->
      <aside class="vip-island">
        <h3 class="vip-island__title">{{ payTitle }}</h3>

        <template v-if="selectedPlan?.tier > 0">
          <div class="vip-island__price">
            <template v-if="showFirstMonth">
              <s class="vip-island__origin">¥{{ formatYuan(selectedPlan.cash.original) }}</s>
              <span class="vip-island__first">¥{{ formatYuan(displayPrice) }}</span>
              <span class="vip-island__first-tag">首月</span>
            </template>
            <template v-else>
              <span class="vip-island__first">¥{{ formatYuan(displayPrice) }}</span>
              <span class="vip-island__first-tag">/ 月</span>
            </template>
          </div>
          <p class="vip-island__hint">{{ showFirstMonth ? '新用户专享优惠' : '续费按原价计费' }}</p>

          <div class="vip-island__qr" aria-hidden="true">
            <div class="vip-island__qr-fake" />
          </div>

          <div class="vip-island__channels">
            <button
              type="button"
              class="vip-island__channel"
              :class="{ 'is-on': payChannel === 'alipay' }"
              @click="payChannel = 'alipay'"
            >
              <img class="vip-island__pay-icon" :src="alipayIconUrl" alt="" />
              支付宝
            </button>
            <button
              type="button"
              class="vip-island__channel"
              :class="{ 'is-on': payChannel === 'wechat' }"
              @click="payChannel = 'wechat'"
            >
              <img class="vip-island__pay-icon" :src="wechatPayIconUrl" alt="" />
              微信
            </button>
          </div>

          <label class="vip-island__agree">
            <input v-model="agreeProtocol" class="vip-island__agree-check" type="checkbox" />
            <span>
              开通即代表同意
              <a
                class="vip-island__agree-link"
                href="javascript:;"
                @click.stop.prevent="openVipAgreement"
              >《会员服务协议》</a>
            </span>
          </label>
          <div class="vip-island__line" />
          <p class="vip-island__tip">购买后若权益没到账，立即联系客服补偿</p>
          <div class="vip-island__contacts">
            <a href="mailto:zlh8232@outlook.com" class="vip-island__contact">
              <img :src="outlookIconUrl" alt="" />
              <span>Outlook邮箱：zlh8232@outlook.com</span>
            </a>
          </div>
        </template>
        <template v-else>
          <p class="vip-island__free">免费方案无需支付，选择 PRO / MAX 后在此扫码开通。</p>
        </template>
      </aside>

      <!-- 左侧会员权益小卡：left 28 top 492 -->
      <div class="vip-pass">
        <div class="vip-pass__head">
          <strong>星愿通行证</strong>
          <button type="button" class="vip-pass__history" title="购买记录" aria-label="查看购买记录" @click="openPurchaseHistory">
            <el-icon><Tickets /></el-icon>
          </button>
        </div>
        <span>解锁限定装扮与社区完整服务</span>
      </div>
      <div v-if="membershipExpiryText" class="vip-pass-expiry">
        {{ membershipExpiryText }}
      </div>
    </div>
  </el-dialog>

  <el-dialog
    v-model="purchaseHistoryVisible"
    class="vip-history-dialog"
    width="620px"
    align-center
    append-to-body
    destroy-on-close
    title="购买记录"
  >
    <div class="vip-history" v-loading="purchaseHistoryLoading">
      <div v-if="purchaseRecords.length" class="vip-history__list">
        <article v-for="record in purchaseRecords" :key="record.id" class="vip-history__item">
          <div class="vip-history__main">
            <span class="vip-history__tier" :class="`is-${String(record.tierLabel).toLowerCase()}`">
              {{ record.tierLabel }}
            </span>
            <div class="vip-history__summary">
              <strong>星愿通行证</strong>
              <span>{{ formatPurchaseDateTime(record.createTime) }}</span>
            </div>
            <strong class="vip-history__amount">¥{{ formatYuan(record.paidAmount) }}</strong>
            <span class="vip-history__state" :class="paymentStateClass(record.paymentState)">
              {{ record.paymentStateLabel }}
            </span>
          </div>
          <div class="vip-history__meta">
            <span>订单号：{{ maskOrderNo(record.paymentOrderNo) }}</span>
            <span v-if="record.periodStart && record.periodEnd">
              权益周期：{{ formatPurchaseDate(record.periodStart) }} 至 {{ formatPurchaseDate(record.periodEnd) }}
            </span>
          </div>
        </article>
      </div>
      <div v-else-if="!purchaseHistoryLoading" class="vip-history__empty">
        <el-icon><Tickets /></el-icon>
        <span>暂无购买记录</span>
      </div>
      <AppPagination
        v-model:current-page="purchaseRecordPage"
        class="vip-history__pagination"
        size="small"
        :total="purchaseRecordTotal"
        :page-size="purchasePageSize"
        :pager-count="5"
        :show-jumper="false"
        :hide-on-single-page="false"
        @current-change="loadPurchaseRecords"
      />
    </div>
  </el-dialog>
</template>

<script setup>
import { Tickets } from '@element-plus/icons-vue'
import AppPagination from '@/components/common/AppPagination.vue'
import { useVipSubscribeDialog } from '@scripts/components/vip/VipSubscribeDialog'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue'])

const {
  vipBgUrl,
  alipayIconUrl,
  wechatPayIconUrl,
  outlookIconUrl,
  visible,
  loading,
  planCards,
  commonsItems,
  selectedCode,
  selectedPlan,
  payChannel,
  agreeProtocol,
  showFirstMonth,
  displayPrice,
  payTitle,
  membershipExpiryText,
  purchaseHistoryVisible,
  purchaseHistoryLoading,
  purchaseRecords,
  purchaseRecordTotal,
  purchaseRecordPage,
  purchasePageSize,
  formatYuan,
  formatPurchaseDateTime,
  formatPurchaseDate,
  maskOrderNo,
  paymentStateClass,
  planVisualUrl,
  selectPlan,
  openVipAgreement,
  openPurchaseHistory,
  loadPurchaseRecords,
  close,
} = useVipSubscribeDialog(props, emit)
</script>

<style scoped lang="scss" src="./VipSubscribeDialog.scss"></style>
