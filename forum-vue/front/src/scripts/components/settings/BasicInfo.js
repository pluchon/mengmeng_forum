import { reactive, ref, onMounted, watch } from 'vue'
import { Camera } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { updateUserInfo, uploadAvatar, updateAvatarUrl } from '@/api/settings'
import { validateText } from '@/api/article'
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

  onMounted(() => {
    syncFromStore()
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

    if (field === 'nickname' && !profileForm.nickname.trim()) {
      return ElMessage.warning('昵称不能为空')
    }

    if (field === 'remark' && profileForm.remark?.trim()) {
      try {
        const aiRes = await validateText(profileForm.remark)
        if (aiRes.code === 0 && aiRes.data && !aiRes.data.isAllowed) {
          return ElMessage.warning(
            '内容违规: ' + (aiRes.data.reason || '签名内容不符合平台规范'),
          )
        }
      } catch (err) {
        console.warn('AI 检测异常:', err)
      }
    }

    saving.value = true
    try {
      const backendField = field === 'nickname' ? 'nickName' : field
      const res = await updateUserInfo({ [backendField]: profileForm[field] })
      if (res.code === 0) {
        ElMessage.success('更新成功')
        originalValues[field] = profileForm[field]
        editing[field] = false
        if (field === 'nickname') userStore.patchUserProfile({ nickname: profileForm.nickname })
        if (field === 'remark') userStore.patchUserProfile({ remark: profileForm.remark })
        if (field === 'gender') userStore.patchUserProfile({ gender: profileForm.gender })
      }
    } finally {
      saving.value = false
    }
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
    Camera,
    DEFAULT_AVATAR,
    cancelEdit,
    editing,
    genderLabel,
    handleAvatarUpload,
    maskPhone,
    profileForm,
    saveSingleField,
    saveGender,
    saving,
    startEdit,
    userStore,
  }
}
