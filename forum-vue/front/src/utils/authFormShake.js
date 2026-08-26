// 让当前校验失败的表单项左右抖动一次。 formInstance: Element Plus el form 组件实例 formRef.value
export function shakeAuthFormErrors(formInstance) {
  const root = formInstance?.$el
  if (!root || typeof root.querySelectorAll !== 'function') return

  const items = root.querySelectorAll('.el-form-item.is-error')
  if (!items.length) return

  items.forEach((item) => {
    item.classList.remove('auth-shake')
  })
  // 强制重排，确保连续失败时动画能重新播放
  void root.offsetWidth
  items.forEach((item) => {
    item.classList.add('auth-shake')
  })

  window.setTimeout(() => {
    items.forEach((item) => {
      item.classList.remove('auth-shake')
    })
  }, 480)
}
