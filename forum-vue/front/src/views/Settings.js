import { ref } from 'vue'
import { useSettings } from '@scripts/views/Settings'
import BehaviorCaptchaDialog from '@/components/captcha/BehaviorCaptchaDialog.vue'

const captchaDialogRef = ref()

const {
  AccountSecurity,
  ArrowLeft,
  BasicInfo,
  Cpu,
  ElMessage,
  Lock,
  MascotSettings,
  Message,
  Phone,
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
} = useSettings(captchaDialogRef)
