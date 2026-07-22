import { ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { sendSmsCodeForReset, sendMailCodeForReset, findPasswordByMail, findPasswordBySms } from '@/api/auth'
import { ElMessage } from 'element-plus'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const PHONE_RE = /^1[3-9]\d{9}$/

export function useForgotPassword(captchaDialogRef) {
  const router = useRouter()

  const recoverFormRef = ref()
  const loading = ref(false)
  const sendingCode = ref(false)
  const countdown = ref(0)
  let timer = null

  const form = ref({
    account: '',
    code: '',
    newPassword: '',
    type: 'EMAIL',
  })

  const rules = {
    account: [
      { required: true, message: '请输入账号', trigger: 'blur' },
      {
        validator: (_rule, value, callback) => {
          const pattern = form.value.type === 'PHONE' ? PHONE_RE : EMAIL_RE
          if (pattern.test(String(value || '').trim())) {
            callback()
            return
          }
          callback(new Error(form.value.type === 'PHONE' ? '手机号格式不正确' : '邮箱格式不正确'))
        },
        trigger: 'blur',
      },
    ],
    code: [
      { required: true, message: '请输入验证码', trigger: 'blur' },
      {
        validator: (_rule, value, callback) => {
          const expectedLength = form.value.type === 'PHONE' ? 4 : 6
          if (String(value || '').trim().length === expectedLength) {
            callback()
            return
          }
          callback(new Error(`验证码为 ${expectedLength} 位`))
        },
        trigger: 'blur',
      },
    ],
    newPassword: [
      { required: true, message: '请输入新密码', trigger: 'blur' },
      { min: 6, max: 12, message: '密码长度为 6 到 12 位', trigger: 'blur' },
    ],
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

  const handleSendCode = async () => {
    if (!recoverFormRef.value) return
    try {
      await recoverFormRef.value.validateField('account')
    } catch {
      return
    }

    const account = form.value.account
    const isPhone = form.value.type === 'PHONE'

    const ticket = await verifyCaptcha('RESET_SEND')
    if (!ticket) return

    sendingCode.value = true
    try {
      const res = isPhone
        ? await sendSmsCodeForReset(account, ticket)
        : await sendMailCodeForReset(account, ticket)
      if (res.code === 0) {
        form.value.code = ''
        ElMessage.success('验证码已发送')
        countdown.value = 60
        timer = setInterval(() => {
          countdown.value--
          if (countdown.value <= 0) clearInterval(timer)
        }, 1000)
      }
    } finally {
      sendingCode.value = false
    }
  }

  const handleSubmit = async () => {
    if (!recoverFormRef.value) return
    try {
      await recoverFormRef.value.validate()
    } catch {
      return
    }
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
    } finally {
      loading.value = false
    }
  }

  onUnmounted(() => {
    if (timer) clearInterval(timer)
  })

  return {
    countdown,
    form,
    handleSendCode,
    handleSubmit,
    loading,
    recoverFormRef,
    rules,
    sendingCode,
    switchRecoveryType,
  }
}
