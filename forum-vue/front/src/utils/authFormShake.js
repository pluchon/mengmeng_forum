import { nextTick } from 'vue'
import { ElMessage } from 'element-plus'

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

// 认证表单统一关掉了行内错误文案（show-message=false）以免撑开布局，
// 所以校验失败时要另外把原因说出来：抖动指出哪个框，顶部提示说明错在哪
export function firstAuthErrorMessage(invalidFields) {
  if (!invalidFields || typeof invalidFields !== 'object') return ''
  const groups = Array.isArray(invalidFields) ? invalidFields : Object.values(invalidFields)
  for (const group of groups) {
    const entry = Array.isArray(group) ? group[0] : group
    if (entry?.message) return entry.message
  }
  return ''
}

async function reportInvalid(formInstance, invalidFields) {
  await nextTick()
  shakeAuthFormErrors(formInstance)
  const message = firstAuthErrorMessage(invalidFields)
  if (message) ElMessage.warning(message)
}

// 校验整个表单，失败时抖动 + 顶部提示，返回是否通过
export async function validateAuthForm(formInstance) {
  if (!formInstance) return false
  try {
    await formInstance.validate()
    return true
  } catch (invalidFields) {
    await reportInvalid(formInstance, invalidFields)
    return false
  }
}

// 校验单个字段，用于"获取验证码"这类只依赖某一个输入框的动作
export async function validateAuthField(formInstance, prop) {
  if (!formInstance) return false
  try {
    await formInstance.validateField(prop)
    return true
  } catch (invalidFields) {
    await reportInvalid(formInstance, invalidFields)
    return false
  }
}
