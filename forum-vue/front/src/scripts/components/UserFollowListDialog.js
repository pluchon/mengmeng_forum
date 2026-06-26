import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import { useUserStore } from '@/stores/user'
import { followUser, unfollowUser, getFollowingList, getFollowerList } from '@/api/userFollow'
import { DEFAULT_AVATAR } from '@/utils/constants'
import { unwrapPageRecords } from '@/utils/apiData'

const PAGE_SIZE = 10
const REMARK_MAX = 56

function remarkSummary(text) {
  const s = (text || '').trim()
  if (!s) return '还没有填写个人简介'
  return s.length > REMARK_MAX ? `${s.slice(0, REMARK_MAX)}…` : s
}

function useUserFollowListDialog() {
  const router = useRouter()
  const userStore = useUserStore()

  const visible = ref(false)
  const mode = ref('following')
  const profileUserId = ref(null)
  const keyword = ref('')
  const loading = ref(false)
  const items = ref([])
  const pageNum = ref(1)
  const total = ref(0)
  const actionSavingId = ref(null)

  let searchTimer = null

  const dialogTitle = computed(() => (mode.value === 'following' ? '关注列表' : '粉丝列表'))

  const isProfileOwner = computed(() => {
    const pid = Number(profileUserId.value)
    const me = Number(userStore.id)
    return pid > 0 && me > 0 && pid === me
  })

  function openList(type, userId) {
    const uid = Number(userId)
    if (!uid) return
    mode.value = type === 'followers' ? 'followers' : 'following'
    profileUserId.value = uid
    keyword.value = ''
    pageNum.value = 1
    items.value = []
    total.value = 0
    visible.value = true
    fetchPage(1)
  }

  function closeList() {
    visible.value = false
  }

  async function fetchPage(page = pageNum.value) {
    const uid = Number(profileUserId.value)
    if (!uid) return
    pageNum.value = page
    loading.value = true
    try {
      const params = {
        userId: uid,
        pageNum: pageNum.value,
        pageSize: PAGE_SIZE,
      }
      const kw = keyword.value.trim()
      if (kw) params.keyword = kw
      const res = mode.value === 'following'
        ? await getFollowingList(params)
        : await getFollowerList(params)
      if (res.code === 0) {
        items.value = unwrapPageRecords(res.data).map((row) => ({
          ...row,
          isFollowing: !!row.isFollowing,
        }))
        total.value = Number(res.data?.total) || 0
      }
    } catch {
      items.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  function onKeywordInput() {
    if (searchTimer) clearTimeout(searchTimer)
    searchTimer = setTimeout(() => fetchPage(1), 320)
  }

  function clearKeyword() {
    keyword.value = ''
    fetchPage(1)
  }

  watch(visible, (v) => {
    if (!v && searchTimer) {
      clearTimeout(searchTimer)
      searchTimer = null
    }
  })

  function buttonLabel(item) {
    if (mode.value === 'following') {
      return item.isFollowing ? '已关注' : '关注'
    }
    if (isProfileOwner.value && item.isFollowing) {
      return '互相关注'
    }
    return item.isFollowing ? '已关注' : '回关'
  }

  function buttonType(item) {
    const followed = !!item.isFollowing
    if (mode.value === 'followers' && isProfileOwner.value && followed) {
      return 'default'
    }
    return followed ? 'default' : 'danger'
  }

  function showActionButton(item) {
    if (!userStore.isLoggedIn) return false
    const uid = Number(item?.user?.id)
    const me = Number(userStore.id)
    return uid > 0 && me > 0 && uid !== me
  }

  async function toggleRowFollow(item) {
    if (!userStore.isLoggedIn) {
      ElMessage.warning('请先登录')
      return
    }
    const uid = Number(item?.user?.id)
    if (!uid || actionSavingId.value) return
    actionSavingId.value = uid
    try {
      const res = item.isFollowing
        ? await unfollowUser(uid)
        : await followUser(uid)
      if (res.code === 0) {
        item.isFollowing = !item.isFollowing
      } else {
        ElMessage.error(res.message || '操作失败')
      }
    } catch {
      ElMessage.error('操作异常')
    } finally {
      actionSavingId.value = null
    }
  }

  function goProfile(item) {
    const uid = Number(item?.user?.id)
    if (!uid) return
    visible.value = false
    router.push(`/profile/${uid}`)
  }

  return {
    PAGE_SIZE,
    visible,
    mode,
    dialogTitle,
    keyword,
    loading,
    items,
    pageNum,
    total,
    actionSavingId,
    openList,
    closeList,
    fetchPage,
    onKeywordInput,
    clearKeyword,
    buttonLabel,
    buttonType,
    showActionButton,
    toggleRowFollow,
    goProfile,
    remarkSummary,
  }
}

const defaultAvatar = DEFAULT_AVATAR

const {
  visible,
  dialogTitle,
  keyword,
  loading,
  items,
  pageNum,
  total,
  actionSavingId,
  closeList,
  fetchPage,
  onKeywordInput,
  clearKeyword,
  buttonLabel,
  buttonType,
  showActionButton,
  toggleRowFollow,
  goProfile,
  openList,
} = useUserFollowListDialog()

defineExpose({ openList })
