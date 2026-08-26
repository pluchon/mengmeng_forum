import { ref } from 'vue'
import updateIllustration from '@/assets/images/update.png'

const visible = ref(false)

function open() {
  visible.value = true
}

function close() {
  visible.value = false
}

defineExpose({ open, close })
