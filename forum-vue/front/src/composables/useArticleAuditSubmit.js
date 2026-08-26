import { ElMessage } from 'element-plus'
import { confirmDialog } from '@/utils/appDialog'
import { submitForAudit } from '@/api/article'

// 发布确认框样式
export const auditSubmitMessageBoxOptions = {
  customClass: 'audit-submit-msgbox',
  confirmButtonClass: 'audit-submit-msgbox__confirm',
  cancelButtonClass: 'audit-submit-msgbox__cancel',
}

// 在任何封面或审核写请求发生前确认发布
export async function confirmArticlePublish() {
  try {
    await confirmDialog(
      '发布后将进入内容审核，审核结果可以在消息中心查看。',
      '是否确认发布',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info',
        showClose: false,
        closeOnClickModal: false,
        closeOnPressEscape: false,
        ...auditSubmitMessageBoxOptions,
      },
    )
    return true
  } catch {
    return false
  }
}

// 确认发布并提交异步审核，审核结果统一通过站内信通知
export async function submitArticleForAuditWithPrompt(articleId, options = {}) {
  if (!options.confirmed && !(await confirmArticlePublish())) {
    return { ok: false }
  }

  const res = await submitForAudit({ articleId: Number(articleId) })
  if (res.code !== 0) {
    ElMessage.error(res.message || '提交审核失败')
    return { ok: false, message: res.message }
  }
  ElMessage.success('审核结果可以在消息中心查看')
  return { ok: true, taskId: res.data }
}
