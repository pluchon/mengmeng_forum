import { computed } from 'vue'

const props = defineProps({
  region: { type: String, default: '' },
})

const displayRegion = computed(() => {
  const value = props.region == null ? '' : String(props.region).trim()
  return value || ''
})
