import { computed, ref, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { getEnterToSendEnabled, onEnterToSendChanged } from '@/utils/chatSendPreference'
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
  // 额度用尽只封输入与发送；查看记忆、压缩上下文这些不该跟着一起锁
  quotaExhausted: { type: Boolean, default: false },
  placeholder: { type: String, default: '说点什么…' },
  vip: { type: Boolean, default: false },
  generationHint: { type: String, default: '' },
  contextUsedTokens: { type: Number, default: 0 },
  contextMaxTokens: { type: Number, default: 128000 },
  contextCompressing: { type: Boolean, default: false },
  contextAvailable: { type: Boolean, default: false },
})

const emit = defineEmits([
  'update:modelValue',
  'update:imageQuality',
  'send',
  'compress-context',
  'open-memory',
])

const textareaRef = ref(null)
const charCount = computed(() => (props.modelValue || '').length)

const enterToSendEnabled = ref(getEnterToSendEnabled())
let offEnterToSend = null
onMounted(() => {
  offEnterToSend = onEnterToSendChanged((enabled) => {
    enterToSendEnabled.value = enabled
  })
})
onBeforeUnmount(() => {
  offEnterToSend?.()
})
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

// 「回车发送」是全站设置（设置页里能关），私信一直在读它，看板娘原来是硬编码的
function onEnterKey(event) {
  if (!enterToSendEnabled.value) return
  event.preventDefault()
  onEnter()
}

function onEnter() {
  if (!props.loading && props.modelValue.trim()) {
    emit('send')
  }
}

watch(() => props.modelValue, () => nextTick(resizeTextarea))
