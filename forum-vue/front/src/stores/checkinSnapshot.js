import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getCheckinInfo } from '@/api/checkin'
import { useUserStore } from '@/stores/user'

// 签到状态快照：供顶栏、首页条、签到页共用，避免签后返回其它页数据不同步
export const useCheckinSnapshotStore = defineStore('checkinSnapshot', () => {
  const streakDays = ref(0)
  const totalDays = ref(0)
  const totalPoints = ref(0)
  const todaySigned = ref(false)
  const loaded = ref(false)

  function applyFromInfo(data) {
    if (!data) return
    streakDays.value = data.streakDays ?? 0
    totalDays.value = data.totalDays ?? 0
    totalPoints.value = data.totalPoints ?? 0
    todaySigned.value = !!data.todaySigned
    loaded.value = true
  }

  function clear() {
    streakDays.value = 0
    totalDays.value = 0
    totalPoints.value = 0
    todaySigned.value = false
    loaded.value = false
  }

  async function refresh() {
    const userStore = useUserStore()
    if (!userStore.isLoggedIn) {
      clear()
      return
    }
    try {
      const res = await getCheckinInfo()
      if (res.code === 0 && res.data) applyFromInfo(res.data)
      else loaded.value = false
    } catch {
      loaded.value = false
    }
  }

  return {
    streakDays,
    totalDays,
    totalPoints,
    todaySigned,
    loaded,
    applyFromInfo,
    clear,
    refresh,
  }
})
