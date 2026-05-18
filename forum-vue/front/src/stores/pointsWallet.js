import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getPointsWallet } from '@/api/points'

/** 积分钱包（与签到共用余额；不持久化，进入相关页刷新） */
export const usePointsWalletStore = defineStore('pointsWallet', () => {
  const balance = ref(0)
  const totalCheckinPoints = ref(0)
  const totalSpendPoints = ref(0)
  const loading = ref(false)

  function setBalance(v) {
    balance.value = Number(v) || 0
  }

  async function refresh() {
    loading.value = true
    try {
      const res = await getPointsWallet()
      if (res.code === 0 && res.data) {
        balance.value = Number(res.data.balance) || 0
        totalCheckinPoints.value = Number(res.data.totalCheckinPoints) || 0
        totalSpendPoints.value = Number(res.data.totalSpendPoints) || 0
      }
    } finally {
      loading.value = false
    }
  }

  return {
    balance,
    totalCheckinPoints,
    totalSpendPoints,
    loading,
    setBalance,
    refresh,
  }
})
