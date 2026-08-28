defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '请确认' },
  message: { type: String, default: '' },
  confirmText: { type: String, default: '确认' },
  cancelText: { type: String, default: '取消' },
  danger: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  width: { type: String, default: 'min(360px, 92vw)' },
  zIndex: { type: Number, default: 7000 },
  // 默认必须点按钮才能关，删除 / 解散这类不可逆操作依赖这个约束；
  // 只有"要不要去登录"这种随时可以放弃的场景才打开这两项
  closeOnClickModal: { type: Boolean, default: false },
  showClose: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'confirm', 'cancel'])

function onConfirm() {
  emit('confirm')
  emit('update:modelValue', false)
}

function onCancel() {
  emit('cancel')
  emit('update:modelValue', false)
}
