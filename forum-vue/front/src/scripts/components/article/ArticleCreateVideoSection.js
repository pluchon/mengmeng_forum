import { Loading, VideoCamera } from '@element-plus/icons-vue'

defineProps({
  variant: { type: String, default: 'grid' },
  url: { type: String, default: '' },
  uploading: { type: Boolean, default: false },
  progress: { type: Number, default: 0 },
  uploadError: { type: String, default: '' },
})

const emit = defineEmits(['open', 'remove'])
