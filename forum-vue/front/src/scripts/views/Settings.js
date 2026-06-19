import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { User, Lock, Setting, Message, Phone, ArrowLeft } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { sendUpdatePwdCode, sendMailCode, sendSmsCode, updatePasswordByMail, updatePasswordBySms, verifyAndBindEmail, verifyAndBindPhone } from '@/api/settings'
import BasicInfo from '@/components/settings/BasicInfo.vue'
import AccountSecurity from '@/components/settings/AccountSecurity.vue'

export function useSettings(captchaDialogRef) {
  const userStore = useUserStore()
  const activeMenu = ref('profile')

  const pwdDialogVisible = ref(false)
  const emailDialogVisible = ref(false)
  const phoneDialogVisible = ref(false)

  const pwdMethodSelected = ref(false)
  const pwdStepMethod = ref('')
  const pwdForm = reactive({ code: '', newPassword: '' })
  let pwdSendTicket = ''

  const sendingEmailCode = ref(false)
  const sendingPhoneCode = ref(false)

  const emailCodeBtnDisabledPwd = ref(false)
  const emailCodeBtnTextPwd = ref('获取验证码')
  const phoneCodeBtnDisabledPwd = ref(false)
  const phoneCodeBtnTextPwd = ref('获取验证码')

  const emailForm = reactive({ email: '', code: '' })
  const phoneForm = reactive({ phoneNumber: '', code: '' })
  const emailCodeBtnDisabled = ref(false)
  const emailCodeBtnText = ref('获取验证码')
  const phoneCodeBtnDisabled = ref(false)
  const phoneCodeBtnText = ref('获取验证码')

  watch(pwdDialogVisible, (val) => {
    if (!val) {
      pwdMethodSelected.value = false
      pwdStepMethod.value = ''
      pwdForm.code = ''
      pwdForm.newPassword = ''
      pwdSendTicket = ''
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

    if (type === 'email' && contact === userStore.email) {
      return ElMessage.warning('新邮箱不能与当前绑定的邮箱相同')
    }
    if (type === 'sms' && contact === userStore.phoneNum) {
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
    } catch (err) {
      console.error(err)
    } finally {
      loadingRef.value = false
    }
  }

  async function sendPwdCode(type) {
    const contact = type === 'EMAIL' ? userStore.email : userStore.phoneNum
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

  async function submitPwd() {
    const submitTicket = await verifyCaptcha('RESET_SUBMIT')
    if (!submitTicket) return

    const contact = pwdStepMethod.value === 'email' ? userStore.email : userStore.phoneNum
    const res =
      pwdStepMethod.value === 'email'
        ? await updatePasswordByMail(contact, pwdForm.code, pwdForm.newPassword, submitTicket)
        : await updatePasswordBySms(contact, pwdForm.code, pwdForm.newPassword, submitTicket)

    if (res.code === 0) {
      ElMessage.success('密码修改成功')
      pwdDialogVisible.value = false
    }
  }

  async function submitBindEmail() {
    const res = await verifyAndBindEmail(emailForm.email, emailForm.code)
    if (res.code === 0) {
      ElMessage.success('邮箱绑定成功')
      userStore.patchUserProfile({ email: emailForm.email })
      emailDialogVisible.value = false
    }
  }

  async function submitBindPhone() {
    const res = await verifyAndBindPhone(phoneForm.phoneNumber, phoneForm.code)
    if (res.code === 0) {
      ElMessage.success('手机号绑定成功')
      userStore.patchUserProfile({ phoneNum: phoneForm.phoneNumber })
      phoneDialogVisible.value = false
    }
  }

  return {
    AccountSecurity,
    ArrowLeft,
    BasicInfo,
    ElMessage,
    Lock,
    Message,
    Phone,
    Setting,
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
    sendingEmailCode,
    sendingPhoneCode,
    submitBindEmail,
    submitBindPhone,
    submitPwd,
    userStore,
  }
}
