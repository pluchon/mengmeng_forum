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

const categoryIcons = ['✦', '⌘', '☼', '◌', '✎', '♡', '⌁', '♧']

function categoryIcon(item, index) {
  const name = String(item?.category?.name || '')
  if (name.includes('游戏')) return '🎮'
  if (name.includes('数码') || name.includes('科技')) return '⌘'
  if (name.includes('学习') || name.includes('职场')) return '✎'
  if (name.includes('生活')) return '☼'
  if (name.includes('影视') || name.includes('音乐')) return '◌'
  if (name.includes('萌宠')) return '♧'
  return categoryIcons[index % categoryIcons.length]
}

function groupDescription(item) {
  const description = String(item?.category?.description || '').trim()
  if (description) return description
  const boardNames = (item?.boardList || []).map(board => board?.name).filter(Boolean)
  if (boardNames.length === 0) return '等待内容加入'
  return boardNames.slice(0, 3).join(' · ')
}

function isBoardSelectionDisabled(boardId) {
  if (props.saving) return true
  return !selectedBoardIds.value.includes(Number(boardId))
    && selectedCount.value >= props.maximumSelection
}
