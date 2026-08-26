import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import TopTitleDialog from '@/components/dialog/TopTitleDialog.vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  title: { type: String, default: '举报内容' },
  submitting: { type: Boolean, default: false },
})

const emit = defineEmits(['update:visible', 'submit'])

const reasonOptions = [
  { value: 'ABUSE', label: '辱骂攻击他人' },
  { value: 'SEXUAL', label: '色情低俗内容' },
  { value: 'ILLEGAL', label: '违法违规内容' },
  { value: 'SPAM', label: '垃圾广告引流' },
  { value: 'MISLEADING', label: '虚假误导信息' },
  { value: 'OTHER', label: '其他理由' },
]

const reasonRows = computed(() => {
  const rows = []
  for (let i = 0; i < reasonOptions.length; i += 2) {
    rows.push(reasonOptions.slice(i, i + 2))
  }
  return rows
})

const selectedReason = ref('')
const customReason = ref('')

const selectedOption = computed(() =>
  reasonOptions.find((option) => option.value === selectedReason.value),
)

const finalReason = computed(() => {
  if (selectedReason.value === 'OTHER') return customReason.value.trim()
  return selectedOption.value?.label || ''
})

const canSubmit = computed(() => {
  if (props.submitting || !selectedReason.value) return false
  const reason = finalReason.value
  return reason.length >= 5 && reason.length <= 200
})

watch(
  () => props.visible,
  (visible) => {
    if (!visible) return
    selectedReason.value = ''
    customReason.value = ''
  },
)

function onVisible(value) {
  emit('update:visible', value)
}

function closeDialog() {
  if (props.submitting) return
  emit('update:visible', false)
}

function submitReason() {
  if (!canSubmit.value) {
    ElMessage.warning('请选择举报理由，其他理由需填写至少 5 个字')
    return
  }
  emit('submit', finalReason.value)
}
