defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '提示' },
  message: { type: String, default: '' },
  confirmText: { type: String, default: '我知道了' },
  tone: { type: String, default: 'success' }, // success | warn | danger
  loading: { type: Boolean, default: false },
  width: { type: String, default: 'min(360px, 92vw)' },
  zIndex: { type: Number, default: 7000 },
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
