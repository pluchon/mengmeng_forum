import { computed, ref, watch } from 'vue'
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
// 折叠态的条数直接用父级传下来的 subReplyCount，展开后才以实际拉到的为准
const total = computed(() => (loaded.value ? loadedTotal.value : Number(props.subReplyCount) || 0))
const loadedTotal = ref(0)
const loaded = ref(false)
const subLoading = ref(false)

function emitProfile(userId) {
  const uid = String(userId ?? '').trim()
  if (!/^\d+$/.test(uid) || /^0+$/.test(uid)) return
  emit('open-profile', uid)
}

async function toggle() {
  expanded.value = !expanded.value
  // 首次展开才真正拉数据：挂载即拉的话，一页 10 条评论要发 10 个请求，
  // 而绝大多数楼中楼根本不会被展开
  if (expanded.value && !loaded.value && !subLoading.value) {
    await loadSubs(1)
  }
}

function resolveIpRegion(sub) {
  return String(
    sub?.subReply?.ipRegion
    || sub?.ipRegion
    || sub?.subReply?.ip_region
    || '',
  ).trim()
}

// 时间和 IP 分开渲染：挤在同一行会把昵称压得只剩几个字。
// IP 留在头部原位，时间挪到操作行右侧、举报图标左边
function formatSubTime(createTime) {
  return formatCommentTimeShanghai(createTime)
}

// 原本一律截到 4 个字，"儒雅的诺诺吖"会变成"儒雅的诺..."。
// 时间移走之后头部宽松了，放到 12 字，更长的交给 CSS 省略号
function compactNickname(value) {
  const chars = Array.from(String(value || '用户'))
  return chars.length > 12 ? `${chars.slice(0, 12).join('')}...` : chars.join('')
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

// 快速翻页会并发出多个请求，返回顺序不定，后到的旧页会覆盖新页。
// 用自增序号只认最后一次请求的结果
let loadSeq = 0

async function loadSubs(p = 1) {
  page.value = p
  const seq = ++loadSeq
  subLoading.value = true
  try {
    const res = await getSubReplyList({ replyId: props.replyId, pageNum: p, pageSize: pageSize.value })
    if (seq !== loadSeq) return
    if (res.code !== 0) return
    const raw = res.data
    subList.value = unwrapPageRecords(raw).map((row) => ({
      ...row,
      liked: !!row.liked,
    }))
    loadedTotal.value = raw?.total != null ? Number(raw.total) : subList.value.length
    loaded.value = true
  } finally {
    if (seq === loadSeq) subLoading.value = false
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

const subLikePending = ref(new Set())

async function toggleSubLike(sub) {
  if (!(await ensureLoggedIn('点赞需要登录'))) return
  const subReplyId = sub?.subReply?.id
  if (!subReplyId) return
  // 逐条防重：连点会重复请求，而点赞数是本地加减的，会被加错
  if (subLikePending.value.has(subReplyId)) return
  subLikePending.value = new Set(subLikePending.value).add(subReplyId)
  try {
    const res = sub.liked ? await unlikeSubReply(subReplyId) : await likeSubReply(subReplyId)
    if (res.code === 0) {
      sub.liked = !sub.liked
      const base = Number(sub.subReply.likeCount) || 0
      sub.subReply.likeCount = Math.max(0, base + (sub.liked ? 1 : -1))
    }
    // 失败原因由响应拦截器统一提示，这里不再叠加原始 HTTP 错误
  } finally {
    const next = new Set(subLikePending.value)
    next.delete(subReplyId)
    subLikePending.value = next
  }
}

watch(
  () => [props.replyId, props.articleId],
  async ([replyId, articleId]) => {
    if (replyId == null || replyId === '' || articleId == null || articleId === '') return
    expanded.value = false
    page.value = 1
    subList.value = []
    loadedTotal.value = 0
    loaded.value = false
    // 这里原本 immediate 就拉一次子回复：一页 10 条评论 = 10 个请求，
    // 而折叠态只需要一个数量，父级已经通过 subReplyCount 传下来了
  },
  { immediate: true },
)

watch(
  () => props.refreshToken,
  async (token, prev) => {
    if (!token || token === prev) return
    expanded.value = true
    // 子回复也是时间正序，新回复落在最后一页。跳第 1 页的话，
    // 超过一页的楼中楼里用户看不到自己刚发的那条。
    // 这是个小分页组件，跳末页不会丢失任何浏览位置
    const known = Math.max(Number(props.subReplyCount) || 0, loadedTotal.value)
    await loadSubs(Math.max(1, Math.ceil((known + 1) / pageSize.value)))
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
