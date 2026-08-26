import { computed, ref } from 'vue'

const props = defineProps({
  content: {
    type: String,
    default: '',
  },
  maxLength: {
    type: Number,
    default: 50,
  },
  renderHtml: {
    type: Function,
    default: null,
  },
})

const expanded = ref(false)

function stripPlainText(raw) {
  return String(raw || '').replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim()
}

const plainText = computed(() => stripPlainText(props.content))

const needsCollapse = computed(() => plainText.value.length > props.maxLength)

const collapsedText = computed(() => {
  if (!needsCollapse.value) return plainText.value
  return `${plainText.value.slice(0, props.maxLength)}…`
})

function toggleExpanded() {
  expanded.value = !expanded.value
}
