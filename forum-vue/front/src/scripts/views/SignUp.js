import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '@/api/auth'
import { ElMessage } from 'element-plus'

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

  const rules = {
    userName: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
    nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
    password: [
      { required: true, message: '请输入密码', trigger: 'blur' },
      { min: 6, max: 12, message: '长度在 6 到 12 个字符', trigger: 'blur' },
    ],
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

    await formRef.value.validate(async (valid) => {
      if (!valid) return
      const ticket = await verifyCaptcha('REGISTER')
      if (!ticket) return
      loading.value = true
      try {
        const res = await register({ ...regForm.value, captchaTicket: ticket })
        if (res.code === 0) {
          ElMessage.success('账号创建成功，请登录')
          router.push('/sign-in?first=1')
        } else {
          ElMessage.error(res.message)
        }
      } finally {
        loading.value = false
      }
    })
  }

  return {
    agreed,
    formRef,
    handleSignUp,
    loading,
    regForm,
    rules,
  }
}
