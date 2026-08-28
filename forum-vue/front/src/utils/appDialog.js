import { createApp, h, nextTick, ref } from 'vue'
import ElementPlus from 'element-plus'
import FactConfirmDialog from '@/components/dialog/FactConfirmDialog.vue'
import IconConfirmDialog from '@/components/dialog/IconConfirmDialog.vue'
import '@/components/dialog/dialog-tokens.scss'

function mountDialog(Component, props) {
  return new Promise((resolve, reject) => {
    const container = document.createElement('div')
    document.body.appendChild(container)
    const visible = ref(true)
    let settled = false

    const app = createApp({
      setup() {
        function cleanup(result, ok) {
          if (settled) return
          settled = true
          visible.value = false
          nextTick(() => {
            app.unmount()
            container.remove()
            if (ok) resolve(result)
            else reject(result)
          })
        }

        return () =>
          h(Component, {
            ...props,
            modelValue: visible.value,
            'onUpdate:modelValue': (value) => {
              visible.value = value
              if (!value && !settled) cleanup('cancel', false)
            },
            onConfirm: () => cleanup(true, true),
            onCancel: () => cleanup('cancel', false),
          })
      },
    })

    app.use(ElementPlus)
    app.mount(container)
  })
}

/** 单按钮事实确认（对齐 fact-confirm-dialog.html） */
export function factConfirm({
  title = '提示',
  message = '',
  confirmText = '我知道了',
  tone = 'success',
} = {}) {
  return mountDialog(FactConfirmDialog, { title, message, confirmText, tone }).catch(() => false)
}

/** 双按钮居中图标确认（对齐 icon-confirm-dialog.html） */
export function iconConfirm({
  title = '请确认',
  message = '',
  confirmText = '确认',
  cancelText = '取消',
  danger = false,
  closeOnClickModal = false,
  showClose = false,
} = {}) {
  return mountDialog(IconConfirmDialog, {
    title,
    message,
    confirmText,
    cancelText,
    danger,
    closeOnClickModal,
    showClose,
  })
}

/** 兼容旧 ElMessageBox.confirm(message, title, options) 调用形态 */
export async function confirmDialog(message, title = '提示', options = {}) {
  const danger =
    options.type === 'warning' ||
    options.type === 'error' ||
    /删除|解散|移除|清空|认输/.test(`${title}${message}${options.confirmButtonText || ''}`)
  try {
    await iconConfirm({
      title: title || '提示',
      message: message || '',
      confirmText: options.confirmButtonText || '确定',
      cancelText: options.cancelButtonText || '取消',
      danger,
    })
    return true
  } catch {
    const err = new Error('cancel')
    err === 'cancel'
    throw 'cancel'
  }
}
