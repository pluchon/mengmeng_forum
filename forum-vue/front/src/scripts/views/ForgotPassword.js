import { ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { sendSmsCodeForReset, sendMailCodeForReset, findPasswordByMail, findPasswordBySms } from '@/api/auth'
import { ElMessage } from 'element-plus'

export function useForgotPassword(captchaDialogRef) {
  const router = useRouter()

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
    const account = form.value.account
    if (!account) return ElMessage.warning('请输入账号')

    const isPhone = form.value.type === 'PHONE'
    if (isPhone && !/^1[3-9]\d{9}$/.test(account)) return ElMessage.warning('手机号格式不正确')
    if (!isPhone && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(account)) return ElMessage.warning('邮箱格式不正确')

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
    if (!form.value.account || !form.value.code || !form.value.newPassword) {
      return ElMessage.warning('请填写完整信息')
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
    ArrowLeft,
    countdown,
    form,
    handleSendCode,
    handleSubmit,
    loading,
    sendingCode,
  }
}
