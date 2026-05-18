<template>
  <div class="vip-page animate-fade-in" v-loading="loading">
    <div class="vip-bg" :style="{ backgroundImage: `url(${bgUrl})` }" />

    <div class="vip-inner">
      <header class="vip-topbar">
        <h1 class="vip-title">萌萌论坛 · 会员方案</h1>
      </header>

      <div class="vip-plan-grid">
        <div
          v-for="plan in plans"
          :key="plan.code"
          class="vip-ring-host"
          :class="{ 'vip-ring-host--on': vipActive }"
        >
          <article :class="planCardClass(plan)">
            <div>
              <div v-if="plan.badge" class="vip-plan-head">
                <div :class="planNameClass(plan)">{{ plan.name }}</div>
                <span
                  class="vip-plan-badge"
                  :class="plan.code === 'pro' ? 'vip-plan-badge--pro' : 'vip-plan-badge--max'"
                >{{ plan.badge }}</span>
              </div>
              <template v-else>
                <div :class="planNameClass(plan)">{{ plan.name }}</div>
              </template>
              <div class="vip-plan-sub">{{ plan.subtitle }}</div>
            </div>

            <div class="vip-plan-price">
              <template v-if="plan.tier === 0">
                <strong>免费</strong>
              </template>
              <template v-else>
                <strong :class="plan.code === 'pro' ? 'price-pro' : 'price-max'">{{ plan.pricePoints }}</strong>
                积分 / {{ plan.durationDays }} 天
              </template>
            </div>

            <div class="vip-feat-list">
              <div
                v-for="(f, idx) in plan.features"
                :key="idx"
                class="vip-feat"
                :class="f.enabled ? 'vip-feat--on' : 'vip-feat--off'"
              >
                <el-icon><Check v-if="f.enabled" /><Close v-else /></el-icon>
                <span>{{ f.text }}</span>
              </div>
            </div>

            <button
              type="button"
              :class="planBtnClass(plan)"
              :disabled="planBtnDisabled(plan)"
              @click="subscribe(plan)"
            >
              <span v-if="subLoading && plan.buttonState === 'subscribe'" class="vip-plan-btn__loading" />
              {{ plan.buttonLabel }}
            </button>
          </article>
        </div>
      </div>

      <div v-if="showQuota" class="vip-quota-block">
        <div
          class="vip-ring-host"
          :class="{ 'vip-ring-host--on': vipActive }"
        >
        <section class="vip-quota-section" v-loading="quotaLoading">
          <div class="vip-quota-head">
            <div class="vip-quota-title">
              <el-icon><Cpu /></el-icon>
              本期模型用量 · {{ quotaPanel?.tierLabel || 'VIP' }} 配额
            </div>
            <div class="vip-quota-meta">
              <span v-if="periodResetLabel" class="vip-quota-meta__date">{{ periodResetLabel }}</span>
              <el-button size="small" round :loading="quotaLoading" @click="refreshQuota">
                <el-icon class="el-icon--left"><Refresh /></el-icon>
                刷新
              </el-button>
            </div>
          </div>

          <div
            v-for="(group, gi) in quotaGroups"
            :key="gi"
            class="vip-model-group"
          >
            <div class="vip-model-group-label">{{ group.label }}</div>
            <div
              v-for="item in group.items"
              :key="item.quotaKey"
              class="vip-quota-item"
            >
              <div class="vip-quota-item__main">
                <div class="vip-model-name">
                  <img
                    v-if="item.iconProvider"
                    :src="providerIcon(item.iconProvider)"
                    alt=""
                    class="vip-model-icon"
                  >
                  <span class="vip-model-name__text">{{ item.displayName }}</span>
                  <span
                    v-if="item.scopeLabel && !isUnlimited(item)"
                    class="vip-scope-tag"
                    :class="scopeTagClass(item)"
                  >{{ item.scopeLabel }}</span>
                  <span
                    v-if="item.tierTag"
                    class="vip-tier-tag"
                    :class="tierTagClass(item.tierTag)"
                  >{{ item.tierTag }}</span>
                </div>
                <div v-if="!isUnlimited(item)" class="vip-bar-wrap">
                  <div
                    class="vip-bar-fill"
                    :class="barClass(item.percent || 0)"
                    :style="{ width: `${Math.min(100, item.percent || 0)}%` }"
                  />
                </div>
              </div>
              <div class="vip-quota-item__meter" :class="usedClass(item.percent || 0)">
                <template v-if="isUnlimited(item)">
                  <div class="vip-meter vip-meter--unlimited">
                    <span class="vip-meter__infinity" title="不限次">∞</span>
                  </div>
                </template>
                <template v-else>
                  <div class="vip-meter">
                    <span class="vip-meter__used">{{ usageUsedText(item) }}</span>
                    <span class="vip-meter__sep">/</span>
                    <span class="vip-meter__limit">{{ usageLimitText(item) }}</span>
                    <span class="vip-meter__unit">{{ usageUnitText(item) }}</span>
                  </div>
                </template>
                <div v-if="displaySub(item) && !isUnlimited(item)" class="vip-meter__hint">{{ displaySub(item) }}</div>
              </div>
            </div>
          </div>
        </section>
        </div>

        <div
          v-if="quotaPanel"
          class="vip-ring-host vip-ring-host--summary"
          :class="{ 'vip-ring-host--on': vipActive }"
        >
          <div class="vip-summary-card">
            <p class="vip-summary-line">
              <span class="vip-summary-main">
                <span class="vip-summary-part">
                  当前套餐：
                  <strong
                    class="vip-summary-strong"
                    :class="quotaPanel.tierLabel === 'MAX' ? 'vip-summary-strong--max' : 'vip-summary-strong--pro'"
                  >{{ quotaPanel.tierLabel }}</strong>
                </span>
                <span class="vip-summary-part">
                  本期总调用 <strong class="vip-summary-strong">{{ formatCount(quotaPanel.totalCalls) }}</strong> 次大模型
                </span>
                <span class="vip-summary-part">
                  tokens 消耗 <strong class="vip-summary-strong">{{ formatTokens(quotaPanel.totalTokensUsed) }}</strong>
                </span>
              </span>
              <span v-if="vipExpireAt" class="vip-summary-expire">会员到期为 {{ formatDate(vipExpireAt) }}</span>
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Check, Close, Cpu, Refresh } from '@element-plus/icons-vue'
import bgUrl from '@/assets/images/vip.jpg'
import { useVipCenter } from '@scripts/views/VipCenter'

function formatDate(v) {
  if (!v) return '—'
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return '—'
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const {
  loading,
  quotaLoading,
  subLoading,
  plans,
  vipActive,
  vipExpireAt,
  quotaPanel,
  quotaGroups,
  showQuota,
  periodResetLabel,
  providerIcon,
  barClass,
  usedClass,
  tierTagClass,
  scopeTagClass,
  planCardClass,
  planNameClass,
  planBtnClass,
  planBtnDisabled,
  isUnlimited,
  usageUsedText,
  usageLimitText,
  usageUnitText,
  displaySub,
  formatCount,
  formatTokens,
  refreshQuota,
  subscribe,
} = useVipCenter()
</script>

<style scoped src="@/assets/styles/vip-center.css"></style>
