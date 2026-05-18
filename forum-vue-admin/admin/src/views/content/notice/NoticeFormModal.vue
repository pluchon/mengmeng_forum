<template>
  <a-modal
    :visible="visible"
    title=""
    width="min(1020px, 96vw)"
    :mask-closable="false"
    unmount-on-close
    class="notice-form-modal"
    @cancel="close"
    @before-ok="handleBeforeOk"
  >
    <template #title>
      <div class="notice-modal-head">
        <span class="notice-modal-head__title">{{ modalTitle }}</span>
      </div>
    </template>

    <a-form ref="formRef" :model="form" :rules="rules" layout="vertical" class="notice-form">
      <div class="notice-dbody">
        <div class="notice-left-col">
          <div class="section-label">基本信息</div>

          <a-row :gutter="10" class="field-row">
            <a-col :span="12">
              <a-form-item field="templateId" label="展示模板" required>
                <a-select v-model="form.templateId" :options="templateOptions" placeholder="选择模板" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item field="sidebarKey" label="侧栏标识" required>
                <a-input v-model="form.sidebarKey" placeholder="如 onboarding_welcome" allow-clear />
                <template #extra>
                  <span class="field-hint">英文/数字，勿随意修改</span>
                </template>
              </a-form-item>
            </a-col>
          </a-row>

          <div class="tpl-preview" role="img" :aria-label="templatePreviewLabel">
            <div class="tpl-preview__text">
              <div class="tpl-preview__title">{{ form.title.trim() || '主标题区' }}</div>
              <div class="tpl-preview__sub">{{ form.subtitle.trim() || '副标题 / 说明' }}</div>
            </div>
            <div v-if="form.templateId === 'welcome_hero_right'" class="tpl-preview__img">
              <icon-image />
            </div>
          </div>

          <a-form-item v-if="isBoardRule" field="categoryScope" label="版规适用范围" required>
            <a-select
              v-model="form.categoryScope"
              :options="categorySelectOptions"
              placeholder="全站或指定分类"
              allow-search
            />
          </a-form-item>

          <div class="section-label">公告文案</div>

          <a-row :gutter="10" class="field-row">
            <a-col :span="12">
              <a-form-item field="title" label="公告标题" required>
                <a-input v-model="form.title" placeholder="主标题" allow-clear />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item field="subtitle" label="副标题">
                <a-input v-model="form.subtitle" placeholder="一句话说明，可选" allow-clear />
              </a-form-item>
            </a-col>
          </a-row>

          <a-form-item field="contentMarkdown" required class="notice-form-item--md">
            <template #label>
              <span>公告内容</span>
              <span class="label-opt">markdown编码</span>
            </template>
            <div class="md-split">
              <div class="md-split__pane md-split__pane--edit">
                <div class="md-split__pane-title">编辑</div>
                <a-textarea
                  v-model="form.contentMarkdown"
                  class="md-split__textarea"
                  :auto-size="{ minRows: 10, maxRows: 18 }"
                  placeholder="支持 Markdown：标题、列表、加粗、链接等"
                />
              </div>
              <div class="md-split__pane md-split__pane--preview">
                <div class="md-split__pane-title">预览</div>
                <div class="md-split__preview arco-typography" v-html="previewHtml" />
              </div>
            </div>
          </a-form-item>

          <div class="r-section r-section--extra">
            <div class="r-title">
              <icon-image />
              卡片配图与要点
              <span class="r-title-opt">(可选)</span>
            </div>
            <a-form-item field="coverImageUrl" hide-label class="cover-upload-item">
              <div class="upload-row">
                <a-upload :custom-request="onCoverCustomRequest" accept="image/*" :show-file-list="false">
                  <template #upload-button>
                    <a-button type="outline" size="small" :loading="coverUploading" class="upload-btn">
                      上传图片
                    </a-button>
                  </template>
                </a-upload>
                <a-input
                  v-model="form.coverImageUrl"
                  class="upload-row__input"
                  placeholder="上传后自动填入 URL，也可手动粘贴外链"
                  allow-clear
                  size="small"
                />
              </div>
            </a-form-item>
            <a-form-item field="highlightLines" hide-label>
              <a-textarea
                v-model="form.highlightLines"
                :auto-size="{ minRows: 3, maxRows: 8 }"
                placeholder="每行一条要点，会显示在模板高亮区；可留空"
              />
            </a-form-item>
          </div>
        </div>

        <div class="notice-right-col">
          <div class="r-section r-section--status">
            <div class="r-title">
              <icon-send />
              发布状态
            </div>
            <a-form-item field="publishState" hide-label class="status-form-item status-form-item--stack">
              <a-radio-group v-model="form.publishState" direction="vertical" class="status-seg status-seg--vertical">
                <a-radio :value="0">草稿</a-radio>
                <a-radio :value="1">已发布</a-radio>
              </a-radio-group>
              <p class="publish-hint publish-hint--below">仅「已发布」会在用户端公告中心展示；草稿保存后用户看不到。</p>
            </a-form-item>
          </div>

          <div class="r-section">
            <div class="r-title">
              <icon-tag />
              公告类型
            </div>
            <a-form-item field="noticeKind" hide-label>
              <div class="type-list">
                <button
                  v-for="opt in kindOptions"
                  :key="String(opt.value)"
                  type="button"
                  class="type-list__item"
                  :class="{ 'type-list__item--active': Number(form.noticeKind) === Number(opt.value) }"
                  @click="form.noticeKind = Number(opt.value)"
                >
                  {{ opt.label }}
                </button>
              </div>
            </a-form-item>
          </div>

          <div class="r-section r-section--preview-btn">
            <div class="r-title">
              <icon-eye />
              效果预览
            </div>
            <a-button type="outline" long class="preview-open-btn" @click="livePreviewVisible = true">
              查看用户端效果
            </a-button>
          </div>
        </div>
      </div>
    </a-form>

    <NoticeFormLivePreviewDialog
      v-model:visible="livePreviewVisible"
      :template-id="form.templateId"
      :title="form.title"
      :subtitle="form.subtitle"
      :content-markdown="form.contentMarkdown"
      :highlight-lines="form.highlightLines"
      :cover-image-url="form.coverImageUrl"
    />
  </a-modal>
</template>

<script setup lang="ts">
import type { FormInstance } from '@arco-design/web-vue'
import { Message } from '@arco-design/web-vue'
import type { RequestOption, UploadRequest } from '@arco-design/web-vue/es/upload/interfaces'
import axios from 'axios'
import { marked } from 'marked'
import type { NoticeCategoryOption } from '@/apis/content/notice'
import { getNoticeCategories, getNoticeDetail, saveNotice, updateNotice } from '@/apis/content/notice'
import { getToken } from '@/utils/auth'
import { useDict } from '@/hooks'
import NoticeFormLivePreviewDialog from './NoticeFormLivePreviewDialog.vue'

const MSG = {
  kindInvalid: '公告类型无效',
  bodyFail: '扩展字段生成失败',
  saveOk: '保存成功',
  uploadFail: '上传失败',
  noUrl: '上传返回无 URL',
  uploadOk: '配图已上传',
  noContent: '暂无内容',
  mdFail: '预览解析失败，请检查语法',
  defaultKind: '公告',
  siteWide: '全站通用',
}

const props = defineProps<{
  visible: boolean
  editId: string | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'success'): void
}>()

const { dictData } = useDict(['FORUM_NOTICE_KIND'] as const)

const kindOptions = computed(() => dictData.value.FORUM_NOTICE_KIND || [])

const modalTitle = computed(() => (props.editId ? '编辑公告' : '新增公告'))

const templateOptions = [
  { label: '欢迎横版 + 右侧图', value: 'welcome_hero_right' },
  { label: '纯段落 / 列表', value: 'plain_sections' },
]

const categoryRows = ref<NoticeCategoryOption[]>([])

const categorySelectOptions = computed(() => {
  const rest = categoryRows.value.map(c => ({
    label: c.name,
    value: Number(c.id),
  }))
  return [{ label: MSG.siteWide, value: 0 }, ...rest]
})

const formRef = ref<FormInstance>()
const coverUploading = ref(false)
const livePreviewVisible = ref(false)

const form = reactive({
  noticeKind: 0 as number | undefined,
  categoryScope: 0,
  templateId: 'welcome_hero_right',
  sidebarKey: '',
  title: '',
  subtitle: '',
  contentMarkdown: '',
  coverImageUrl: '',
  highlightLines: '',
  pinTop: 0 as 0 | 1,
  publishState: 1 as 0 | 1,
})

const templatePreviewLabel = computed(() =>
  form.templateId === 'welcome_hero_right' ? '横版主视觉 + 右侧配图 示意' : '多段正文与列表 示意',
)

const isBoardRule = computed(() => Number(form.noticeKind) === 4)

function onCoverCustomRequest(option: RequestOption): UploadRequest {
  const file = option.fileItem?.file as File | undefined
  if (!file) {
    option.onError(new Error('no file'))
    return {}
  }
  coverUploading.value = true
  const fd = new FormData()
  fd.append('file', file)
  const base = import.meta.env.VITE_API_PREFIX || ''
  const noticeId = props.editId ? Number(props.editId) : 0
  axios
    .post(`${base}/file/uploadNoticePicture?noticeId=${noticeId}`, fd, {
      headers: { Authorization: getToken() || '' },
    })
    .then((res) => {
      const body = res.data as { code?: number, message?: string, data?: string }
      if (body?.code !== undefined && body.code !== 0) {
        Message.error(body.message || MSG.uploadFail)
        option.onError(new Error(body.message))
        return
      }
      const url = typeof body?.data === 'string' ? body.data : ''
      if (!url) {
        Message.error(MSG.noUrl)
        option.onError(new Error('no url'))
        return
      }
      form.coverImageUrl = url
      Message.success(MSG.uploadOk)
      option.onSuccess(res.data)
    })
    .catch(() => {
      Message.error(MSG.uploadFail)
      option.onError(new Error('upload'))
    })
    .finally(() => {
      coverUploading.value = false
    })
  return {}
}

const rules = {
  noticeKind: [{ required: true, message: '请选择类型' }],
  categoryScope: [
    {
      validator: (v: number, cb: (err?: string) => void) => {
        if (isBoardRule.value && (v === undefined || v === null || Number.isNaN(Number(v)))) {
          cb('请选择适用范围')
          return
        }
        cb()
      },
    },
  ],
  templateId: [{ required: true, message: '请选择模板' }],
  sidebarKey: [{ required: true, message: '请填写侧栏标识' }],
  title: [{ required: true, message: '请填写标题' }],
  contentMarkdown: [{ required: true, message: '请填写公告正文' }],
  publishState: [{ required: true, message: '请选择发布状态' }],
}

const previewHtml = computed(() => {
  const raw = form.contentMarkdown?.trim() || ''
  if (!raw)
    return '<p class="md-preview-empty">' + MSG.noContent + '</p>'
  try {
    return marked.parse(raw, { async: false }) as string
  }
  catch {
    return '<p class="md-preview-empty">' + MSG.mdFail + '</p>'
  }
})

watch(
  () => form.noticeKind,
  () => {
    if (!isBoardRule.value)
      form.categoryScope = 0
  },
)

async function ensureCategories() {
  if (categoryRows.value.length)
    return
  try {
    const res = await getNoticeCategories()
    categoryRows.value = res.data || []
  }
  catch {
    categoryRows.value = []
  }
}

watch(
  () => props.visible,
  async (vis) => {
    if (vis)
      await ensureCategories()
  },
)

function parseBodyToForm(bodyJson: string | undefined) {
  form.coverImageUrl = ''
  form.highlightLines = ''
  if (!bodyJson?.trim())
    return
  try {
    const o = JSON.parse(bodyJson) as { coverImageUrl?: string, highlights?: string[] }
    if (o.coverImageUrl)
      form.coverImageUrl = String(o.coverImageUrl)
    if (Array.isArray(o.highlights)) {
      form.highlightLines = o.highlights
        .map((x: unknown) => {
          if (typeof x === 'string')
            return x
          if (x && typeof x === 'object' && 'text' in (x as object))
            return String((x as { text?: string }).text || '')
          return ''
        })
        .map(s => s.trim())
        .filter(Boolean)
        .join('\n')
    }
  }
  catch {
    /* ignore */
  }
}

function buildBodyJson(): string {
  const highlights = form.highlightLines
    .split('\n')
    .map(s => s.trim())
    .filter(Boolean)
  return JSON.stringify({
    highlights,
    coverImageUrl: form.coverImageUrl?.trim() || '',
  })
}

function resetForm() {
  form.noticeKind = undefined
  form.categoryScope = 0
  form.templateId = 'welcome_hero_right'
  form.sidebarKey = ''
  form.title = ''
  form.subtitle = ''
  form.contentMarkdown = ''
  form.coverImageUrl = ''
  form.highlightLines = ''
  form.pinTop = 0
  form.publishState = 1
  form.noticeKind = 0
}

async function loadDetail(id: string) {
  const res = await getNoticeDetail({ id })
  const d = res.data
  if (!d)
    return
  form.noticeKind = d.noticeKind
  form.categoryScope = Number(d.categoryScope) || 0
  form.templateId = d.templateId
  form.sidebarKey = d.sidebarKey
  form.title = d.title
  form.subtitle = d.subtitle || ''
  form.contentMarkdown = d.contentMarkdown || ''
  parseBodyToForm(d.bodyJson)
  form.pinTop = d.pinTop === 1 ? 1 : 0
  form.publishState = (d.publishState === 1 ? 1 : 0) as 0 | 1
}

watch(
  () => [props.visible, props.editId] as const,
  async ([vis, id]) => {
    if (!vis)
      return
    await ensureCategories()
    await nextTick()
    formRef.value?.clearValidate()
    if (id) {
      await loadDetail(id)
    }
    else {
      resetForm()
    }
  },
)

function close() {
  emit('update:visible', false)
}

function toByteNum(v: unknown): number {
  const n = Number(v)
  if (Number.isNaN(n))
    return 0
  return n
}

async function handleBeforeOk() {
  try {
    await formRef.value?.validate()
  }
  catch {
    return false
  }
  const nk = toByteNum(form.noticeKind)
  if (nk < 0 || nk > 4) {
    Message.error(MSG.kindInvalid)
    return false
  }
  let bodyJson: string
  try {
    bodyJson = buildBodyJson()
    JSON.parse(bodyJson)
  }
  catch {
    Message.error(MSG.bodyFail)
    return false
  }
  const scope = isBoardRule.value ? Number(form.categoryScope) || 0 : 0
  const payload: Record<string, unknown> = {
    noticeKind: nk,
    categoryScope: scope,
    templateId: form.templateId,
    sidebarKey: form.sidebarKey.trim(),
    title: form.title.trim(),
    subtitle: form.subtitle?.trim() || '',
    contentMarkdown: form.contentMarkdown.trim(),
    bodyJson,
    pinTop: Number(form.pinTop) === 1 ? 1 : 0,
    publishState: Number(form.publishState) === 1 ? 1 : 0,
  }
  try {
    if (props.editId) {
      payload.id = Number(props.editId)
      await updateNotice(payload)
    }
    else {
      await saveNotice(payload)
    }
    if (Number(payload.publishState) === 0) {
      Message.warning('已保存为草稿，用户端公告中心不会展示。可在列表中点击「发布」。')
    }
    else {
      Message.success(MSG.saveOk)
    }
    emit('success')
    close()
    return true
  }
  catch {
    return false
  }
}
</script>

<style scoped lang="scss">
.notice-form-modal {
  :deep(.arco-modal-header) {
    padding: 14px 20px;
    border-bottom: 1px solid var(--color-border-2);
  }

  :deep(.arco-modal-body) {
    max-height: min(88vh, 900px);
    padding: 0;
    overflow-x: hidden;
    overflow-y: auto;
  }

  :deep(.arco-modal-footer) {
    padding: 12px 20px;
    border-top: 1px solid var(--color-border-2);
  }
}

.notice-modal-head {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding-right: 28px;
}

.notice-modal-head__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-1);
}

.notice-form {
  margin: 0;
}

.notice-dbody {
  display: grid;
  grid-template-columns: 1fr 280px;
  min-height: 520px;
  overflow: visible;
}

.notice-left-col {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px 20px;
  border-right: 1px solid var(--color-border-2);
}

.notice-right-col {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  background: var(--color-fill-1);
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

.section-label {
  padding-bottom: 8px;
  margin-bottom: 2px;
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-3);
  letter-spacing: 0.04em;
  border-bottom: 1px solid var(--color-border-2);
}

.field-row {
  width: 100%;
}

.field-hint {
  font-size: 11px;
  color: var(--color-text-3);
}

.label-opt {
  margin-left: 6px;
  font-size: 11px;
  font-weight: 400;
  color: var(--color-text-3);
}

.tpl-preview {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: var(--color-fill-2);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.tpl-preview__text {
  flex: 1;
  min-width: 0;
}

.tpl-preview__title {
  margin-bottom: 2px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-1);
}

.tpl-preview__sub {
  font-size: 11px;
  color: var(--color-text-3);
}

.tpl-preview__img {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 36px;
  font-size: 18px;
  color: var(--color-text-3);
  background: var(--color-fill-3);
  border-radius: 5px;
}

.notice-form-item--md {
  :deep(.arco-form-item-content-wrapper),
  :deep(.arco-form-item-content) {
    width: 100%;
    max-width: 100%;
  }
}

.md-split {
  display: grid;
  grid-template-columns: 1fr 1fr;
  width: 100%;
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.md-split__pane {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 200px;
}

.md-split__pane--edit {
  border-right: 1px solid var(--color-border-2);
}

.md-split__pane--preview {
  background: var(--color-fill-1);
}

.md-split__pane-title {
  padding: 8px 10px;
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-3);
  background: var(--color-fill-2);
  border-bottom: 1px solid var(--color-border-2);
}

.md-split__textarea {
  flex: 1;
  width: 100%;
  min-height: 180px;
  background: transparent;
  border: none;
  border-radius: 0;
}

.md-split__preview {
  flex: 1;
  min-height: 180px;
  max-height: 280px;
  padding: 10px 12px;
  overflow: auto;
  font-size: 13px;
  line-height: 1.6;
}

.r-section--status {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 96px;
  text-align: center;
}

.r-section--status .r-title {
  justify-content: center;
  width: 100%;
}

.r-section--status :deep(.status-form-item) {
  display: flex;
  justify-content: center;
  width: 100%;
  margin-bottom: 0;
}

.r-section--status .status-seg {
  max-width: 220px;
}

.type-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}

.type-list__item {
  width: 100%;
  padding: 8px 10px;
  font-size: 12px;
  line-height: 1.35;
  color: var(--color-text-2);
  text-align: left;
  cursor: pointer;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 7px;
  transition: border-color 0.15s, background 0.15s, color 0.15s;
}

.type-list__item:hover {
  border-color: rgb(var(--primary-3));
}

.type-list__item--active {
  color: rgb(var(--primary-6));
  background: rgb(var(--primary-1));
  border-color: rgb(var(--primary-4));
}

.md-editor {
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 7px;
}

.md-tabs {
  padding: 6px 8px 0;
  background: var(--color-fill-2);
  border-bottom: 1px solid var(--color-border-2);
}

.md-editor__area {
  width: 100%;
  background: transparent;
  border: none;
}

.md-preview {
  min-height: 120px;
  max-height: 200px;
  padding: 10px 12px;
  overflow: auto;
  font-size: 13px;
  line-height: 1.6;

  :deep(h1),
  :deep(h2),
  :deep(h3) {
    margin: 0.5em 0 0.3em;
  }

  :deep(p) {
    margin: 0.4em 0;
  }

  :deep(ul),
  :deep(ol) {
    padding-left: 1.2em;
  }

  :deep(.md-preview-empty) {
    margin: 0;
    color: var(--color-text-3);
  }
}

.r-section {
  padding: 12px 14px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 10px;

  &--preview-btn {
    .preview-open-btn {
      width: 100%;
    }
  }

  &--extra {
    :deep(.arco-form-item) {
      margin-bottom: 10px;
    }
  }
}

.r-title {
  display: flex;
  gap: 5px;
  align-items: center;
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-2);
}

.r-title-opt {
  font-size: 11px;
  font-weight: 400;
  color: var(--color-text-3);
}

.r-hint {
  margin: 0;
  font-size: 11px;
  line-height: 1.6;
  color: var(--color-text-3);
}

.status-seg {
  display: flex;
  width: 100%;

  :deep(.arco-radio-button) {
    flex: 1;
    justify-content: center;
  }
}

.status-form-item--stack :deep(.arco-radio-group) {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.publish-hint--below {
  margin-top: 10px;
}

.publish-hint {
  margin: 8px 0 0;
  font-size: 11px;
  line-height: 1.5;
  color: var(--color-text-3);
  text-align: center;
}

.type-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  width: 100%;

  :deep(.arco-radio) {
    align-items: center;
    justify-content: flex-start;
    width: 100%;
    height: auto;
    min-height: 34px;
    padding: 7px 8px;
    margin-right: 0;
    font-size: 12px;
    line-height: 1.3;
    border: 1px solid var(--color-border-2);
    border-radius: 7px;
  }

  :deep(.arco-radio-checked) {
    color: rgb(var(--primary-6));
    background: rgb(var(--primary-1));
    border-color: rgb(var(--primary-4));
  }

  :deep(.arco-radio-label) {
    margin-left: 4px;
  }
}

.upload-row {
  display: flex;
  gap: 10px;
  align-items: center;
  width: 100%;
}

.upload-row__input {
  flex: 1;
  min-width: 0;
}

.upload-btn {
  flex-shrink: 0;
  color: rgb(var(--primary-6));
  background: rgb(var(--primary-1));
  border-color: rgb(var(--primary-3));
}

.preview-card {
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.preview-card__header {
  padding: 10px 12px;
  background: linear-gradient(135deg, rgb(var(--primary-1)), var(--color-fill-2));
}

.preview-tag {
  display: inline-block;
  padding: 2px 7px;
  margin-bottom: 5px;
  font-size: 10px;
  color: rgb(var(--primary-6));
  background: rgb(var(--primary-1));
  border-radius: 20px;
}

.preview-card__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-1);
}

.preview-card__sub {
  margin-top: 2px;
  font-size: 11px;
  color: var(--color-text-3);
}

.preview-card__cover {
  padding: 8px 12px 0;
  text-align: center;

  img {
    max-width: 100%;
    max-height: 80px;
    object-fit: contain;
    border-radius: 6px;
  }
}

.preview-card__body {
  flex: 1;
  min-height: 48px;
  max-height: 160px;
  padding: 10px 12px;
  overflow-y: auto;
  font-size: 11px;
  line-height: 1.6;
  color: var(--color-text-2);
  border-top: 1px solid var(--color-border-2);
  scrollbar-width: none;
  -ms-overflow-style: none;

  &::-webkit-scrollbar {
    display: none;
  }

  :deep(.preview-highlights) {
    padding-left: 1.1em;
    margin: 0 0 8px;
  }

  :deep(p) {
    margin: 0.35em 0;
  }
}

@media (max-width: 768px) {
  .notice-dbody {
    grid-template-columns: 1fr;
    max-height: none;
  }

  .notice-left-col {
    border-right: none;
    border-bottom: 1px solid var(--color-border-2);
  }
}
</style>
