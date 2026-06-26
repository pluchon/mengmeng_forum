import { ref, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  Key,
  CirclePlus,
  UserFilled,
  ArrowLeft,
  Lock,
  Message as MailIcon,
} from '@element-plus/icons-vue'
import { login as apiLogin, smsLogin, mailLogin } from '@/api/auth'
import { ElMessage } from 'element-plus'
import AnnouncementBoard from '@/components/common/AnnouncementBoard.vue'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function useSignIn(captchaDialogRef) {
  const route = useRoute()
  const router = useRouter()
  const announcementRef = ref()
  const phoneFormRef = ref()
  const userNameFormRef = ref()
  const emailFormRef = ref()

  const loginTab = ref('phone')
  /** 邮箱页：null 时仅展示方式选择；'password' | 'code' 时展示对应表单 */
  const emailSubTab = ref(null)

  watch(loginTab, (v) => {
    if (v !== 'email') emailSubTab.value = null
  })

  watch(emailSubTab, (v) => {
    if (v === 'password') loginForm.value.emailCode = ''
    if (v === 'code') loginForm.value.emailPassword = ''
  })
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
        validator: (_r, v, cb) => {
          if (v && String(v).includes('@')) {
            cb(new Error('邮箱登录请切换到「邮箱登录」页签'))
          } else {
            cb()
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
    emailPassword: [],
    emailCode: [],
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
    if (emailSubTab.value !== 'code') {
      ElMessage.warning('请先选择「验证码登录」')
      return
    }
    try {
      await emailFormRef.value.validateField('email')
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
    else formRef = emailFormRef.value

    if (!formRef) return

    if (tab === 'email') {
      if (!emailSubTab.value) {
        ElMessage.warning('请先选择「密码登录」或「验证码登录」')
        return
      }
      try {
        await formRef.validateField('email')
      } catch {
        return
      }
      if (emailSubTab.value === 'password') {
        const pwd = String(loginForm.value.emailPassword || '').trim()
        if (pwd.length < 6) {
          ElMessage.warning('密码不能少于 6 位')
          return
        }
      } else {
        const code = String(loginForm.value.emailCode || '').trim()
        if (code.length !== 6) {
          ElMessage.warning('请输入 6 位邮箱验证码')
          return
        }
      }
    } else {
      try {
        await formRef.validate()
      } catch {
        return
      }
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
      } else {
        const email = loginForm.value.email
        const code = String(loginForm.value.emailCode || '').trim()
        const pwd = String(loginForm.value.emailPassword || '').trim()
        if (emailSubTab.value === 'code') {
          const ticket = await verifyCaptcha('MAIL_LOGIN')
          if (!ticket) return
          const res = await mailLogin(email, code, ticket)
          if (res.code !== 0) {
            if (res.code === 1119) {
              ElMessage.info('该邮箱未绑定账号')
              router.push('/sign-up')
            } else {
              ElMessage.error(res.message || '登录失败')
            }
            return
          }
        } else {
          const ticket = await verifyCaptcha('USER_LOGIN')
          if (!ticket) return
          await apiLogin(
            {
              userName: email,
              password: pwd,
            },
            ticket,
          )
        }
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
    ArrowLeft,
    Key,
    Lock,
    MailIcon,
    CirclePlus,
    UserFilled,
    userNameFormRef,
    emailFormRef,
    agreed,
    announcementRef,
    countdown,
    mailCountdown,
    handleLogin,
    handleSendCode,
    handleSendMailCode,
    loading,
    loginForm,
    loginTab,
    emailSubTab,
    phoneFormRef,
    rules,
    sendingCode,
    sendingMailCode,
  }
}
