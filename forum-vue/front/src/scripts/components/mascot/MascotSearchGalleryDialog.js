import { computed } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  items: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:visible'])

const visibleModel = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value),
})
