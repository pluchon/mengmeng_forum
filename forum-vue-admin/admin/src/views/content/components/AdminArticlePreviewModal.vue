<template>
  <a-modal
    v-model:visible="visible"
    title="帖子预览"
    width="min(960px, 96vw)"
    :footer="false"
    unmount-on-close
    class="article-preview-modal"
    @cancel="visible = false"
  >
    <a-spin :loading="loading" style="width: 100%">
      <template v-if="detail">
        <a-card class="preview-section preview-section--meta" :bordered="true" size="small">
          <template #title>
            <span class="preview-section-title">基本信息</span>
          </template>
          <div class="meta-head">
            <h2 class="meta-title">{{ detail.title }}</h2>
            <div class="meta-row">
              <p class="meta-board">{{ boardLine }}</p>
              <div class="meta-author">
                <AdminVipAvatar
                  :src="detail.authorAvatarUrl"
                  :vip-tier="detail.authorVipTier"
                  :vip-expire-at="detail.authorVipExpireAt"
                  :size="36"
                  :fallback-text="(detail.nickname || detail.username || '?').slice(0, 1)"
                />
                <span class="meta-author-name">{{ detail.nickname || detail.username || '—' }}</span>
              </div>
            </div>
          </div>
          <a-descriptions :column="1" size="small" class="meta-desc meta-desc--status">
            <a-descriptions-item label="帖子状态">
              <ArticleStatusIcon
                :status="detail.status"
                :state="detail.state"
                :delete-state="detail.deleteState"
              />
            </a-descriptions-item>
          </a-descriptions>
        </a-card>

        <a-card
          v-if="detail.coverImg || galleryOnly.length"
          class="preview-section preview-section--media"
          :bordered="true"
          size="small"
        >
          <div v-if="detail.coverImg" class="media-block">
            <div class="media-block__label">封面图</div>
            <div class="cover-wrap">
              <img class="cover-img" :src="detail.coverImg" alt="封面图">
            </div>
          </div>

          <div v-if="detail.coverImg && galleryOnly.length" class="media-divider" />

          <div v-if="galleryOnly.length" class="media-block">
            <div class="media-block__label">帖子内容图片</div>
            <a-space wrap class="gallery-row">
              <a-image
                v-for="(u, i) in galleryOnly"
                :key="i"
                :src="u"
                width="132"
                height="132"
                fit="contain"
                class="thumb-img"
              />
            </a-space>
          </div>
        </a-card>

        <a-card class="preview-section" :bordered="true" size="small">
          <template #title>
            <span class="preview-section-title">正文</span>
            <a-tag v-if="detail.contentType === 1" size="small" class="preview-tag">Markdown</a-tag>
            <a-tag v-else size="small" class="preview-tag">富文本</a-tag>
          </template>
          <div class="body-scroll scrollbar-hidden">
            <div class="preview-body arco-typography" v-html="bodyHtml" />
          </div>
        </a-card>

        <a-card class="preview-section" :bordered="true" size="small">
          <template #title>
            <span class="preview-section-title">热门评论</span>
            <span class="preview-hint">按点赞排序，最多 10 条</span>
          </template>
          <a-empty v-if="!topComments.length" description="暂无评论" />
          <ul v-else class="comment-list">
            <li v-for="(c, idx) in topComments" :key="idx" class="comment-item">
              <a-avatar :size="32" class="comment-avatar">
                <img v-if="c.avatarUrl" :src="c.avatarUrl" alt="">
                <span v-else>{{ (c.nickname || '?').slice(0, 1) }}</span>
              </a-avatar>
              <div class="comment-main">
                <div class="comment-head">
                  <span class="comment-nick">{{ c.nickname }}</span>
                  <span class="comment-likes">
                    <icon-thumb-up />
                    {{ c.likeCount ?? 0 }}
                  </span>
                </div>
                <p class="comment-text">{{ c.content }}</p>
              </div>
            </li>
          </ul>
        </a-card>
      </template>
    </a-spin>
  </a-modal>
</template>


<script setup lang="ts">
import type { ArticlePreview } from '@/apis/content/article'
import { getArticlePreview } from '@/apis/content/article'
import AdminVipAvatar from '@/components/AdminVipAvatar.vue'
import { marked } from 'marked'
import ArticleStatusIcon from './ArticleStatusIcon.vue'

const props = defineProps<{
  articleId: string | null
}>()

const visible = defineModel<boolean>('visible', { default: false })

const loading = ref(false)
const detail = ref<ArticlePreview | null>(null)

const boardLine = computed(() => {
  const d = detail.value
  if (!d)
    return ''
  const cat = d.categoryName?.trim()
  const board = d.boardName?.trim()
  if (cat && board)
    return `${cat} \u00b7 ${board}`
  return cat || board || '\u2014'
})

const topComments = computed(() => detail.value?.topComments ?? [])

const bodyHtml = computed(() => {
  const d = detail.value
  if (!d?.content)
    return '<p class="preview-empty">\uff08\u65e0\u6b63\u6587\uff09</p>'
  if (d.contentType === 1)
    return marked.parse(d.content, { async: false }) as string
  return d.content
})

const galleryOnly = computed(() => {
  const urls = detail.value?.imageUrls ?? []
  const c = detail.value?.coverImg
  if (!c)
    return urls
  return urls.filter(u => u && u !== c)
})

watch(
  () => [visible.value, props.articleId] as const,
  async ([v, id]) => {
    if (!v || !id) {
      if (!v)
        detail.value = null
      return
    }
    loading.value = true
    try {
      const res = await getArticlePreview({ id })
      const data = res?.data
      detail.value = data
        ? {
            ...data,
            categoryName: data.categoryName ?? '',
            authorVipTier: Number(data.authorVipTier) || 0,
            topComments: Array.isArray(data.topComments) ? data.topComments : []
          }
        : null
    } catch {
      detail.value = null
    } finally {
      loading.value = false
    }
  }
)
</script>


<style scoped lang="scss">
.article-preview-modal {
  :deep(.arco-modal-header) {
    border-bottom: 1px solid var(--color-border-2);
  }

  :deep(.arco-modal-body) {
    max-height: min(78vh, 860px);
    padding: 16px 20px 20px;
    overflow: auto;
    background: var(--color-fill-1);
    scrollbar-width: none;
    -ms-overflow-style: none;

    &::-webkit-scrollbar {
      display: none;
      width: 0;
      height: 0;
    }
  }
}

.scrollbar-hidden {
  scrollbar-width: none;
  -ms-overflow-style: none;

  &::-webkit-scrollbar {
    display: none;
    width: 0;
    height: 0;
  }
}

.preview-section {
  margin-bottom: 14px;
  overflow: hidden;
  border-radius: 10px;
  box-shadow: 0 1px 4px rgb(0 0 0 / 6%);

  &--meta {
    background: linear-gradient(135deg, rgb(var(--primary-1)) 0%, var(--color-bg-2) 48%);
    border: 1px solid rgb(var(--primary-2));
  }

  &--media {
    :deep(.arco-card-header) {
      display: none;
    }
  }

  :deep(.arco-card-header) {
    border-bottom: 1px solid var(--color-border-2);
  }

  :deep(.arco-card-body) {
    padding: 14px 16px 16px;
  }
}

.preview-section-title {
  font-weight: 600;
  color: var(--color-text-1);
}

.preview-tag {
  margin-left: 8px;
  vertical-align: middle;
}

.preview-hint {
  margin-left: 10px;
  font-size: 12px;
  font-weight: normal;
  color: var(--color-text-3);
}

.meta-head {
  margin-bottom: 10px;
}

.meta-title {
  margin: 0 0 10px;
  font-size: 20px;
  font-weight: 700;
  line-height: 1.35;
  color: var(--color-text-1);
}

.meta-row {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
}

.meta-board {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-2);
}

.meta-author {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
  align-items: center;
}

.meta-author-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-1);
}

.meta-desc--status {
  :deep(.arco-descriptions-item-label) {
    text-align: center;
  }

  :deep(.arco-descriptions-item-value) {
    display: flex;
    justify-content: center;
    align-items: center;
  }
}

.media-block {
  &__label {
    margin-bottom: 10px;
    font-size: 13px;
    font-weight: 600;
    color: var(--color-text-1);
    text-align: center;
  }
}

.media-divider {
  height: 0;
  margin: 16px 0;
  border-top: 1px solid var(--color-border-2);
}

.cover-wrap {
  display: flex;
  justify-content: center;
}

.cover-img {
  display: block;
  max-width: 100%;
  max-height: min(40vh, 400px);
  width: auto;
  height: auto;
  object-fit: contain;
  border-radius: 6px;
}

.gallery-row {
  display: flex;
  justify-content: center;
  width: 100%;
}

.thumb-img {
  overflow: hidden;
  border-radius: 8px;
}

.body-scroll {
  max-height: min(38vh, 400px);
  padding: 4px 2px;
  overflow: auto;
  border-radius: 8px;
  background: var(--color-bg-1);
}

.preview-body {
  min-height: 48px;
  font-size: 14px;
  line-height: 1.75;
  color: var(--color-text-1);
}

.preview-body :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 6px;
}

.preview-empty {
  margin: 0;
  color: var(--color-text-3);
}

.comment-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.comment-item {
  display: flex;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid var(--color-border-2);

  &:last-child {
    border-bottom: none;
  }
}

.comment-main {
  flex: 1;
  min-width: 0;
}

.comment-head {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.comment-nick {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-1);
}

.comment-likes {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  font-size: 12px;
  color: var(--color-text-3);
}

.comment-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--color-text-2);
  word-break: break-word;
}
</style>

