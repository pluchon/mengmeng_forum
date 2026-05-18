<template>
  <a-modal
    v-model:visible="visible"
    :title="previewTitle"
    width="min(880px, 96vw)"
    :footer="false"
    unmount-on-close
    class="notice-live-preview-dialog"
    @cancel="visible = false"
  >
    <a-card class="np-card np-card--hero" :bordered="true" size="small">
      <div class="np-shell" :class="{ 'np-shell--plain': !isHeroTemplate }">
        <div class="np-text">
          <h2 class="np-title">{{ displayTitle }}</h2>
          <p v-if="displaySubtitle" class="np-sub">{{ displaySubtitle }}</p>

          <div v-if="featureRows.length" class="np-features">
            <div v-for="(feat, idx) in featureRows" :key="idx" class="np-feature">
              <a-tag :color="feat.color" size="small">{{ feat.label }}</a-tag>
              <span class="np-feature-text">{{ feat.text }}</span>
            </div>
          </div>

          <div class="np-md arco-typography" v-html="mdHtml" />
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
  </a-modal>
</template>

<script setup lang="ts">
import { marked } from 'marked'

const props = defineProps<{
  templateId: string
  title: string
  subtitle: string
  contentMarkdown: string
  highlightLines: string
  coverImageUrl: string
}>()

const visible = defineModel<boolean>('visible', { default: false })

const previewTitle = computed(() => {
  const t = props.title.trim()
  return t ? `\u7528\u6237\u7aef\u9884\u89c8 \u00b7 ${t}` : '\u7528\u6237\u7aef\u9884\u89c8'
})

const displayTitle = computed(() => props.title.trim() || '\u4e3b\u6807\u9898')
const displaySubtitle = computed(() => props.subtitle.trim())

const isHeroTemplate = computed(() => props.templateId === 'welcome_hero_right')

function normalizeHighlight(text: string, idx: number) {
  const colors = ['red', 'green', 'orange', 'arcoblue'] as const
  return { label: '\u8981\u70b9', text, color: colors[idx % colors.length] }
}

const featureRows = computed(() =>
  props.highlightLines
    .split('\n')
    .map(s => s.trim())
    .filter(Boolean)
    .map((text, idx) => normalizeHighlight(text, idx)),
)

const mdHtml = computed(() => {
  const raw = props.contentMarkdown?.trim()
  if (!raw)
    return '<p class="np-md-empty">\u6682\u65e0\u6b63\u6587</p>'
  try {
    return marked.parse(raw, { async: false }) as string
  }
  catch {
    return '<p class="np-md-empty">\u6b63\u6587\u89e3\u6790\u5931\u8d25</p>'
  }
})

const coverSrc = computed(() => props.coverImageUrl?.trim() || '')
</script>

<style scoped lang="scss">
.notice-live-preview-dialog {
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
  margin: 0;
  font-size: 28px;
  font-weight: 900;
  line-height: 1.35;
  color: #1d2129;
}

.np-sub {
  margin: 10px 0 20px;
  font-size: 15px;
  color: #86909c;
}

.np-features {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 16px;
}

.np-feature {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.np-feature-text {
  flex: 1;
  font-size: 14px;
  line-height: 1.6;
  color: #4e5969;
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
  max-height: 280px;
  object-fit: cover;
  border-radius: 24px;
  box-shadow: 0 20px 40px rgb(0 0 0 / 10%);
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

