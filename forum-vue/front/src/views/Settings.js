import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSettings } from '@scripts/views/Settings'
import BehaviorCaptchaDialog from '@/components/captcha/BehaviorCaptchaDialog.vue'

const captchaDialogRef = ref()
const router = useRouter()

function goBack() {
  if (window.history.length > 1) {
    router.back()
    return
  }
  router.push('/')
}

const {
  AccountSecurity,
  Avatar,
  ArrowLeft,
  BasicInfo,
  Close,
  ElMessage,
  Lock,
  MagicStick,
  mascotUi,
  Message,
  Operation,
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
  personalizedEnabled,
  interestBoardIds,
  interestBoardGroups,
  interestBoardOptions,
  interestBoardSummary,
  interestBoardDialogVisible,
  draftInterestBoardIds,
  interestSaving,
  preferenceLoading,
  openInterestBoardDialog,
  closeInterestBoardDialog,
  onDraftInterestBoardChange,
  confirmInterestBoards,
  saveInterestBoards,
  enterToSendEnabled,
  saveEnterToSendEnabled,
  pwdDialogVisible,
  pwdForm,
  pwdMethodSelected,
  pwdStepMethod,
  sendCode,
  sendPwdCode,
  pwdSubmitting,
  pwdStepTitle,
  bindSubmitting,
  hasBoundEmail,
  hasBoundPhone,
  saveRecommendationSetting,
  sendingEmailCode,
  sendingPhoneCode,
  submitBindEmail,
  submitBindPhone,
  submitPwd,
  userStore,
} = useSettings(captchaDialogRef)
