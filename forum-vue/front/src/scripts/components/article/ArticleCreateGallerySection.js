import { Loading, Picture, Plus } from '@element-plus/icons-vue'
import { computed, onBeforeUnmount } from 'vue'

const props = defineProps({
  variant: { type: String, default: 'strip' },
  urls: { type: Array, default: () => [] },
  maxCount: { type: Number, default: 15 },
  stripOverflow: { type: Boolean, default: false },
  stripFadeLeft: { type: Boolean, default: false },
  canAdd: { type: Boolean, default: true },
  uploading: { type: Boolean, default: false },
  pendingCount: { type: Number, default: 0 },
  uploadLabel: { type: String, default: '上传中…' },
})

const emit = defineEmits(['open', 'remove', 'scroll', 'bind-ref'])

const displayCount = computed(() => props.urls.length + Math.max(0, props.pendingCount || 0))

function setItemsRef(el) {
  if (props.variant === 'strip') {
    emit('bind-ref', el)
  }
}

function onScroll() {
  emit('scroll')
}

onBeforeUnmount(() => {
  if (props.variant === 'strip') {
    emit('bind-ref', null)
  }
})
