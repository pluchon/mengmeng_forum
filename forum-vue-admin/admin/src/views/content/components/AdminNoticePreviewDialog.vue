<template>
  <a-modal
    v-model:visible="visible"
    :title="detail?.title || '公告预览'"
    width="min(880px, 96vw)"
    :footer="false"
    unmount-on-close
    class="notice-preview-dialog"
    @cancel="visible = false"
  >
    <a-spin :loading="loading" style="width: 100%">
      <template v-if="detail">
        <a-card class="np-card np-card--hero" :bordered="true" size="small">
          <div class="np-shell" :class="{ 'np-shell--plain': !isHeroTemplate }">
            <div class="np-text">
              <h2 class="np-title">{{ detail.title }}</h2>
              <p v-if="detail.subtitle" class="np-sub">{{ detail.subtitle }}</p>

              <div v-if="featureRows.length" class="np-features">
                <div v-for="(feat, idx) in featureRows" :key="idx" class="np-feature">
                  <a-tag :color="feat.color" size="small">{{ feat.label }}</a-tag>
                  <span class="np-feature-text">{{ feat.text }}</span>
                </div>
              </div>

              <div class="np-md arco-typography" v-html="mdHtml"></div>
            </div>

            <div v-if="isHeroTemplate && coverSrc" class="np-media">
              <div class="np-media-frame">
                <img class="np-cover" :src="coverSrc" alt="">
              </div>
            </div>
          </div>
        </a-card>

        <div class="np-footer">
          <a-button type="primary" @click="visible = false">关闭</a-button>
        </div>
      </template>
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
import type { NoticeDetail } from '@/apis/content/notice'
import { getNoticeDetail } from '@/apis/content/notice'
import { marked } from 'marked'

const props = defineProps<{
  noticeId: string | null
}>()

const visible = defineModel<boolean>('visible', { default: false })

const loading = ref(false)
const detail = ref<NoticeDetail | null>(null)

function parseBody(bodyJson: string | undefined) {
  try {
    const o = typeof bodyJson === 'string' ? JSON.parse(bodyJson || '{}') : (bodyJson || {})
    const highlights = Array.isArray(o.highlights) ? o.highlights : []
    const cover = typeof o.coverImageUrl === 'string' ? o.coverImageUrl.trim() : ''
    return { cover, highlights }
  } catch {
    return { cover: '', highlights: [] as unknown[] }
  }
}

function normalizeHighlight(h: unknown, idx: number) {
  if (typeof h === 'string') {
    const colors = ['red', 'green', 'orange', 'arcoblue'] as const
    return { label: '要点', text: h, color: colors[idx % colors.length] }
  }
  const o = h as { label?: string, text?: string, labelColor?: string }
  const label = o.label || '要点'
  const text = o.text || ''
  const c = (o.labelColor || '').toLowerCase()
  let color: 'red' | 'orangered' | 'green' | 'orange' | 'arcoblue' | 'purple' = 'arcoblue'
  if (c.includes('f53') || c.includes('ff4') || c === 'red')
    color = 'red'
  else if (c.includes('00b') || c.includes('green'))
    color = 'green'
  else if (c.includes('ff7') || c.includes('faad') || c.includes('orange'))
    color = 'orange'
  else if (c.includes('722') || c.includes('purple'))
    color = 'purple'
  return { label, text, color }
}

const bodyInfo = computed(() => parseBody(detail.value?.bodyJson))

const featureRows = computed(() =>
  bodyInfo.value.highlights.map((h, idx) => normalizeHighlight(h, idx))
)

const mdHtml = computed(() => {
  const raw = detail.value?.contentMarkdown?.trim()
  if (!raw)
    return '<p class="np-md-empty">暂无正文</p>'
  try {
    return marked.parse(raw, { async: false }) as string
  } catch {
    return '<p class="np-md-empty">正文解析失败</p>'
  }
})

const isHeroTemplate = computed(() => detail.value?.templateId === 'welcome_hero_right')

const coverSrc = computed(() => bodyInfo.value.cover?.trim() || '')

watch(
  () => [visible.value, props.noticeId] as const,
  async ([v, id]) => {
    if (!v || !id) {
      if (!v)
        detail.value = null
      return
    }
    loading.value = true
    try {
      const res = await getNoticeDetail({ id })
      detail.value = res?.data ?? null
    } catch {
      detail.value = null
    } finally {
      loading.value = false
    }
  }
)
</script>

<style scoped lang="scss">
.notice-preview-dialog {
  :deep(.arco-modal-body) {
    max-height: min(82vh, 720px);
    padding: 16px 20px 12px;
    overflow: auto;
    background: var(--color-fill-1);
  }
}

.np-card {
  border-radius: 12px;
  box-shadow: 0 2px 12px rgb(0 0 0 / 6%);

  &--hero {
    border: 1px solid var(--color-border-2);
  }

  :deep(.arco-card-body) {
    padding: 20px 22px 24px;
  }
}

.np-shell {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  align-items: flex-start;

  &--plain {
    flex-direction: column;
  }
}

.np-text {
  flex: 1 1 320px;
  min-width: 0;
}

.np-title {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.35;
  color: var(--color-text-1);
}

.np-sub {
  margin: 0 0 16px;
  font-size: 14px;
  color: var(--color-text-3);
}

.np-features {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
}

.np-feature {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: flex-start;
}

.np-feature-text {
  flex: 1;
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text-2);
}

.np-md {
  margin-top: 8px;
  font-size: 14px;
  line-height: 1.75;
  color: var(--color-text-1);
}

.np-md :deep(h1),
.np-md :deep(h2),
.np-md :deep(h3) {
  margin: 0.7em 0 0.4em;
  font-weight: 600;
}

.np-md :deep(p) {
  margin: 0.5em 0;
}

.np-md :deep(ul),
.np-md :deep(ol) {
  padding-left: 1.25em;
}

.np-md-empty {
  margin: 0;
  color: var(--color-text-3);
}

.np-media {
  flex: 0 0 280px;
  width: 100%;
  max-width: 320px;
}

.np-media-frame {
  padding: 8px;
  background: var(--color-fill-2);
  border-radius: 16px;
  box-shadow: 0 4px 14px rgb(0 0 0 / 8%);
}

.np-cover {
  display: block;
  width: 100%;
  max-height: 320px;
  object-fit: cover;
  border-radius: 12px;
}

.np-shell--plain .np-media {
  display: none;
}

.np-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
