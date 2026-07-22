import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CaretBottom, CaretTop, ChatDotRound } from '@element-plus/icons-vue'
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import IpRegionLabel from '@/components/common/IpRegionLabel.vue'
import LikeCountIcon from '@/components/common/LikeCountIcon.vue'
import CommentReplyMediaDisplay from '@/components/article/CommentReplyMediaDisplay.vue'
import { getSubReplyList, likeSubReply, unlikeSubReply } from '@/api/reply'
import { DEFAULT_AVATAR } from '@/utils/constants'
import { unwrapPageRecords } from '@/utils/apiData'
import { ensureLoggedIn } from '@/utils/loginPrompt'
import '@/assets/styles/article.css'

const props = defineProps({
  replyId: { type: [Number, String], required: true },
  articleId: { type: [Number, String], required: true },
  readOnly: { type: Boolean, default: true },
  refreshToken: { type: Number, default: 0 },
  subReplyCount: { type: Number, default: 0 },
})

const emit = defineEmits(['reply', 'open-shop'])
const router = useRouter()
const defaultAvatar = DEFAULT_AVATAR
const expanded = ref(false)
const subList = ref([])
const page = ref(1)
const pageSize = ref(5)
const total = ref(0)

function goProfile(userId) {
  if (userId == null || userId === '') return
  router.push(`/profile/${userId}`)
}

function toggle() {
  expanded.value = !expanded.value
}

async function loadSubs(p = 1) {
  page.value = p
  const res = await getSubReplyList({ replyId: props.replyId, pageNum: p, pageSize: pageSize.value })
  if (res.code === 0) {
    const raw = res.data
    subList.value = unwrapPageRecords(raw).map((row) => ({
      ...row,
      liked: !!row.liked,
    }))
    total.value = raw?.total != null ? Number(raw.total) : subList.value.length
  }
}

function emitReply(sub) {
  emit('reply', {
    replyId: props.replyId,
    replyUserId: sub?.postUser?.id || null,
    nickname: sub?.postUser?.nickname || sub?.replyUserNickname || '用户',
    content: sub?.subReply?.content || '',
  })
}

function shouldShowReplyMention(sub) {
  return Boolean(sub?.replyUserNickname)
}

async function toggleSubLike(sub) {
  if (!(await ensureLoggedIn('点赞需要登录'))) return
  const subReplyId = sub?.subReply?.id
  if (!subReplyId) return
  try {
    const res = sub.liked ? await unlikeSubReply(subReplyId) : await likeSubReply(subReplyId)
    if (res.code === 0) {
      sub.liked = !sub.liked
      const base = Number(sub.subReply.likeCount) || 0
      sub.subReply.likeCount = Math.max(0, base + (sub.liked ? 1 : -1))
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch {
    ElMessage.error('点赞请求异常')
  }
}

watch(
  () => [props.replyId, props.articleId],
  async ([replyId, articleId]) => {
    if (replyId == null || replyId === '' || articleId == null || articleId === '') return
    expanded.value = false
    page.value = 1
    subList.value = []
    total.value = 0
    await loadSubs(1)
  },
  { immediate: true },
)

watch(
  () => props.refreshToken,
  async (token, prev) => {
    if (!token || token === prev) return
    expanded.value = true
    await loadSubs(1)
  },
)

watch(
  () => props.subReplyCount,
  async (count, prev) => {
    if (count === prev || count <= 0) return
    expanded.value = true
    await loadSubs(1)
  },
)
