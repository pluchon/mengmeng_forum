import { computed, ref, onUnmounted } from 'vue'
import { apiErrorCode } from '@/utils/apiData'
import { useRouter } from 'vue-router'
import { sendSmsCodeForReset, sendMailCodeForReset, findPasswordByMail, findPasswordBySms } from '@/api/auth'
import { ElMessage } from 'element-plus'
import { validateAuthField, validateAuthForm } from '@/utils/authFormShake'
import {
  AUTH_MSG,
  createAuthRules,
  digitsOnlyPhone,
  isValidEmail,
  isValidPassword,
  isValidPhone,
  isValidMailCode,
  isValidSmsCode,
} from '@/utils/authValidators'

const CODE_SUCCESS_FLASH_MS = 650
const CODE_COUNTDOWN_SEC = 60

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export function useForgotPassword(captchaDialogRef) {
  const router = useRouter()

  const recoverFormRef = ref()
  const loading = ref(false)
  // idle | sending | success | countdown | expired
  const codePhase = ref('idle')
  const countdown = ref(0)
  let timer = null

  const form = ref({
    account: '',
    code: '',
    newPassword: '',
    confirmPassword: '',
    type: 'EMAIL',
  })

  // Element Plus 自带 show password 仅在有值时显示眼睛，空态像文本框；改为始终可切换
  const newPasswordVisible = ref(false)
  const confirmPasswordVisible = ref(false)

  const toggleNewPasswordVisible = () => {
    newPasswordVisible.value = !newPasswordVisible.value
  }

  const toggleConfirmPasswordVisible = () => {
    confirmPasswordVisible.value = !confirmPasswordVisible.value
  }

  const onAccountInput = (val) => {
    if (form.value.type === 'PHONE') {
      form.value.account = digitsOnlyPhone(val)
      return
    }
    form.value.account = String(val || '')
  }

  const base = createAuthRules()
  const rules = {
    account: [
      { required: true, message: '请输入账号', trigger: 'blur' },
      {
        validator: (_rule, value, callback) => {
          const text = String(value || '').trim()
          if (form.value.type === 'PHONE') {
            if (isValidPhone(text)) {
              callback()
              return
            }
            callback(new Error(AUTH_MSG.phone))
            return
          }
          if (isValidEmail(text)) {
            callback()
            return
          }
          callback(new Error(AUTH_MSG.email))
        },
        trigger: 'blur',
      },
    ],
    code: [
      { required: true, message: '请输入验证码', trigger: 'blur' },
      {
        validator: (_rule, value, callback) => {
          if (form.value.type === 'PHONE') {
            if (isValidSmsCode(value)) {
              callback()
              return
            }
            callback(new Error(AUTH_MSG.smsCode))
            return
          }
          if (isValidMailCode(value)) {
            callback()
            return
          }
          callback(new Error(AUTH_MSG.mailCode))
        },
        trigger: 'blur',
      },
    ],
    newPassword: base.password,
    confirmPassword: [
      { required: true, message: '请再次输入新密码', trigger: 'blur' },
      {
        validator: (_rule, value, callback) => {
          if (!isValidPassword(form.value.newPassword)) {
            callback(new Error(AUTH_MSG.password))
            return
          }
          if (String(value || '') !== String(form.value.newPassword || '')) {
            callback(new Error('两次输入的密码不一致'))
            return
          }
          callback()
        },
        trigger: 'blur',
      },
    ],
  }

  const codeLabel = computed(() => {
    if (codePhase.value === 'countdown') return String(countdown.value)
    if (codePhase.value === 'expired') return '验证码已过期，点击重发'
    if (codePhase.value === 'success') return ''
    return '获取验证码'
  })

  const codeBusy = computed(() =>
    codePhase.value === 'sending'
    || codePhase.value === 'success'
    || codePhase.value === 'countdown',
  )

  const clearTimer = () => {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  const startCountdown = () => {
    clearTimer()
    codePhase.value = 'countdown'
    countdown.value = CODE_COUNTDOWN_SEC
    timer = setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0) {
        clearTimer()
        countdown.value = 0
        codePhase.value = 'expired'
      }
    }, 1000)
  }

  const flashSuccessThenCountdown = async () => {
    codePhase.value = 'success'
    await sleep(CODE_SUCCESS_FLASH_MS)
    if (codePhase.value === 'success') {
      startCountdown()
    }
  }

  const switchRecoveryType = (type) => {
    if (form.value.type === type) return
    form.value.type = type
    recoverFormRef.value?.clearValidate(['account', 'code'])
  }

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

  // 1115 / 1119 被全局拦截器静默了（登录页自行接管），找回密码页必须自己说清楚，
  // 否则用户点完按钮页面毫无反应
  const notifyUnboundAccount = (err) => {
    if (apiErrorCode(err) === 1115) {
      ElMessage.info('该手机号还没有注册账号，换个方式找回或先去注册')
      return true
    }
    if (apiErrorCode(err) === 1119) {
      ElMessage.info('该邮箱还没有注册账号，换个方式找回或先去注册')
      return true
    }
    return false
  }

  const handleSendCode = async () => {
    if (codeBusy.value || !recoverFormRef.value) return
    if (!await validateAuthField(recoverFormRef.value, 'account')) return

    const account = form.value.account
    const isPhone = form.value.type === 'PHONE'

    codePhase.value = 'sending'
    try {
      const ticket = await verifyCaptcha('RESET_SEND')
      if (!ticket) {
        codePhase.value = 'idle'
        return
      }

      const res = isPhone
        ? await sendSmsCodeForReset(account, ticket)
        : await sendMailCodeForReset(account, ticket)
      if (res.code === 0) {
        form.value.code = ''
        await flashSuccessThenCountdown()
      } else {
        codePhase.value = 'idle'
      }
    } catch (err) {
      codePhase.value = 'idle'
      notifyUnboundAccount(err)
    }
  }

  const handleSubmit = async () => {
    if (!recoverFormRef.value) return
    if (!await validateAuthForm(recoverFormRef.value)) return
    const ticket = await verifyCaptcha('RESET_SUBMIT')
    if (!ticket) return
    loading.value = true
    try {
      const res =
        form.value.type === 'PHONE'
          ? await findPasswordBySms(form.value.account, form.value.code, form.value.newPassword, ticket)
          : await findPasswordByMail(form.value.account, form.value.code, form.value.newPassword, ticket)

      if (res.code === 0) {
        ElMessage.success('密码重置成功')
        router.push('/sign-in')
      }
    } catch (err) {
      notifyUnboundAccount(err)
    } finally {
      loading.value = false
    }
  }

  onUnmounted(() => {
    clearTimer()
  })

  return {
    codeBusy,
    codeLabel,
    codePhase,
    confirmPasswordVisible,
    countdown,
    form,
    handleSendCode,
    handleSubmit,
    loading,
    newPasswordVisible,
    onAccountInput,
    recoverFormRef,
    rules,
    switchRecoveryType,
    toggleConfirmPasswordVisible,
    toggleNewPasswordVisible,
  }
}
