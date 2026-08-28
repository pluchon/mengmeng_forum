import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '@/api/auth'
import { ElMessage } from 'element-plus'
import { validateAuthForm } from '@/utils/authFormShake'
import { createAuthRules, digitsOnlyPhone } from '@/utils/authValidators'

export function useSignUp(captchaDialogRef) {
  const router = useRouter()
  const formRef = ref()
  const loading = ref(false)
  const agreed = ref(false)

  const regForm = ref({
    userName: '',
    nickname: '',
    password: '',
    phoneNum: '',
    email: '',
  })

  const base = createAuthRules()
  const rules = {
    userName: base.userName,
    nickname: base.nickname,
    password: base.password,
    phoneNum: base.phoneOptional,
    email: base.emailOptional,
  }

  const onPhoneNumInput = (val) => {
    regForm.value.phoneNum = digitsOnlyPhone(val)
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

  const handleSignUp = async () => {
    if (!agreed.value) return ElMessage.warning('请先同意用户协议')
    if (!formRef.value) return
    if (!await validateAuthForm(formRef.value)) return

    const ticket = await verifyCaptcha('REGISTER')
    if (!ticket) return
    loading.value = true
    try {
      const payload = {
        ...regForm.value,
        phoneNum: String(regForm.value.phoneNum || '').trim() || undefined,
        email: String(regForm.value.email || '').trim() || undefined,
        captchaTicket: ticket,
      }
      const res = await register(payload)
      if (res.code === 0) {
        ElMessage.success('账号创建成功，请登录')
        router.push('/sign-in?first=1')
      } else {
        ElMessage.error(res.message)
      }
    } finally {
      loading.value = false
    }
  }

  return {
    agreed,
    formRef,
    handleSignUp,
    loading,
    onPhoneNumInput,
    regForm,
    rules,
  }
}
