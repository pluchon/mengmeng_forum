import { computed, ref, watch, nextTick } from 'vue'
import { Loading, Promotion } from '@element-plus/icons-vue'
import { findImageQualityOption } from '@/constants/aiModels'

const props = defineProps({
  modelValue: { type: String, default: '' },
  llm: { type: String, default: 'qwen-flash' },
  imageQuality: { type: String, default: 'normal' },
  imageOptions: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  imageGenerating: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '说点什么…' },
  vip: { type: Boolean, default: false },
  generationHint: { type: String, default: '' },
  showPointsPayButton: { type: Boolean, default: false },
  pointsPayActive: { type: Boolean, default: false },
})

const emit = defineEmits([
  'update:modelValue',
  'update:imageQuality',
  'send',
  'toggle-points-pay',
])

const textareaRef = ref(null)
const charCount = computed(() => (props.modelValue || '').length)
const activeImageOption = computed(() => findImageQualityOption(props.imageQuality) || props.imageOptions[0])

function resizeTextarea() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 120)}px`
}

function onInput(e) {
  emit('update:modelValue', e.target.value)
  nextTick(resizeTextarea)
}

function onEnter() {
  if (!props.loading && props.modelValue.trim()) {
    emit('send')
  }
}

watch(() => props.modelValue, () => nextTick(resizeTextarea))
