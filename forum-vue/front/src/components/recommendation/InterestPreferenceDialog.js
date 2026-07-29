import { computed } from 'vue'

const props = defineProps({
  visible: Boolean,
  boardIds: {
    type: Array,
    default: () => [],
  },
  categories: {
    type: Array,
    default: () => [],
  },
  loading: Boolean,
  saving: Boolean,
  error: {
    type: String,
    default: '',
  },
  maximumSelection: {
    type: Number,
    default: 8,
  },
})

const emit = defineEmits(['update:visible', 'update:boardIds', 'retry', 'save'])

const selectedBoardIds = computed({
  get: () => props.boardIds,
  set: value => emit('update:boardIds', value),
})

const selectedCount = computed(() => selectedBoardIds.value.length)

const categoryColumns = computed(() => [
  {
    key: 'left',
    items: props.categories.filter((_, index) => index % 2 === 0),
  },
  {
    key: 'right',
    items: props.categories.filter((_, index) => index % 2 === 1),
  },
])

function categoryIcon(item) {
  const name = String(item?.category?.name || '')
  if (name.includes('二次元')) return '✦'
  if (name.includes('游戏')) return '🎮'
  if (name.includes('数码') || name.includes('科技')) return '⌘'
  if (name.includes('学习') || name.includes('职场')) return '✎'
  if (name.includes('生活')) return '☼'
  if (name.includes('影视') || name.includes('音乐')) return '🎬'
  if (name.includes('情感')) return '♡'
  if (name.includes('萌宠')) return '♧'
  return '◌'
}

function isBoardSelectionDisabled(boardId) {
  if (props.saving) return true
  return !selectedBoardIds.value.includes(Number(boardId))
    && selectedCount.value >= props.maximumSelection
}
