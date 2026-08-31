import draftIconUrl from '@/assets/svg/草稿.svg?url'
import publishedIconUrl from '@/assets/svg/已发布.svg?url'
import pendingIconUrl from '@/assets/svg/审核中.svg?url'
import rejectedIconUrl from '@/assets/svg/审核失败.svg?url'
import auditErrorIconUrl from '@/assets/svg/审核异常.svg?url'

// 与后端 ArticleStatus 一致 audit async api.md
export const ARTICLE_STATUS = {
  DRAFT: 0,
  PENDING_AUDIT: 1,
  APPROVED: 2,
  REJECTED: 3,
  AUDIT_ERROR: 4,
  PUBLISHED: 5,
}

export function articleStatusLabel(status) {
  const s = Number(status)
  const map = {
    0: '草稿',
    1: '审核中',
    2: '审核通过',
    3: '审核未通过',
    4: '审核异常',
    5: '已发布',
  }
  return map[s] ?? '未知'
}

// 只有已发布的帖子能进详情页。作者本人也一样：草稿和审核中还没定稿，
// 未通过 / 异常的原因在创作中心卡片与编辑页里已经说清楚了
export function canOpenArticleDetail(status) {
  return Number(status) === ARTICLE_STATUS.PUBLISHED
}

export function articleDetailBlockedHint(status) {
  const s = Number(status)
  if (s === ARTICLE_STATUS.PENDING_AUDIT || s === ARTICLE_STATUS.APPROVED) {
    return '帖子正在审核中，结果会通过站内信通知'
  }
  if (s === ARTICLE_STATUS.DRAFT) return '草稿还没发布，去创作中心继续编辑吧'
  return '帖子未通过审核，可在下方查看原因或点编辑修改'
}

// 收藏夹里帖子失效时盖在卡片上的说明。用户看的是"为什么打不开"，
// 不是创作者视角的审核流程，所以文案比 articleStatusLabel 更直白
export function favoriteBlockedReason(status) {
  if (status === null || status === undefined || status === '') return '该帖子已删除'
  const s = Number(status)
  if (s === ARTICLE_STATUS.PUBLISHED) return ''
  if (s === ARTICLE_STATUS.DRAFT) return '该帖子已转为草稿'
  if (s === ARTICLE_STATUS.PENDING_AUDIT || s === ARTICLE_STATUS.APPROVED) return '该帖子审核中'
  if (s === ARTICLE_STATUS.REJECTED) return '该帖子未通过审核'
  if (s === ARTICLE_STATUS.AUDIT_ERROR) return '该帖子审核异常'
  return '该帖子已失效'
}

export function isArticleEditingLocked(status) {
  return Number(status) === ARTICLE_STATUS.PENDING_AUDIT
}

export function canSubmitArticleAudit(status) {
  const s = Number(status)
  return [0, 3, 4, 5].includes(s)
}

// 创作中心列表：状态图标 + 悬停提示
export function articleStatusMeta(status) {
  const s = Number(status)
  const label = articleStatusLabel(s)
  const map = {
    [ARTICLE_STATUS.DRAFT]: { icon: draftIconUrl, tip: '草稿', isDraft: true },
    [ARTICLE_STATUS.PENDING_AUDIT]: { icon: pendingIconUrl, tip: '审核中', isDraft: false },
    [ARTICLE_STATUS.APPROVED]: { icon: pendingIconUrl, tip: '审核通过（待发布）', isDraft: false },
    [ARTICLE_STATUS.REJECTED]: { icon: rejectedIconUrl, tip: '审核未通过', isDraft: false },
    [ARTICLE_STATUS.AUDIT_ERROR]: { icon: auditErrorIconUrl, tip: '审核系统异常', isDraft: false },
    [ARTICLE_STATUS.PUBLISHED]: { icon: publishedIconUrl, tip: '已发布', isDraft: false },
  }
  return map[s] ?? { icon: pendingIconUrl, tip: label, isDraft: false }
}
