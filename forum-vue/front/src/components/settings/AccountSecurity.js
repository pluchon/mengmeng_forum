import { EditPen } from '@element-plus/icons-vue'
import editPwdIconUrl from '@/assets/svg/修改.svg?url'
import InterestPreferenceDialog from '@/components/recommendation/InterestPreferenceDialog.vue'
import { useAccountSecurity } from '@scripts/components/settings/AccountSecurity'

const emit = defineEmits(['open-password'])

const {
  categoriesWithId,
  interestDialogVisible,
  interestDraftBoardIds,
  interestError,
  interestLoading,
  loadingLogs,
  loadingPersonalization,
  loginLogPage,
  loginLogPageSize,
  loginLogVisible,
  loginLogs,
  openInterestEditor,
  openLoginLogs,
  pagedLoginLogs,
  personalizedEnabled,
  saveInterestPreferences,
  savingPersonalization,
  togglePersonalization,
} = useAccountSecurity()
