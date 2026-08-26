<template>
  <div class="emoji-shop-page animate-fade-in" v-loading="loading">
    <el-page-header @back="$router.push('/emoji-shop')">
      <template #content>
        <span class="page-title" style="font-size: 1.25rem; font-weight: 700">{{ detail?.name || '商品详情' }}</span>
      </template>
    </el-page-header>

    <template v-if="detail">
      <el-row :gutter="24" style="margin-top: 20px">
        <el-col :xs="24" :md="10">
          <el-card shadow="never" body-style="padding: 0">
            <img :src="detail.coverUrl" alt="" class="emoji-shop-card-cover" style="border-radius: 12px" />
          </el-card>
        </el-col>
        <el-col :xs="24" :md="14">
          <el-space direction="vertical" alignment="stretch" :size="12" style="width: 100%">
            <el-text size="large" strong>{{ detail.name }}</el-text>
            <div>
              <el-text type="danger" size="large">{{ detail.price === 0 ? '免费领取' : `${detail.price} 积分` }}</el-text>
              <el-text type="info" style="margin-left: 12px">已售 {{ detail.salesCount ?? 0 }}</el-text>
            </div>
            <el-text v-if="detail.uploadUserNickname" type="info">上传者：{{ detail.uploadUserNickname }}</el-text>
            <el-tag v-if="detail.status === 2" type="warning">已下架</el-tag>
            <el-tag v-else-if="detail.status === 1" type="success">上架中</el-tag>
            <el-space wrap>
              <el-button
                v-if="userStore.isLoggedIn && !detail.owned && detail.status === 1"
                type="primary"
                size="large"
                round
                :disabled="detail.price > 0 && wallet.balance < detail.price"
                :loading="purchasing"
                @click="onPurchase"
              >
                {{ detail.price === 0 ? '免费领取' : '立即购买' }}
              </el-button>
              <el-button v-if="userStore.isLoggedIn && detail.owned" type="success" round disabled>已拥有</el-button>
              <el-button v-if="!userStore.isLoggedIn" type="primary" round @click="$router.push('/sign-in')">
                登录后购买
              </el-button>
              <template v-if="Number(userStore.isAdmin) === 1 && detail.status">
                <el-button v-if="detail.status === 1" round @click="setShelf(2)">下架</el-button>
                <el-button v-else round @click="setShelf(1)">上架</el-button>
              </template>
            </el-space>
            <el-text v-if="detail.price > 0 && userStore.isLoggedIn" type="info" size="small">
              当前积分：{{ wallet.balance }}
            </el-text>
          </el-space>
        </el-col>
      </el-row>

      <el-divider content-position="left">包内表情</el-divider>
      <div class="emoji-shop-detail-grid">
        <img
          v-for="(url, idx) in detail.imageUrls || []"
          :key="idx"
          :src="url"
          alt=""
          class="emoji-shop-detail-thumb"
        />
      </div>
    </template>
  </div>
</template>

<script setup>
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
</script>

<style scoped src="@/assets/styles/emoji-shop.css"></style>
