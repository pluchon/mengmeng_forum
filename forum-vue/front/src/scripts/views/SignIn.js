import { ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { login as apiLogin, smsLogin, mailLogin } from '@/api/auth'
import { ElMessage } from 'element-plus'
import AnnouncementBoard from '@/components/common/AnnouncementBoard.vue'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function useSignIn(captchaDialogRef) {
  const router = useRouter()
  const announcementRef = ref()
  const phoneFormRef = ref()
  const userNameFormRef = ref()
  const emailCodeFormRef = ref()
  const emailPasswordFormRef = ref()

  const loginTab = ref('phone')
  const loading = ref(false)
  const sendingCode = ref(false)
  const sendingMailCode = ref(false)
  const agreed = ref(false)
  const countdown = ref(0)
  const mailCountdown = ref(0)
  let smsTimer = null
  let mailTimer = null

  const loginForm = ref({
    userName: '',
    password: '',
    phoneNum: '',
    code: '',
    email: '',
    emailPassword: '',
    emailCode: '',
  })

  const rules = {
    phoneNum: [
      { required: true, message: '请输入手机号', trigger: 'blur' },
      { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
    ],
    code: [
      { required: true, message: '请输入验证码', trigger: 'blur' },
      { len: 4, message: '验证码为 4 位', trigger: 'blur' },
    ],
    userName: [
      { required: true, message: '请输入用户名', trigger: 'blur' },
      {
        validator: (_rule, value, callback) => {
          if (value && String(value).includes('@')) {
            callback(new Error('邮箱登录请切换到邮箱登录方式'))
          } else {
            callback()
          }
        },
        trigger: 'blur',
      },
    ],
    password: [
      { required: true, message: '请输入密码', trigger: 'blur' },
      { min: 6, message: '密码不能少于 6 位', trigger: 'blur' },
    ],
    email: [
      { required: true, message: '请输入邮箱', trigger: 'blur' },
      { pattern: EMAIL_RE, message: '邮箱格式不正确', trigger: 'blur' },
    ],
    emailPassword: [
      { required: true, message: '请输入密码', trigger: 'blur' },
      { min: 6, message: '密码不能少于 6 位', trigger: 'blur' },
    ],
    emailCode: [
      { required: true, message: '请输入验证码', trigger: 'blur' },
      { len: 6, message: '验证码为 6 位', trigger: 'blur' },
    ],
  }

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

  const startSmsTimer = () => {
    countdown.value = 60
    if (smsTimer) clearInterval(smsTimer)
    smsTimer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) {
        clearInterval(smsTimer)
        smsTimer = null
      }
    }, 1000)
  }

  const startMailTimer = () => {
    mailCountdown.value = 60
    if (mailTimer) clearInterval(mailTimer)
    mailTimer = setInterval(() => {
      mailCountdown.value--
      if (mailCountdown.value <= 0) {
        clearInterval(mailTimer)
        mailTimer = null
      }
    }, 1000)
  }

  const handleSendCode = async () => {
    try {
      await phoneFormRef.value.validateField('phoneNum')
    } catch {
      return
    }

    sendingCode.value = true
    try {
      const ticket = await verifyCaptcha('SMS_SEND')
      if (!ticket) return

      const res = await smsLogin(loginForm.value.phoneNum, undefined, ticket)
      if (res.code === 0) {
        loginForm.value.code = ''
        ElMessage.success(res.message || '验证码已发送')
        startSmsTimer()
      }
    } catch {
      /* 错误已由 axios 响应拦截器提示 */
    } finally {
      sendingCode.value = false
    }
  }

  const handleSendMailCode = async () => {
    try {
      await emailCodeFormRef.value.validateField('email')
    } catch {
      return
    }

    sendingMailCode.value = true
    try {
      const ticket = await verifyCaptcha('MAIL_SEND')
      if (!ticket) return

      const res = await mailLogin(loginForm.value.email, undefined, ticket)
      if (res.code === 0) {
        loginForm.value.emailCode = ''
        ElMessage.success(res.message || '验证码已发送')
        startMailTimer()
      }
    } catch {
      /* 错误已由 axios 响应拦截器提示 */
    } finally {
      sendingMailCode.value = false
    }
  }

  const afterLoginSuccess = () => {
    ElMessage.success('欢迎回来')
    router.push('/')
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
    if (smsTimer) clearInterval(smsTimer)
    if (mailTimer) clearInterval(mailTimer)
  })

  return {
    AnnouncementBoard,
    agreed,
    announcementRef,
    countdown,
    emailCodeFormRef,
    emailPasswordFormRef,
    handleLogin,
    handleSendCode,
    handleSendMailCode,
    loading,
    loginForm,
    loginTab,
    mailCountdown,
    phoneFormRef,
    rules,
    sendingCode,
    sendingMailCode,
    userNameFormRef,
  }
}
