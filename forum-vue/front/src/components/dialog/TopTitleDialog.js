defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' },
  hint: { type: String, default: '' },
  confirmText: { type: String, default: '确定' },
  cancelText: { type: String, default: '取消' },
  showCancel: { type: Boolean, default: true },
  showFooter: { type: Boolean, default: true },
  showClose: { type: Boolean, default: false },
  confirmDisabled: { type: Boolean, default: false },
  danger: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  width: { type: String, default: 'min(400px, 92vw)' },
  zIndex: { type: Number, default: 6500 },
})

const emit = defineEmits(['update:modelValue', 'confirm', 'cancel'])

function onConfirm() {
  emit('confirm')
}

function onCancel() {
  emit('cancel')
  emit('update:modelValue', false)
}
