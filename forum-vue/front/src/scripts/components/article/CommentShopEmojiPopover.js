import { ref } from 'vue'
import { getShopDetail } from '@/api/shop'
import { apiErrorCode } from '@/utils/apiData'

// 与后端 ResultCode.FAILED_SHOP_OFFLINE 对齐
const SHOP_OFFLINE_CODE = 1221

const props = defineProps({
  mediaUrl: { type: String, required: true },
  shopId: { type: [Number, String], default: null },
})

const emit = defineEmits(['open-shop'])
const visible = ref(false)
const loading = ref(false)
const detail = ref(null)
const errorText = ref('')
const offlineNotice = ref(false)

async function loadDetail() {
  if (detail.value || loading.value || !props.shopId) return
  loading.value = true
  errorText.value = ''
  offlineNotice.value = false
  try {
    const res = await getShopDetail(Number(props.shopId), {}, { silentBizCodes: [SHOP_OFFLINE_CODE] })
    if (res.code === 0 && res.data) {
      detail.value = res.data
    } else {
      errorText.value = res.message || '表情包信息加载失败'
    }
  } catch (error) {
    // 下架不是加载失败，也不是这个用户做错了什么，如实说明即可
    offlineNotice.value = apiErrorCode(error) === SHOP_OFFLINE_CODE
    errorText.value = offlineNotice.value ? '该表情包已下架' : '表情包信息加载失败'
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
