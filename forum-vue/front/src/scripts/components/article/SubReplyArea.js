import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { CaretBottom, CaretTop, ChatDotRound, Flag } from '@element-plus/icons-vue'
import LikeCountIcon from '@/components/common/LikeCountIcon.vue'
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import AppPagination from '@/components/common/AppPagination.vue'
import CommentReplyMediaDisplay from '@/components/article/CommentReplyMediaDisplay.vue'
import CommentExpandableText from '@/components/article/CommentExpandableText.vue'
import { getSubReplyList, likeSubReply, unlikeSubReply } from '@/api/reply'
import { unwrapPageRecords } from '@/utils/apiData'
import { ensureLoggedIn } from '@/utils/loginPrompt'
import { useUserStore } from '@/stores/user'
import { DEFAULT_AVATAR } from '@/utils/constants'
import { formatCommentTimeShanghai } from '@/utils/datetime'
import '@/assets/styles/article.css'

const props = defineProps({
  replyId: { type: [Number, String], required: true },
  articleId: { type: [Number, String], required: true },
  authorId: { type: [Number, String], default: null },
  readOnly: { type: Boolean, default: true },
  refreshToken: { type: Number, default: 0 },
  subReplyCount: { type: Number, default: 0 },
  canAccept: { type: Boolean, default: false },
  acceptSaving: { type: Boolean, default: false },
})

const emit = defineEmits(['reply', 'open-profile', 'open-shop', 'report', 'accept'])
const userStore = useUserStore()
const defaultAvatar = DEFAULT_AVATAR
const expanded = ref(false)
const subList = ref([])
const page = ref(1)
const pageSize = ref(5)
const total = ref(0)

function emitProfile(userId) {
  const uid = String(userId ?? '').trim()
  if (!/^\d+$/.test(uid) || /^0+$/.test(uid)) return
  emit('open-profile', uid)
}

function toggle() {
  expanded.value = !expanded.value
}

function resolveIpRegion(sub) {
  return String(
    sub?.subReply?.ipRegion
    || sub?.ipRegion
    || sub?.subReply?.ip_region
    || '',
  ).trim()
}

function formatCommentMeta(createTime, ipRegion) {
  const time = formatCommentTimeShanghai(createTime)
  const region = String(ipRegion || '').trim()
  if (time && region) return `${time} · ${region}`
  return time || region || ''
}

function compactNickname(value) {
  const chars = Array.from(String(value || '用户'))
  return chars.length > 4 ? `${chars.slice(0, 4).join('')}...` : chars.join('')
}

function isAuthorReply(sub) {
  const uid = sub?.postUser?.id
  if (uid == null || props.authorId == null || props.authorId === '') return false
  return Number(uid) === Number(props.authorId)
}

function isViolated(sub) {
  return !!(sub?.violated || sub?.subReply?.violated)
}

function isOwnSub(sub) {
  const uid = sub?.postUser?.id
  const me = userStore.userInfo?.id
  if (uid == null || me == null) return false
  return Number(uid) === Number(me)
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
    content: isViolated(sub) ? '' : (sub?.subReply?.content || ''),
  })
}

function emitReport(sub) {
  if (isOwnSub(sub) || isAuthorReply(sub)) return
  emit('report', {
    subReplyId: sub?.subReply?.id,
    replyId: props.replyId,
    articleId: props.articleId,
  })
}

function emitAccept(sub) {
  if (!props.canAccept || props.acceptSaving || sub?.accepted || isAuthorReply(sub)) return
  emit('accept', sub)
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
