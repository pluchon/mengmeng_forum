import { ElNotification } from 'element-plus'
import { useMessageCenterUiStore } from '@/stores/messageCenterUi'

const PREVIEW_MAX = 80
let lastSeq = 0

/** 右上角私信到达提示（发送者 + 摘要） */
export function showMessageIncomingToast(sig) {
  if (!sig?.seq || sig.seq === lastSeq) return
  lastSeq = sig.seq

  const sender = (sig.sender || '新私信').toString()
  const raw = (sig.preview || '').toString().trim()
  const short = raw.length > PREVIEW_MAX ? `${raw.slice(0, PREVIEW_MAX)}…` : raw

  ElNotification({
    title: sender,
    message: short || '您收到一条新私信',
    type: 'info',
    position: 'top-right',
    duration: 6000,
    zIndex: 10001,
    showClose: true,
    onClick: () => {
      try {
        useMessageCenterUiStore().open()
      } catch {
        /* ignore */
      }
    },
  })
}
