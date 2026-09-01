import { h } from 'vue'
import { ElNotification } from 'element-plus'
import { useMessageCenterUiStore } from '@/stores/messageCenterUi'

const PREVIEW_MAX = 80
let lastSeq = 0

// 右上角的消息到达提示。
//
// 私信：标题是对方昵称，正文是消息摘要。
// 群聊：标题是群名（带群头像），正文是「谁说了什么」——只看得到「谁说了什么」
// 却不知道在哪个群说的，是很别扭的。被 @ 时正文前面再挂一个显眼的标记。
export function showMessageIncomingToast(sig) {
  if (!sig?.seq || sig.seq === lastSeq) return
  lastSeq = sig.seq

  // 已在消息中心时，会话未读状态已经可见，不重复弹出右上角通知
  if (useMessageCenterUiStore().visible) return

  const sender = (sig.sender || '新私信').toString()
  const isGroup = sig.kind === 'group' && !!sig.groupName
  const title = isGroup ? String(sig.groupName) : sender

  const raw = (sig.preview || '').toString().trim()
  const body = isGroup ? `${sender}：${raw || '发来一条消息'}` : raw
  const short = body.length > PREVIEW_MAX ? `${body.slice(0, PREVIEW_MAX)}…` : body
  const text = short || '您收到一条新私信'

  // 被 @ 的标记单独着色，一眼看得出是在叫自己
  const message = sig.mentioned
    ? h('span', null, [
      h('span', { style: 'color:#d4537e;font-weight:800;margin-right:4px;' }, '[有人@你]'),
      text,
    ])
    : text

  const options = {
    title,
    message,
    type: 'info',
    position: 'top-right',
    duration: 6000,
    zIndex: 10001,
    showClose: true,
    onClick: () => {
      try {
        useMessageCenterUiStore().open()
      } catch {
        // 忽略
      }
    },
  }

  // 有群头像就用它替掉默认的信息图标；没有就退回默认样式
  if (isGroup && sig.groupAvatarUrl) {
    options.icon = () => h('img', {
      src: String(sig.groupAvatarUrl),
      alt: '',
      style: 'width:24px;height:24px;border-radius:6px;object-fit:cover;',
    })
  }

  ElNotification(options)
}
