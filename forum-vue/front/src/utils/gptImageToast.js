import { ElMessage } from 'element-plus'

let activeToast = null

/** premium / GPT 生图开始时展示，结束或关闭时调用 dismissGptImageSlowToast */
export function showGptImageSlowToast() {
  dismissGptImageSlowToast()
  activeToast = ElMessage.info({
    message: 'GPT生图较慢，请耐心等待~',
    duration: 0,
    showClose: true,
    offset: 56,
  })
}

export function dismissGptImageSlowToast() {
  if (activeToast?.close) {
    activeToast.close()
  }
  activeToast = null
}
