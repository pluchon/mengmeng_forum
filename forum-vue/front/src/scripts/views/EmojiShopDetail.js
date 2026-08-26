import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { confirmDialog } from '@/utils/appDialog'
import { getShopDetail, purchaseShop, updateShopStatus } from '@/api/shop'
import { useUserStore } from '@/stores/user'
import { usePointsWalletStore } from '@/stores/pointsWallet'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const wallet = usePointsWalletStore()

const loading = ref(false)
const purchasing = ref(false)
const detail = ref(null)

const shopId = () => Number(route.params.id)

async function load() {
  const id = shopId()
  if (!Number.isFinite(id) || id <= 0) {
    ElMessage.warning('无效的商品')
    router.push('/emoji-shop')
    return
  }
  loading.value = true
  try {
    const res = await getShopDetail(id)
    if (res.code === 0 && res.data) {
      detail.value = res.data
    } else {
      detail.value = null
    }
  } finally {
    loading.value = false
  }
}

async function onPurchase() {
  const id = shopId()
  if (!detail.value || detail.value.owned) return
  try {
    await confirmDialog(
      detail.value.price === 0 ? '确认领取该表情包？' : `确认消耗 ${detail.value.price} 积分购买？`,
      '购买确认',
      { type: 'warning' },
    )
  } catch {
    return
  }
  purchasing.value = true
  try {
    const res = await purchaseShop(id)
    if (res.code === 0) {
      wallet.setBalance(res.data)
      detail.value.owned = true
      ElMessage.success('购买成功')
    }
  } finally {
    purchasing.value = false
  }
}

async function setShelf(status) {
  const id = shopId()
  try {
    const res = await updateShopStatus(id, status)
    if (res.code === 0) {
      ElMessage.success(status === 1 ? '已上架' : '已下架')
      await load()
    }
  } catch {
    // 拦截器已提示
  }
}

onMounted(async () => {
  if (userStore.isLoggedIn) await wallet.refresh()
  await load()
})

watch(
  () => route.params.id,
  () => load(),
)
