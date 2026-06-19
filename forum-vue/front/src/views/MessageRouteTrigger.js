import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { openMessageCenterFromRoute } from '@scripts/views/MessageView'

const route = useRoute()
const router = useRouter()

onMounted(() => {
  openMessageCenterFromRoute(router, route.query)
})
