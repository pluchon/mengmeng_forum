import { computed } from 'vue'

const props = defineProps({
  fromFollowing: { type: Boolean, default: false },
})

const show = computed(() => !!props.fromFollowing)
