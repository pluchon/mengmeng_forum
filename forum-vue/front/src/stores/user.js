import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getUserByIdForLogin } from '../api/user'
import router from '../router'

export const useUserStore = defineStore('user', () => {
  const token = ref('')
  const id = ref('')
  const nickname = ref('')
  const avatarUrl = ref('')
  const isAdmin = ref(0)
  
  // 新增字段
  const remark = ref('')
  const phoneNum = ref('')
  const email = ref('')
  const backgroundUrl = ref('')
  const vipTier = ref(0)
  const vipExpireAt = ref(null)
  const mascotModelId = ref(null)
  /** 0女 1男 2保密 */
  const gender = ref(2)
  /** 0 正常 1 禁言 */
  const state = ref(0)

  const isLoggedIn = computed(() => !!token.value && typeof token.value === 'string')

  async function fetchUserInfo() {
    try {
      const res = await getUserByIdForLogin()
      if (res.code === 0 && res.data) {
        id.value = res.data.id
        nickname.value = res.data.nickname
        avatarUrl.value = res.data.avatarUrl
        isAdmin.value = res.data.isAdmin || 0
        // 同步新字段
        remark.value = res.data.remark || ''
        phoneNum.value = res.data.phoneNum || ''
        email.value = res.data.email || ''
        backgroundUrl.value = res.data.backgroundUrl || ''
        vipTier.value = Number(res.data.vipTier) || 0
        vipExpireAt.value = res.data.vipExpireAt ?? null
        mascotModelId.value = res.data.mascotModelId ?? null
        gender.value = res.data.gender != null ? Number(res.data.gender) : 2
        state.value = res.data.state != null ? Number(res.data.state) : 0
      } else {
        logout()
      }
    } catch (error) {
      logout()
    }
  }

  function login(newToken) {
    token.value = newToken
    fetchUserInfo()
  }

  function logout() {
    token.value = ''
    id.value = ''
    nickname.value = ''
    avatarUrl.value = ''
    isAdmin.value = 0
    remark.value = ''
    phoneNum.value = ''
    email.value = ''
    backgroundUrl.value = ''
    vipTier.value = 0
    vipExpireAt.value = null
    mascotModelId.value = null
    gender.value = 2
    state.value = 0
    router.push('/sign-in')
  }

  return {
    token, id, nickname, avatarUrl, isAdmin,
    remark, phoneNum, email, backgroundUrl,
    vipTier, vipExpireAt, mascotModelId, gender, state,
    isLoggedIn, fetchUserInfo, login, logout
  }
}, {
  persist: true
})
