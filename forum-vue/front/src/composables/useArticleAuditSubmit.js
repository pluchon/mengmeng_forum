import { ElMessage, ElMessageBox } from 'element-plus'
import { submitForAudit } from '@/api/article'
import { useUserStore } from '@/stores/user'

/** 提交审核确认框：灰色扁平主按钮 */
export const auditSubmitMessageBoxOptions = {
  customClass: 'audit-submit-msgbox',
  confirmButtonClass: 'audit-submit-msgbox__confirm',
  cancelButtonClass: 'audit-submit-msgbox__cancel',
}

/**
 * 提交异步审核：已绑定邮箱时可选择邮件通知，否则仅站内信确认。
 * @returns {{ ok: boolean, taskId?: string, message?: string }}
 */
export async function submitArticleForAuditWithPrompt(articleId) {
  const userStore = useUserStore()
  const hasBoundEmail = Boolean(String(userStore.email || '').trim())

  let notifyEmail = false
  try {
    if (hasBoundEmail) {
      await ElMessageBox.confirm(
        '审核结果将发送至站内信。是否同时向绑定邮箱发送邮件通知？',
        '提交审核',
        {
          confirmButtonText: '是，同时发邮件',
          cancelButtonText: '否，仅站内信',
          type: 'info',
          distinguishCancelAndClose: true,
          ...auditSubmitMessageBoxOptions,
        },
      )
      notifyEmail = true
    } else {
      await ElMessageBox.confirm(
        '审核结果将通过站内信通知您。',
        '提交审核',
        {
          confirmButtonText: '提交审核',
          cancelButtonText: '取消',
          type: 'info',
          distinguishCancelAndClose: true,
          ...auditSubmitMessageBoxOptions,
        },
      )
    }
  } catch (e) {
    if (e === 'close') return { ok: false }
    notifyEmail = false
  }

  const res = await submitForAudit({ articleId: Number(articleId), notifyEmail })
  if (res.code !== 0) {
    ElMessage.error(res.message || '提交审核失败')
    return { ok: false, message: res.message }
  }
  ElMessage.success('已提交审核，结果将通过站内信或邮件通知您')
  return { ok: true, taskId: res.data }
}
