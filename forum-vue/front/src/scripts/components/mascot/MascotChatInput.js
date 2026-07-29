import { computed, ref, watch, nextTick } from 'vue'
import { Loading, Promotion, ScaleToOriginal } from '@element-plus/icons-vue'
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
  contextUsedTokens: { type: Number, default: 0 },
  contextMaxTokens: { type: Number, default: 128000 },
  contextCompressing: { type: Boolean, default: false },
  contextAvailable: { type: Boolean, default: false },
})

const emit = defineEmits([
  'update:modelValue',
  'update:imageQuality',
  'send',
  'toggle-points-pay',
  'compress-context',
])

const textareaRef = ref(null)
const charCount = computed(() => (props.modelValue || '').length)
const activeImageOption = computed(() =>
  props.imageOptions.find((item) => item.id === props.imageQuality)
  || findImageQualityOption(props.imageQuality)
  || props.imageOptions[0]
)
const contextPercent = computed(() => {
  if (!props.contextMaxTokens) return 0
  return Math.max(0, Math.min(100, (props.contextUsedTokens / props.contextMaxTokens) * 100))
})
const contextUsageLabel = computed(() => `${(props.contextUsedTokens / 1000).toFixed(1)}k/${Math.round(props.contextMaxTokens / 1000)}k`)

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
