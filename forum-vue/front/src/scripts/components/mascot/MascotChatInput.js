import { computed, ref, watch, nextTick } from 'vue'
import { Loading, Promotion } from '@element-plus/icons-vue'
import { findImageQualityOption, findTextLlmOption } from '@/constants/aiModels'

const props = defineProps({
  modelValue: { type: String, default: '' },
  llm: { type: String, default: 'qwen-flash' },
  imageQuality: { type: String, default: 'normal' },
  options: { type: Array, default: () => [] },
  imageOptions: { type: Array, default: () => [] },
  mode: { type: String, default: 'chat' },
  loading: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '说点什么…' },
  vip: { type: Boolean, default: false },
  showModelPicker: { type: Boolean, default: true },
  estimatePoints: { type: Number, default: null },
  estimateLoading: { type: Boolean, default: false },
  estimateHint: { type: String, default: '' },
  showPointsPayButton: { type: Boolean, default: false },
  pointsPayActive: { type: Boolean, default: false },
})

const emit = defineEmits([
  'update:modelValue',
  'update:llm',
  'update:imageQuality',
  'send',
  'clear',
  'toggle-points-pay',
])

const textareaRef = ref(null)
const charCount = computed(() => (props.modelValue || '').length)
const activeTextOption = computed(() => findTextLlmOption(props.llm) || props.options.find(o => o.id === props.llm))
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
