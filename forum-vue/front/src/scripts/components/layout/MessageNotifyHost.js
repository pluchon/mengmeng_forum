import { watch } from 'vue'
import { ElNotification } from 'element-plus'
import { useMessageStore } from '@/stores/message'
import { useMessageCenterUiStore } from '@/stores/messageCenterUi'

const messageStore = useMessageStore()
const messageCenterUi = useMessageCenterUiStore()

let lastSeq = 0

watch(
  () => messageStore.incomingSignal?.seq,
  (seq) => {
    if (!seq || seq === lastSeq) return
    lastSeq = seq
    const sig = messageStore.incomingSignal
    const sender = (sig?.sender || '新私信').toString()
    const raw = (sig?.preview || '').toString().trim()
    const short = raw.length > 80 ? `${raw.slice(0, 80)}…` : raw
    ElNotification({
      title: sender,
      message: short || '您收到一条新私信',
      type: 'info',
      position: 'top-right',
      duration: 6000,
      onClick: () => messageCenterUi.open(),
    })
  },
)
