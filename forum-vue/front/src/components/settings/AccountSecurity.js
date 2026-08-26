import { CircleCheck, Clock, Key, RefreshRight } from '@element-plus/icons-vue'
import AppPagination from '@/components/common/AppPagination.vue'
import { useAccountSecurity } from '@scripts/components/settings/AccountSecurity'

const emit = defineEmits(['open-password'])

const {
  loadingLogs,
  loadSecurityAssessment,
  loginLogPage,
  loginLogPageSize,
  loginLogVisible,
  loginLogs,
  openLoginLogs,
  pagedLoginLogs,
  reassessSecurity,
  securityAssessment,
  securityAssessmentLoading,
  securityReassessing,
} = useAccountSecurity()
