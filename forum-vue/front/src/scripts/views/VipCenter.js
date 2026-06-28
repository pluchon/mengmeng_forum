import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getVipCenter, getVipQuota, vipSubscribe } from '@/api/vip'
import { useUserStore } from '@/stores/user'
import { resolveAiIcon } from '@/constants/aiModels'

export function useVipCenter() {
  const userStore = useUserStore()
  const loading = ref(false)
  const quotaLoading = ref(false)
  const subLoading = ref(false)
  const center = ref(null)
  const quota = ref(null)

  const plans = computed(() => center.value?.plans || [])
  const points = computed(() => center.value?.points ?? null)
  const vipActive = computed(() => !!center.value?.vipActive)
  const vipTier = computed(() => Number(center.value?.vipTier) || 0)
  const vipExpireAt = computed(() => center.value?.vipExpireAt || null)

  const quotaPanel = computed(() => quota.value || center.value?.quota || null)
  const quotaGroups = computed(() => quotaPanel.value?.groups || [])
  const showQuota = computed(() => vipActive.value && (vipTier.value === 1 || vipTier.value === 2))

  function providerIcon(provider, modelCode) {
    return resolveAiIcon(provider, modelCode)
  }

  function formatCount(n) {
    if (n == null) return '0'
    return Number(n).toLocaleString('zh-CN')
  }

  function formatTokens(n) {
    if (n == null) return '0'
    const v = Number(n)
    if (v >= 1_000_000) return `${(v / 1_000_000).toFixed(2)}M`
    if (v >= 1000) return `${(v / 1000).toFixed(1)}K`
    return String(v)
  }

  function barClass(percent) {
    if (percent >= 90) return 'vip-bar-red'
    if (percent >= 70) return 'vip-bar-amber'
    if (percent >= 40) return 'vip-bar-blue'
    return 'vip-bar-green'
  }

  function usedClass(percent) {
    if (percent >= 90) return 'vip-token-used--danger'
    if (percent >= 70) return 'vip-token-used--warn'
    return ''
  }

  function tierTagClass(tag) {
    if (tag === 'MAX') return 'vip-tier-tag--max'
    if (tag === 'PRO') return 'vip-tier-tag--pro'
    return 'vip-tier-tag--free'
  }

  function scopeTagClass(item) {
    if (item.scopeLabel === '每日') return 'vip-scope-tag--daily'
    if (item.scopeLabel === '本周期') return 'vip-scope-tag--period'
    if (item.scopeLabel === '会员期内') return 'vip-scope-tag--member'
    return ''
  }

  function planCardClass(plan) {
    const classes = ['vip-plan-card']
    if (plan.code === 'pro') classes.push('vip-plan-card--pro')
    else if (plan.code === 'max') classes.push('vip-plan-card--max')
    else classes.push('vip-plan-card--free')
    if (plan.buttonState === 'owned') {
      classes.push('vip-plan-card--owned')
    } else if (plan.code === 'pro' && plan.featured) {
      classes.push('vip-plan-card--featured')
    }
    return classes.join(' ')
  }

  function planNameClass(plan) {
    if (plan.code === 'pro') return 'vip-plan-name vip-plan-name--pro'
    if (plan.code === 'max') return 'vip-plan-name vip-plan-name--max'
    return 'vip-plan-name'
  }

  function planBtnClass(plan) {
    const classes = ['vip-plan-btn']
    if (plan.buttonState === 'current') {
      classes.push('vip-plan-btn--current')
      return classes
    }
    if (plan.buttonState === 'owned') {
      classes.push('vip-plan-btn--owned')
      return classes
    }
    if (plan.code === 'pro') classes.push('vip-plan-btn--pro')
    if (plan.code === 'max') classes.push('vip-plan-btn--max')
    return classes
  }

  function planBtnDisabled(plan) {
    return plan.buttonState === 'owned' || plan.buttonState === 'current'
  }

  function isUnlimited(item) {
    return item.quotaType === 'unlimited'
  }

  function usageUsedText(item) {
    if (isUnlimited(item)) return '∞'
    if (item.unit === 'tokens') return formatTokens(item.used)
    return formatCount(item.used)
  }

  function usageLimitText(item) {
    if (isUnlimited(item)) return ''
    if (item.unit === 'tokens') return formatTokens(item.limit)
    return formatCount(item.limit)
  }

  function usageUnitText(item) {
    if (isUnlimited(item)) return '不限次'
    if (item.unit === 'tokens') return 'tokens'
    return '次'
  }

  function displaySub(item) {
    const hint = item.resetHint || ''
    return hint.replace(/（订阅周期）/g, '').replace(/（自然日）/g, '').trim()
  }

  async function loadCenter() {
    loading.value = true
    try {
      const res = await getVipCenter()
      center.value = res?.data || null
      quota.value = center.value?.quota || null
    } catch {
      ElMessage.error('加载会员中心失败')
    } finally {
      loading.value = false
    }
  }

  async function refreshQuota() {
    quotaLoading.value = true
    try {
      const res = await getVipQuota()
      quota.value = res?.data || null
      ElMessage.success('配额已刷新')
    } catch {
      ElMessage.error('刷新配额失败')
    } finally {
      quotaLoading.value = false
    }
  }

  async function subscribe(plan) {
    if (!plan || plan.buttonState !== 'subscribe' || !plan.tier) return
    const name = `${plan.name}（${plan.pricePoints} 积分 / ${plan.durationDays} 天）`
    try {
      await ElMessageBox.confirm(`确认使用积分订阅 ${name}？续费将从当前到期日顺延。`, '积分订阅', {
        type: 'warning',
        confirmButtonText: '确认扣款',
        cancelButtonText: '取消',
      })
    } catch {
      return
    }
    subLoading.value = true
    const requestId = typeof crypto !== 'undefined' && crypto.randomUUID
      ? crypto.randomUUID().replace(/-/g, '')
      : `${Date.now()}${Math.random().toString(16).slice(2)}`
    try {
      const res = await vipSubscribe({ tier: plan.tier, requestId })
      const d = res?.data
      try {
        await userStore.fetchUserInfo()
      } catch {
        /* ignore */
      }
      ElMessage.success(`订阅成功，到期：${d?.vipExpireAt || ''}，积分：${d?.pointsBalance ?? ''}`)
      await loadCenter()
    } catch (e) {
      ElMessage.error(e?.response?.data?.message || e?.message || '订阅失败')
    } finally {
      subLoading.value = false
    }
  }

  onMounted(() => {
    loadCenter()
  })

  return {
    loading,
    quotaLoading,
    subLoading,
    plans,
    points,
    vipActive,
    vipTier,
    vipExpireAt,
    quotaPanel,
    quotaGroups,
    showQuota,
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
    loadCenter,
  }
}
