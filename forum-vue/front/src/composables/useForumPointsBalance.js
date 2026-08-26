import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { usePointsWalletStore } from '@/stores/pointsWallet'
import { useUserStore } from '@/stores/user'

// 论坛积分余额 与首页顶栏、积分钱包同源，勿用游戏天梯分 score
export function useForumPointsBalance() {
  const pointsWalletStore = usePointsWalletStore()
  const userStore = useUserStore()
  const { balance, loading } = storeToRefs(pointsWalletStore)

  const pointsBalance = computed(() => balance.value)

  async function refreshForumPointsBalance() {
    if (!userStore.isLoggedIn) return
    await pointsWalletStore.refresh()
  }

  return {
    balance,
    pointsBalance,
    pointsBalanceLoading: loading,
    refreshForumPointsBalance,
  }
}
