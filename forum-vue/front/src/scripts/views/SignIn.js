import { computed, ref, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { login as apiLogin, smsLogin, mailLogin } from '@/api/auth'
import { ElMessage } from 'element-plus'
import { shakeAuthFormErrors } from '@/utils/authFormShake'
import {
  AUTH_MSG,
  containsDangerousInput,
  createAuthRules,
  digitsOnlyPhone,
} from '@/utils/authValidators'

const CODE_SUCCESS_FLASH_MS = 650
const CODE_COUNTDOWN_SEC = 60

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export function useSignIn(captchaDialogRef) {
  const router = useRouter()
  const phoneFormRef = ref()
  const userNameFormRef = ref()
  const emailCodeFormRef = ref()
  const emailPasswordFormRef = ref()

  const loginTab = ref('phone')
  const loading = ref(false)
  const agreed = ref(false)

  // idle | sending | success | countdown | expired
  const smsCodePhase = ref('idle')
  const mailCodePhase = ref('idle')
  const countdown = ref(0)
  const mailCountdown = ref(0)
  let smsTimer = null
  let mailTimer = null
  let smsFlashTimer = null
  let mailFlashTimer = null

  const loginForm = ref({
    userName: '',
    password: '',
    phoneNum: '',
    code: '',
    email: '',
    emailPassword: '',
    emailCode: '',
  })

  const base = createAuthRules()
  const rules = {
    phoneNum: base.phoneRequired,
    code: base.smsCode,
    userName: [
      { required: true, message: '请输入用户名', trigger: 'blur' },
      {
        validator: (_rule, value, callback) => {
          const text = String(value || '').trim()
          if (text.includes('@')) {
            callback(new Error('邮箱登录请切换到邮箱登录方式'))
            return
          }
          if (containsDangerousInput(text)) {
            callback(new Error(AUTH_MSG.dangerous))
            return
          }
          // 登录兼容历史用户名；新注册已禁止特殊符号
          if (!text || text.length < 4 || text.length > 20) {
            callback(new Error(AUTH_MSG.userName))
            return
          }
          callback()
        },
        trigger: 'blur',
      },
    ],
    password: base.password,
    email: base.emailRequired,
    emailPassword: base.password,
    emailCode: base.mailCode,
  }

  const smsCodeLabel = computed(() => {
    if (smsCodePhase.value === 'countdown') return String(countdown.value)
    if (smsCodePhase.value === 'expired') return '验证码已过期，点击重发'
    if (smsCodePhase.value === 'success') return ''
    return '获取验证码'
  })

  const mailCodeLabel = computed(() => {
    if (mailCodePhase.value === 'countdown') return String(mailCountdown.value)
    if (mailCodePhase.value === 'expired') return '验证码已过期，点击重发'
    if (mailCodePhase.value === 'success') return ''
    return '获取验证码'
  })

  const smsCodeBusy = computed(() =>
    smsCodePhase.value === 'sending'
    || smsCodePhase.value === 'success'
    || smsCodePhase.value === 'countdown',
  )

  const mailCodeBusy = computed(() =>
    mailCodePhase.value === 'sending'
    || mailCodePhase.value === 'success'
    || mailCodePhase.value === 'countdown',
  )

  async function verifyCaptcha(purpose) {
    const dialog = captchaDialogRef?.value
    if (!dialog?.run) {
      ElMessage.error('人机验证未就绪')
      return null
    }
    try {
      return await dialog.run(purpose)
    } catch {
      return null
    }
  }

  const clearSmsTimer = () => {
    if (smsTimer) {
      clearInterval(smsTimer)
      smsTimer = null
    }
    if (smsFlashTimer) {
      clearTimeout(smsFlashTimer)
      smsFlashTimer = null
    }
  }

  const clearMailTimer = () => {
    if (mailTimer) {
      clearInterval(mailTimer)
      mailTimer = null
    }
    if (mailFlashTimer) {
      clearTimeout(mailFlashTimer)
      mailFlashTimer = null
    }
  }

  const startSmsTimer = () => {
    clearSmsTimer()
    smsCodePhase.value = 'countdown'
    countdown.value = CODE_COUNTDOWN_SEC
    smsTimer = setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0) {
        clearInterval(smsTimer)
        smsTimer = null
        countdown.value = 0
        smsCodePhase.value = 'expired'
      }
    }, 1000)
  }

  const startMailTimer = () => {
    clearMailTimer()
    mailCodePhase.value = 'countdown'
    mailCountdown.value = CODE_COUNTDOWN_SEC
    mailTimer = setInterval(() => {
      mailCountdown.value -= 1
      if (mailCountdown.value <= 0) {
        clearInterval(mailTimer)
        mailTimer = null
        mailCountdown.value = 0
        mailCodePhase.value = 'expired'
      }
    }, 1000)
  }

  const flashSmsSuccessThenCountdown = async () => {
    smsCodePhase.value = 'success'
    await sleep(CODE_SUCCESS_FLASH_MS)
    if (smsCodePhase.value === 'success') {
      startSmsTimer()
    }
  }

  const flashMailSuccessThenCountdown = async () => {
    mailCodePhase.value = 'success'
    await sleep(CODE_SUCCESS_FLASH_MS)
    if (mailCodePhase.value === 'success') {
      startMailTimer()
    }
  }

  const handleSendCode = async () => {
    if (smsCodeBusy.value) return

    try {
      await phoneFormRef.value.validateField('phoneNum')
    } catch {
      await nextTick()
      shakeAuthFormErrors(phoneFormRef.value)
      return
    }

    smsCodePhase.value = 'sending'
    try {
      const ticket = await verifyCaptcha('SMS_SEND')
      if (!ticket) {
        smsCodePhase.value = 'idle'
        return
      }

      const res = await smsLogin(loginForm.value.phoneNum, undefined, ticket)
      if (res.code === 0) {
        loginForm.value.code = ''
        await flashSmsSuccessThenCountdown()
      } else {
        smsCodePhase.value = 'idle'
      }
    } catch {
      smsCodePhase.value = 'idle'
    }
  }

  const handleSendMailCode = async () => {
    if (mailCodeBusy.value) return

    try {
      await emailCodeFormRef.value.validateField('email')
    } catch {
      await nextTick()
      shakeAuthFormErrors(emailCodeFormRef.value)
      return
    }

    mailCodePhase.value = 'sending'
    try {
      const ticket = await verifyCaptcha('MAIL_SEND')
      if (!ticket) {
        mailCodePhase.value = 'idle'
        return
      }

      const res = await mailLogin(loginForm.value.email, undefined, ticket)
      if (res.code === 0) {
        loginForm.value.emailCode = ''
        await flashMailSuccessThenCountdown()
      } else {
        mailCodePhase.value = 'idle'
      }
    } catch {
      mailCodePhase.value = 'idle'
    }
  }

  const afterLoginSuccess = () => {
    ElMessage.success('欢迎回来')
    router.push('/')
  }

  const onPhoneNumInput = (val) => {
    loginForm.value.phoneNum = digitsOnlyPhone(val)
  }

  const handleLogin = async () => {
    if (!agreed.value) return ElMessage.warning('请先同意用户协议')

    const tab = loginTab.value
    let formRef = null
    if (tab === 'phone') formRef = phoneFormRef.value
    else if (tab === 'userName') formRef = userNameFormRef.value
    else if (tab === 'emailCode') formRef = emailCodeFormRef.value
    else if (tab === 'emailPassword') formRef = emailPasswordFormRef.value

    if (!formRef) return

    try {
      await formRef.validate()
    } catch {
      await nextTick()
      shakeAuthFormErrors(formRef)
      return
    }

    loading.value = true
    try {
      if (tab === 'phone') {
        const ticket = await verifyCaptcha('SMS_LOGIN')
        if (!ticket) return
        const res = await smsLogin(loginForm.value.phoneNum, loginForm.value.code, ticket)
        if (res.code !== 0) {
          if (res.code === 1115) {
            ElMessage.info('该手机号未绑定账号')
            router.push('/sign-up')
          } else {
            ElMessage.error(res.message || '登录失败')
          }
          return
        }
      } else if (tab === 'userName') {
        const ticket = await verifyCaptcha('USER_LOGIN')
        if (!ticket) return
        await apiLogin(
          {
            userName: loginForm.value.userName,
            password: loginForm.value.password,
          },
          ticket,
        )
      } else if (tab === 'emailCode') {
        const ticket = await verifyCaptcha('MAIL_LOGIN')
        if (!ticket) return
        const res = await mailLogin(loginForm.value.email, loginForm.value.emailCode.trim(), ticket)
        if (res.code !== 0) {
          if (res.code === 1119) {
            ElMessage.info('该邮箱未绑定账号')
            router.push('/sign-up')
          } else {
            ElMessage.error(res.message || '登录失败')
          }
          return
        }
      } else if (tab === 'emailPassword') {
        const ticket = await verifyCaptcha('USER_LOGIN')
        if (!ticket) return
        await apiLogin(
          {
            userName: loginForm.value.email,
            password: loginForm.value.emailPassword.trim(),
          },
          ticket,
        )
      }

      afterLoginSuccess()
    } catch (err) {
      if (err && err.code === 1115) {
        ElMessage.info('该手机号未绑定账号')
        router.push('/sign-up')
      }
    } finally {
      loading.value = false
    }
  }

  onUnmounted(() => {
    clearSmsTimer()
    clearMailTimer()
  })

  return {
    agreed,
    countdown,
    emailCodeFormRef,
    emailPasswordFormRef,
    handleLogin,
    handleSendCode,
    handleSendMailCode,
    loading,
    loginForm,
    loginTab,
    mailCodeBusy,
    mailCodeLabel,
    mailCodePhase,
    mailCountdown,
    onPhoneNumInput,
    phoneFormRef,
    rules,
    smsCodeBusy,
    smsCodeLabel,
    smsCodePhase,
    userNameFormRef,
  }
}
