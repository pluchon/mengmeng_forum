import { onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  getProfileChangeStatus,
  submitProfileChange,
  updateUserInfo,
  uploadAvatar,
  updateAvatarUrl,
} from '@/api/settings'
import { DEFAULT_AVATAR } from '@/utils/constants'
import { openImageUploadLoading, validateLocalImageFile } from '@/utils/imageUploadFeedback'

export function useBasicInfo() {
  const userStore = useUserStore()

  const editing = reactive({
    nickname: false,
    remark: false,
    gender: false,
  })

  const originalValues = reactive({
    nickname: '',
    remark: '',
    gender: 2,
  })

  const profileForm = reactive({
    nickname: '',
    remark: '',
    phoneNum: '',
    email: '',
    gender: 2,
  })

  const saving = ref(false)
  const reviewState = reactive({
    nickname: null,
    remark: null,
  })
  let reviewTimer = null

  onMounted(() => {
    syncFromStore()
    loadReviewStates()
  })

  onUnmounted(() => {
    if (reviewTimer) window.clearTimeout(reviewTimer)
  })

  watch(
    () => [userStore.email, userStore.phoneNum, userStore.nickname, userStore.remark, userStore.gender],
    () => {
      syncFromStore()
    },
  )

  function syncFromStore() {
    profileForm.nickname = userStore.nickname || ''
    profileForm.remark = userStore.remark || ''
    profileForm.phoneNum = userStore.phoneNum || ''
    profileForm.email = userStore.email || ''
    profileForm.gender = userStore.gender != null ? Number(userStore.gender) : 2

    originalValues.nickname = profileForm.nickname
    originalValues.remark = profileForm.remark
    originalValues.gender = profileForm.gender
  }

  async function saveGender() {
    const g = Number(profileForm.gender)
    if (![0, 1, 2].includes(g)) return
    if (g === originalValues.gender) {
      editing.gender = false
      return
    }
    saving.value = true
    try {
      const res = await updateUserInfo({ gender: g })
      if (res.code === 0) {
        ElMessage.success('性别已更新')
        originalValues.gender = g
        profileForm.gender = g
        editing.gender = false
        userStore.patchUserProfile({ gender: g })
      }
    } finally {
      saving.value = false
    }
  }

  function genderLabel(g) {
    const val = Number(g)
    if (val === 0) return '女'
    if (val === 1) return '男'
    return '保密'
  }

  function maskPhone(value) {
    const phone = String(value || '').trim()
    if (phone.length < 7) return phone
    return `${phone.slice(0, 3)}****${phone.slice(-4)}`
  }

  function startEdit(field) {
    editing[field] = true
  }

  function cancelEdit(field) {
    editing[field] = false
    profileForm[field] = originalValues[field]
  }

  async function saveSingleField(field) {
    if (profileForm[field] === originalValues[field]) {
      editing[field] = false
      return
    }

    if (field === 'nickname') {
      const nickname = profileForm.nickname.trim()
      if (!nickname) return ElMessage.warning('昵称不能为空')
      if (!/^[\u4e00-\u9fa5A-Za-z0-9]{2,20}$/.test(nickname)) {
        return ElMessage.warning('昵称需为2–20位中文、英文字母或数字')
      }
      profileForm.nickname = nickname
    }

    const remark = String(profileForm.remark || '').trim()
    if (field === 'remark' && remark.length > 50) {
      return ElMessage.warning('个人简介不能超过50个字')
    }

    saving.value = true
    try {
      const fieldType = field === 'nickname' ? 'NICKNAME' : 'BIO'
      const content = field === 'nickname' ? profileForm.nickname : remark
      const res = await submitProfileChange({ fieldType, content })
      if (res.code === 0) {
        ElMessage.success('已提交审核，通过后将自动生效')
        reviewState[field] = res.data
        editing[field] = false
        profileForm[field] = originalValues[field]
        scheduleReviewRefresh()
      } else {
        ElMessage.error(res.message || '提交审核失败')
      }
    } catch (error) {
      ElMessage.error(error?.response?.data?.message || error?.message || '更新失败')
    } finally {
      saving.value = false
    }
  }

  async function loadReviewStates() {
    try {
      const [nicknameRes, bioRes] = await Promise.all([
        getProfileChangeStatus('NICKNAME'),
        getProfileChangeStatus('BIO'),
      ])
      reviewState.nickname = nicknameRes.code === 0 ? nicknameRes.data : null
      reviewState.remark = bioRes.code === 0 ? bioRes.data : null
      const approved = [reviewState.nickname, reviewState.remark].some(
        (item) => item?.status === 'APPROVED',
      )
      if (approved) {
        await userStore.fetchUserInfo()
        syncFromStore()
      }
      scheduleReviewRefresh()
    } catch {
      // 审核状态加载失败不影响资料页正常使用
    }
  }

  function scheduleReviewRefresh() {
    if (reviewTimer) window.clearTimeout(reviewTimer)
    const pending = [reviewState.nickname, reviewState.remark].some((item) =>
      ['PENDING', 'PROCESSING'].includes(item?.status),
    )
    if (pending) reviewTimer = window.setTimeout(loadReviewStates, 4000)
  }

  function reviewStatusText(field) {
    const item = reviewState[field]
    if (!item) return ''
    // 已通过不展示状态文案
    if (item.status === 'APPROVED') return ''
    if (item.status === 'PENDING' || item.status === 'PROCESSING') return '审核中'
    if (item.status === 'REJECTED') return compactReviewReason(item.reason)
    if (item.status === 'FAILED') return compactReviewReason(item.reason || '审核暂时失败')
    return ''
  }

  function compactReviewReason(reason) {
    if (!reason) return '未通过'
    const normalized = String(reason)
      .replace(/\s+/g, ' ')
      .replace(/^(审核结果|审核不通过|违规原因|原因)\s*[:：]?\s*/u, '')
      .trim()
    const firstClause = normalized.split(/[。；;！!\n]/u)[0] || '未通过'
    return firstClause.length > 28 ? `${firstClause.slice(0, 28)}…` : firstClause
  }

  function reviewStatusClass(field) {
    const status = reviewState[field]?.status
    if (status === 'REJECTED' || status === 'FAILED') return 'is-rejected'
    return 'is-pending'
  }

  async function handleAvatarUpload(file) {
    const isImage = file.type.startsWith('image/')
    if (!isImage) {
      ElMessage.error('请上传图片文件')
      return false
    }

    const pre = validateLocalImageFile(file)
    if (!pre.ok) {
      ElMessage.warning(pre.message)
      return false
    }

    const formData = new FormData()
    formData.append('file', file)
    const loading = openImageUploadLoading(file, '正在上传头像…')
    try {
      const res = await uploadAvatar(formData)
      if (res.code === 0) {
        const url = res.data
        await updateAvatarUrl(url)
        userStore.patchUserProfile({ avatarUrl: url + '?t=' + Date.now() })
        ElMessage.success('头像已更新')
      }
    } catch (err) {
      ElMessage.error('上传失败')
    } finally {
      loading.close()
    }
    return false
  }

  return {
    DEFAULT_AVATAR,
    cancelEdit,
    editing,
    genderLabel,
    handleAvatarUpload,
    maskPhone,
    profileForm,
    reviewState,
    reviewStatusClass,
    reviewStatusText,
    saveSingleField,
    saveGender,
    saving,
    startEdit,
    userStore,
  }
}
