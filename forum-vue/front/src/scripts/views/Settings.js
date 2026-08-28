import { computed, onMounted, ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Avatar, User, Lock, MagicStick, Message, Phone, ArrowLeft, Close, Operation } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useBoardStore } from '@/stores/board'
import { changePasswordByCurrent, sendUpdatePwdCode, sendMailCode, sendSmsCode, updatePasswordByMail, updatePasswordBySms, verifyAndBindEmail, verifyAndBindPhone } from '@/api/settings'
import { AUTH_MSG, isValidEmail, isValidPassword, isValidPhone } from '@/utils/authValidators'
import { getRecommendationSetting, updateRecommendationSetting } from '@/api/recommendation'
import { notifyRecommendationSettingChanged } from '@/utils/recommendationSettingEvent'
import { getEnterToSendEnabled, setEnterToSendEnabled } from '@/utils/chatSendPreference'
import BasicInfo from '@/components/settings/BasicInfo.vue'
import AccountSecurity from '@/components/settings/AccountSecurity.vue'
import { useMascotUiStore } from '@/stores/mascotUi'

export function useSettings(captchaDialogRef) {
  const userStore = useUserStore()
  const boardStore = useBoardStore()
  const mascotUi = useMascotUiStore()
  const activeMenu = ref('profile')
  const personalizedEnabled = ref(true)
  const interestBoardIds = ref([])
  const preferenceLoading = ref(false)
  const interestSaving = ref(false)
  const interestBoardDialogVisible = ref(false)
  const draftInterestBoardIds = ref([])
  const enterToSendEnabled = ref(getEnterToSendEnabled())

  const interestBoardGroups = computed(() =>
    (boardStore.categoryList || [])
      .filter((item) => item.category?.id)
      .map((item) => ({
        categoryId: item.category.id,
        categoryName: item.category.name || '其他',
        boards: (item.boardList || [])
          .filter((board) => board?.id != null)
          .map((board) => ({
            value: Number(board.id),
            label: board.name || `版块${board.id}`,
          })),
      }))
      .filter((group) => group.boards.length > 0),
  )

  const interestBoardOptions = computed(() =>
    interestBoardGroups.value.flatMap((group) => group.boards),
  )

  const interestBoardSummary = computed(() => {
    const selected = interestBoardOptions.value.filter((item) =>
      interestBoardIds.value.includes(item.value),
    )
    if (!selected.length) return ''
    return selected.map((item) => item.label).join('、')
  })

  const pwdDialogVisible = ref(false)
  const emailDialogVisible = ref(false)
  const phoneDialogVisible = ref(false)

  const pwdMethodSelected = ref(false)
  const pwdStepMethod = ref('')
  const pwdForm = reactive({ code: '', newPassword: '', confirmPassword: '', currentPassword: '' })
  const pwdSubmitting = ref(false)
  const bindSubmitting = ref(false)
  let pwdSendTicket = ''

  // 首次绑定不要密码，改绑才要：新用户完善资料的门槛不该被抬高
  const hasBoundEmail = computed(() => Boolean(userStore.email))
  const hasBoundPhone = computed(() => Boolean(userStore.phoneNum))

  const pwdStepTitle = computed(() => {
    if (pwdStepMethod.value === 'email') return '邮箱验证'
    if (pwdStepMethod.value === 'phone') return '手机验证'
    return '验证当前密码'
  })

  const sendingEmailCode = ref(false)
  const sendingPhoneCode = ref(false)

  const emailCodeBtnDisabledPwd = ref(false)
  const emailCodeBtnTextPwd = ref('获取验证码')
  const phoneCodeBtnDisabledPwd = ref(false)
  const phoneCodeBtnTextPwd = ref('获取验证码')

  const emailForm = reactive({ email: '', code: '', currentPassword: '' })
  const phoneForm = reactive({ phoneNumber: '', code: '', currentPassword: '' })
  const emailCodeBtnDisabled = ref(false)
  const emailCodeBtnText = ref('获取验证码')
  const phoneCodeBtnDisabled = ref(false)
  const phoneCodeBtnText = ref('获取验证码')

  async function loadRecommendationSetting() {
    preferenceLoading.value = true
    try {
      const res = await getRecommendationSetting()
      if (res.code === 0) {
        personalizedEnabled.value = res.data?.personalizedEnabled !== false
        interestBoardIds.value = Array.isArray(res.data?.interestBoardIds)
          ? res.data.interestBoardIds.map((id) => Number(id)).filter((id) => id > 0).slice(0, 5)
          : []
      }
    } catch {
      ElMessage.error('加载个性化推荐设置失败')
    } finally {
      preferenceLoading.value = false
    }
  }

  async function saveRecommendationSetting(value) {
    const nextValue = Boolean(value)
    preferenceLoading.value = true
    try {
      const res = await updateRecommendationSetting(nextValue, interestBoardIds.value)
      if (res.code === 0) {
        ElMessage.success(nextValue ? '已按兴趣与互动推荐' : '已切换为热门与新鲜')
        notifyRecommendationSettingChanged({
          personalizedEnabled: nextValue,
          interestBoardIds: interestBoardIds.value,
        })
        return
      }
      await loadRecommendationSetting()
    } catch {
      ElMessage.error('保存个性化推荐设置失败')
      await loadRecommendationSetting()
    } finally {
      preferenceLoading.value = false
    }
  }

  async function saveInterestBoards(value) {
    const nextIds = Array.isArray(value)
      ? value.map((id) => Number(id)).filter((id) => id > 0).slice(0, 5)
      : []
    interestBoardIds.value = nextIds
    interestSaving.value = true
    try {
      const res = await updateRecommendationSetting(personalizedEnabled.value, nextIds)
      if (res.code === 0) {
        ElMessage.success('兴趣版块已更新')
        notifyRecommendationSettingChanged({
          personalizedEnabled: personalizedEnabled.value,
          interestBoardIds: nextIds,
        })
        return true
      }
      await loadRecommendationSetting()
      return false
    } catch {
      ElMessage.error('保存兴趣版块失败')
      await loadRecommendationSetting()
      return false
    } finally {
      interestSaving.value = false
    }
  }

  function openInterestBoardDialog() {
    if (!personalizedEnabled.value || preferenceLoading.value || interestSaving.value) return
    draftInterestBoardIds.value = [...interestBoardIds.value]
    interestBoardDialogVisible.value = true
  }

  function closeInterestBoardDialog() {
    if (interestSaving.value) return
    interestBoardDialogVisible.value = false
  }

  function onDraftInterestBoardChange(value) {
    const nextIds = Array.isArray(value)
      ? value.map((id) => Number(id)).filter((id) => id > 0)
      : []
    if (nextIds.length > 5) {
      ElMessage.warning('最多选择 5 个兴趣版块')
      draftInterestBoardIds.value = nextIds.slice(0, 5)
      return
    }
    draftInterestBoardIds.value = nextIds
  }

  async function confirmInterestBoards() {
    const nextIds = draftInterestBoardIds.value
      .map((id) => Number(id))
      .filter((id) => id > 0)
      .slice(0, 5)
    const ok = await saveInterestBoards(nextIds)
    if (ok) {
      interestBoardDialogVisible.value = false
    }
  }

  function saveEnterToSendEnabled(value) {
    const nextValue = Boolean(value)
    enterToSendEnabled.value = nextValue
    setEnterToSendEnabled(nextValue)
    ElMessage.success(nextValue ? '已开启回车发送' : '已关闭回车发送')
  }

  onMounted(async () => {
    if (!boardStore.categoryList?.length) {
      await boardStore.fetchCategoryList()
    }
    await loadRecommendationSetting()
  })

  watch(pwdDialogVisible, (val) => {
    if (!val) {
      pwdMethodSelected.value = false
      pwdStepMethod.value = ''
      pwdForm.code = ''
      pwdForm.newPassword = ''
      pwdForm.confirmPassword = ''
      pwdForm.currentPassword = ''
      pwdSendTicket = ''
    }
  })

  watch(emailDialogVisible, (val) => {
    if (!val) {
      emailForm.email = ''
      emailForm.code = ''
      emailForm.currentPassword = ''
    }
  })

  watch(phoneDialogVisible, (val) => {
    if (!val) {
      phoneForm.phoneNumber = ''
      phoneForm.code = ''
      phoneForm.currentPassword = ''
    }
  })

  async function verifyCaptcha(purpose) {
    const dlg = captchaDialogRef?.value
    if (!dlg?.run) {
      ElMessage.error('人机验证未就绪')
      return null
    }
    try {
      return await dlg.run(purpose)
    } catch {
      return null
    }
  }

  function maskContact(contact, type) {
    if (!contact) return ''
    if (type === 'phone') return contact.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
    if (type === 'email') {
      const [name, domain] = contact.split('@')
      return name.charAt(0) + '***@' + domain
    }
    return contact
  }

  function startTimer(textRef, disabledRef) {
    let count = 60
    disabledRef.value = true
    const timer = setInterval(() => {
      count--
      textRef.value = `${count}s后重发`
      if (count <= 0) {
        clearInterval(timer)
        textRef.value = '获取验证码'
        disabledRef.value = false
      }
    }, 1000)
  }

  async function sendCode(type) {
    const contact = type === 'email' ? emailForm.email : phoneForm.phoneNumber
    if (!contact) return ElMessage.warning('请输入联系方式')

    if (type === 'email' && !isValidEmail(contact)) {
      return ElMessage.warning(AUTH_MSG.email)
    }
    if (type !== 'email' && !isValidPhone(contact)) {
      return ElMessage.warning(AUTH_MSG.phone)
    }
    if (type === 'email' && contact === userStore.email) {
      return ElMessage.warning('新邮箱不能与当前绑定的邮箱相同')
    }
    if (type !== 'email' && contact === userStore.phoneNum) {
      return ElMessage.warning('新手机号不能与当前绑定的手机号相同')
    }

    const loadingRef = type === 'email' ? sendingEmailCode : sendingPhoneCode
    const textRef = type === 'email' ? emailCodeBtnText : phoneCodeBtnText
    const disabledRef = type === 'email' ? emailCodeBtnDisabled : phoneCodeBtnDisabled

    loadingRef.value = true
    try {
      if (type === 'email') {
        await sendMailCode(contact)
        emailForm.code = ''
      } else {
        await sendSmsCode(contact)
        phoneForm.code = ''
      }
      ElMessage.success('验证码已发送')
      startTimer(textRef, disabledRef)
    } finally {
      // 失败原因由响应拦截器统一提示，这里再兜一句会变成两条提示叠着弹
      loadingRef.value = false
    }
  }

  async function sendPwdCode(type) {
    // 手机路径不需要 contact，号码由后端从会话取
    const contact = type === 'EMAIL' ? userStore.email : null
    const loadingRef = type === 'EMAIL' ? sendingEmailCode : sendingPhoneCode
    const textRef = type === 'EMAIL' ? emailCodeBtnTextPwd : phoneCodeBtnTextPwd
    const disabledRef = type === 'EMAIL' ? emailCodeBtnDisabledPwd : phoneCodeBtnDisabledPwd

    const ticket = await verifyCaptcha('RESET_SEND')
    if (!ticket) return

    loadingRef.value = true
    try {
      const res = await sendUpdatePwdCode(contact, type, ticket)
      if (res.code === 0) {
        pwdSendTicket = ticket
        pwdForm.code = ''
        ElMessage.success('验证码已发送')
        startTimer(textRef, disabledRef)
      }
    } finally {
      loadingRef.value = false
    }
  }

  // 改密码成功后会立刻登出，所以必须先在前端把新密码校验干净，
  // 否则用户打错一个字符就会被踢出去且不知道新密码是什么
  function validateNewPassword() {
    if (!isValidPassword(pwdForm.newPassword)) {
      ElMessage.warning(AUTH_MSG.password)
      return false
    }
    if (pwdForm.newPassword !== pwdForm.confirmPassword) {
      ElMessage.warning('两次输入的密码不一致')
      return false
    }
    return true
  }

  async function submitPwd() {
    if (pwdSubmitting.value) return
    if (!validateNewPassword()) return

    // 凭当前密码修改：不发验证码，也不需要人机票据
    if (pwdStepMethod.value === 'password') {
      if (!pwdForm.currentPassword) return ElMessage.warning('请输入当前密码')
      pwdSubmitting.value = true
      try {
        const res = await changePasswordByCurrent(pwdForm.currentPassword, pwdForm.newPassword)
        if (res.code === 0) finishPasswordChange()
      } finally {
        pwdSubmitting.value = false
      }
      return
    }

    const isEmail = pwdStepMethod.value === 'email'
    const expectedLength = isEmail ? 6 : 4
    const codeText = String(pwdForm.code || '').trim()
    if (codeText.length !== expectedLength || !/^\d+$/.test(codeText)) {
      return ElMessage.warning(`验证码须为 ${expectedLength} 位数字`)
    }

    const submitTicket = await verifyCaptcha('RESET_SUBMIT')
    if (!submitTicket) return

    pwdSubmitting.value = true
    try {
      // 手机路径不传号码：掩码串不是号码，后端按会话取绑定的真实号码
      const res = isEmail
        ? await updatePasswordByMail(userStore.email, pwdForm.code, pwdForm.newPassword, submitTicket)
        : await updatePasswordBySms(pwdForm.code, pwdForm.newPassword, submitTicket)
      if (res.code === 0) finishPasswordChange()
    } finally {
      pwdSubmitting.value = false
    }
  }

  function finishPasswordChange() {
    ElMessage.success('密码修改成功，请重新登录')
    pwdDialogVisible.value = false
    userStore.logout()
  }

  async function submitBindEmail() {
    if (bindSubmitting.value) return
    if (!isValidEmail(emailForm.email)) return ElMessage.warning(AUTH_MSG.email)
    if (!/^\d{6}$/.test(String(emailForm.code || '').trim())) {
      return ElMessage.warning(AUTH_MSG.mailCode)
    }
    // 改绑等于把账号找回入口迁走，必须先证明是本人
    if (hasBoundEmail.value && !emailForm.currentPassword) {
      return ElMessage.warning('请输入当前密码')
    }
    bindSubmitting.value = true
    try {
      const res = await verifyAndBindEmail(emailForm.email, emailForm.code, emailForm.currentPassword)
      if (res.code === 0) {
        ElMessage.success('邮箱绑定成功')
        userStore.patchUserProfile({ email: emailForm.email })
        emailDialogVisible.value = false
      }
    } finally {
      bindSubmitting.value = false
    }
  }

  async function submitBindPhone() {
    if (bindSubmitting.value) return
    if (!isValidPhone(phoneForm.phoneNumber)) return ElMessage.warning(AUTH_MSG.phone)
    if (!/^\d{4}$/.test(String(phoneForm.code || '').trim())) {
      return ElMessage.warning(AUTH_MSG.smsCode)
    }
    if (hasBoundPhone.value && !phoneForm.currentPassword) {
      return ElMessage.warning('请输入当前密码')
    }
    bindSubmitting.value = true
    try {
      const res = await verifyAndBindPhone(phoneForm.phoneNumber, phoneForm.code, phoneForm.currentPassword)
      if (res.code === 0) {
        ElMessage.success('手机号绑定成功')
        userStore.patchUserProfile({ phoneNum: phoneForm.phoneNumber })
        phoneDialogVisible.value = false
      }
    } finally {
      bindSubmitting.value = false
    }
  }

  return {
    AccountSecurity,
    Avatar,
    ArrowLeft,
    BasicInfo,
    Close,
    ElMessage,
    Lock,
    MagicStick,
    mascotUi,
    Message,
    Operation,
    Phone,
    personalizedEnabled,
    interestBoardIds,
    interestBoardGroups,
    interestBoardOptions,
    interestBoardSummary,
    interestBoardDialogVisible,
    draftInterestBoardIds,
    interestSaving,
    preferenceLoading,
    openInterestBoardDialog,
    closeInterestBoardDialog,
    onDraftInterestBoardChange,
    confirmInterestBoards,
    saveInterestBoards,
    enterToSendEnabled,
    saveEnterToSendEnabled,
    User,
    activeMenu,
    emailCodeBtnDisabled,
    emailCodeBtnDisabledPwd,
    emailCodeBtnText,
    emailCodeBtnTextPwd,
    emailDialogVisible,
    emailForm,
    maskContact,
    phoneCodeBtnDisabled,
    phoneCodeBtnDisabledPwd,
    phoneCodeBtnText,
    phoneCodeBtnTextPwd,
    phoneDialogVisible,
    phoneForm,
    pwdDialogVisible,
    pwdForm,
    pwdMethodSelected,
    pwdStepMethod,
    sendCode,
    sendPwdCode,
    pwdSubmitting,
    pwdStepTitle,
    bindSubmitting,
    hasBoundEmail,
    hasBoundPhone,
    saveRecommendationSetting,
    sendingEmailCode,
    sendingPhoneCode,
    submitBindEmail,
    submitBindPhone,
    submitPwd,
    userStore,
  }
}
