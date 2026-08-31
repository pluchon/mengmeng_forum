import { computed, ref, watch } from 'vue'
import { getVipStatus } from '@/api/vip'
import { isVipActive } from '@/utils/vip'
import { VIP_STATUS_ICONS } from '@/constants/vipStatusIcons'

// 顶栏会员入口：档位以 economy /vip/status 为准 user_vip_subscription ， 不能依赖 auth.user 快照 开通后可能长期不同步
export function useVipStatusEntry(userStore) {
  const vipDialogVisible = ref(false)
  const statusTier = ref(Number(userStore.vipTier) || 0)
  const statusExpireAt = ref(userStore.vipExpireAt ?? null)
  let loadSeq = 0

  const vipActiveNow = computed(() => isVipActive(statusTier.value, statusExpireAt.value))

  const vipStatusMode = computed(() => {
    if (!vipActiveNow.value) return 'buy'
    const tier = Number(statusTier.value) || 0
    if (tier >= 2) return 'max'
    if (tier >= 1) return 'pro'
    return 'buy'
  })

  const vipStatusLabel = computed(() => {
    if (vipStatusMode.value === 'max') return 'MAX 会员 · 已开通'
    if (vipStatusMode.value === 'pro') return 'PRO 会员 · 已开通'
    return '购买 VIP'
  })

  const vipStatusPillClass = computed(() => `vip-status-pill--${vipStatusMode.value}`)

  const vipStatusIcon = computed(() => {
    if (vipStatusMode.value === 'max') return VIP_STATUS_ICONS.maxCrown
    if (vipStatusMode.value === 'pro') return VIP_STATUS_ICONS.proSparkles
    return VIP_STATUS_ICONS.buySparkles
  })

  function applyStatus(tier, expireAt) {
    statusTier.value = Number(tier) || 0
    statusExpireAt.value = expireAt ?? null
    // 同步到 userStore，头像环等共用字段也跟着权威档位走
    userStore.vipTier = statusTier.value
    userStore.vipExpireAt = statusExpireAt.value
  }

  async function refreshVipStatus() {
    if (!userStore.isLoggedIn) {
      applyStatus(0, null)
      return
    }
    const seq = ++loadSeq
    try {
      const res = await getVipStatus()
      if (seq !== loadSeq) return
      if (res?.code === 0 && res.data) {
        applyStatus(res.data.vipTier, res.data.vipExpireAt)
      }
    } catch {
      // 拉取失败时保留上次/快照值，避免把已开通状态误刷成购买
    }
  }

  function openVipPurchase() {
    vipDialogVisible.value = true
  }

  watch(
    () => userStore.isLoggedIn,
    (loggedIn) => {
      if (loggedIn) {
        refreshVipStatus()
      } else {
        applyStatus(0, null)
      }
    },
    { immediate: true },
  )

  // 别处发了会员（背包用体验卡、抽奖中奖）只会更新 userStore，这里跟着走一遍
  watch(
    () => [userStore.vipTier, userStore.vipExpireAt],
    ([tier, expireAt]) => {
      if (!userStore.isLoggedIn) return
      applyStatus(tier, expireAt)
    },
  )

  watch(vipDialogVisible, (open, wasOpen) => {
    // 关闭购买弹窗后回刷一次 试开/支付接通后档位可能变
    if (wasOpen && !open && userStore.isLoggedIn) {
      refreshVipStatus()
    }
  })

  return {
    vipDialogVisible,
    vipStatusIcon,
    vipStatusLabel,
    vipStatusMode,
    vipStatusPillClass,
    openVipPurchase,
    refreshVipStatus,
  }
}
