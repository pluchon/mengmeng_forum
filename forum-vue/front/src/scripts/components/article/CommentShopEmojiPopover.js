import { ref } from 'vue'
import { getShopDetail } from '@/api/shop'

const props = defineProps({
  mediaUrl: { type: String, required: true },
  shopId: { type: [Number, String], default: null },
})

const emit = defineEmits(['open-shop'])
const visible = ref(false)
const loading = ref(false)
const detail = ref(null)
const errorText = ref('')

async function loadDetail() {
  if (detail.value || loading.value || !props.shopId) return
  loading.value = true
  errorText.value = ''
  try {
    const res = await getShopDetail(Number(props.shopId))
    if (res.code === 0 && res.data) {
      detail.value = res.data
    } else {
      errorText.value = res.message || '表情包信息加载失败'
    }
  } catch {
    errorText.value = '表情包信息加载失败'
  } finally {
    loading.value = false
  }
}

function togglePreview() {
  visible.value = !visible.value
  if (visible.value) loadDetail()
}

function openShop() {
  if (!props.shopId) return
  visible.value = false
  emit('open-shop', Number(props.shopId))
}
