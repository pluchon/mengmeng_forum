<template>
  <GiPageLayout margin>
    <a-row justify="space-between" class="g-row-tool">
      <a-space wrap>
        <GiButton type="add" @click="onAdd" />
      </a-space>
      <a-space wrap>
        <a-select
          v-model="queryParams.sortMode"
          :options="sortOptions"
          placeholder="排序"
          style="width: 148px"
          @change="search"
        />
        <a-select
          v-model="queryParams.noticeKind"
          :options="dictData.FORUM_NOTICE_KIND"
          placeholder="公告类型"
          allow-clear
          style="width: 180px"
        />
        <a-input v-model="queryParams.title" placeholder="标题关键词" allow-clear style="width: 200px" />
        <a-select
          v-model="queryParams.deleteState"
          :options="delOptions"
          placeholder="删除标记"
          allow-clear
          style="width: 120px"
        />
        <GiButton type="search" @click="search" />
        <GiButton type="reset" @click="reset" />
      </a-space>
    </a-row>

    <a-table
      class="g-table notice-table"
      row-key="id"
      :loading="loading"
      :data="tableData"
      :columns="tableColumns"
      :bordered="{ cell: true }"
      :scroll="{ x: '100%', y: '100%', minWidth: 1280 }"
      :pagination="pagination"
    />

    <NoticeFormModal v-model:visible="modalVisible" :edit-id="editingId" @success="search" />
    <AdminNoticePreviewDialog v-model:visible="previewVisible" :notice-id="previewId" />
  </GiPageLayout>
</template>

<script setup lang="tsx">
import type { TableColumnData } from '@arco-design/web-vue'
import { Message, Popconfirm, Space, Tag } from '@arco-design/web-vue'
import type { NoticeRow } from '@/apis/content/notice'
import { getNoticeList, setNoticeDeleteState, setNoticePinTop, setNoticePublishState } from '@/apis/content/notice'
import iconCheck from '@/assets/svg/对勾.svg'
import { useDict, useTable } from '@/hooks'
import AdminNoticePreviewDialog from '../components/AdminNoticePreviewDialog.vue'
import NoticeFormModal from './NoticeFormModal.vue'

defineOptions({ name: 'ContentNotice' })

const { dictData } = useDict(['FORUM_NOTICE_KIND'] as const)

type SortMode = 'id_asc' | 'updateTime_asc' | 'updateTime_desc'

const sortOptions = [
  { label: 'ID 升序', value: 'id_asc' as SortMode },
  { label: '更新时间 ↑', value: 'updateTime_asc' as SortMode },
  { label: '更新时间 ↓', value: 'updateTime_desc' as SortMode },
]

/** 公告类型对应 Tag 颜色（与字典 value 0~4 一致） */
const NOTICE_KIND_COLORS: Record<number, string> = {
  0: 'arcoblue',
  1: 'red',
  2: 'orangered',
  3: 'purple',
  4: 'cyan',
}

const delOptions = [
  { label: '未删除', value: 0 },
  { label: '已删除', value: 1 },
]

const queryParams = reactive({
  sortMode: 'id_asc' as SortMode,
  noticeKind: undefined as number | undefined,
  title: '',
  deleteState: undefined as number | undefined,
})

function parseSortMode(mode: SortMode) {
  if (mode === 'updateTime_desc')
    return { sortBy: 'updateTime', sortOrder: 'desc' }
  if (mode === 'updateTime_asc')
    return { sortBy: 'updateTime', sortOrder: 'asc' }
  return { sortBy: 'id', sortOrder: 'asc' }
}

function buildParams(page: { page: number, size: number }) {
  const q: Record<string, unknown> = {
    pageNum: page.page,
    pageSize: page.size,
    ...parseSortMode(queryParams.sortMode),
  }
  if (queryParams.noticeKind !== undefined && queryParams.noticeKind !== null)
    q.noticeKind = queryParams.noticeKind
  if (queryParams.title.trim())
    q.title = queryParams.title.trim()
  if (queryParams.deleteState !== undefined && queryParams.deleteState !== null)
    q.deleteState = queryParams.deleteState
  return q
}

const { loading, tableData, pagination, search, fixed } = useTable<NoticeRow>({
  listAPI: page => getNoticeList(buildParams(page)),
  immediate: true,
})

const reset = () => {
  queryParams.sortMode = 'id_asc'
  queryParams.noticeKind = undefined
  queryParams.title = ''
  queryParams.deleteState = undefined
  search()
}

const modalVisible = ref(false)
const editingId = ref<string | null>(null)

const previewVisible = ref(false)
const previewId = ref<string | null>(null)

function openNoticePreview(row: NoticeRow) {
  previewId.value = row.id
  previewVisible.value = true
}

const onAdd = () => {
  editingId.value = null
  modalVisible.value = true
}

const onEdit = (row: NoticeRow) => {
  editingId.value = row.id
  modalVisible.value = true
}

async function doSetDelete(row: NoticeRow, v: 0 | 1) {
  await setNoticeDeleteState({ id: row.id, deleteState: v })
  Message.success(v === 1 ? '已删除' : '已恢复')
  search()
}

async function togglePublish(row: NoticeRow, v: 0 | 1) {
  await setNoticePublishState({ id: row.id, publishState: v })
  Message.success(v === 1 ? '已发布' : '已设为草稿')
  search()
}

async function togglePin(row: NoticeRow, v: 0 | 1) {
  await setNoticePinTop({ id: row.id, pinTop: v })
  Message.success(v === 1 ? '已置顶' : '已取消置顶')
  search()
}

const kindLabel = (v: number) => {
  const o = dictData.value.FORUM_NOTICE_KIND?.find(i => Number(i.value) === v)
  return o?.label ?? String(v)
}

const kindColor = (v: number) => NOTICE_KIND_COLORS[v] ?? 'gray'

const scopeLabel = (r: NoticeRow) => {
  const s = String(r.categoryScope ?? '0')
  if (s === '0')
    return '全站'
  return `分类 ${s}`
}

function renderCheckCell(active: boolean, tip: string) {
  if (active) {
    return (
      <a-tooltip content={tip}>
        <img class="notice-check-icon" src={iconCheck} alt={tip} />
      </a-tooltip>
    )
  }
  return <span class="notice-cell-dash">—</span>
}

const tableColumns: TableColumnData[] = [
  { title: 'ID', dataIndex: 'id', width: 88 },
  {
    title: '类型',
    width: 120,
    render: ({ record }) => {
      const r = record as NoticeRow
      return (
        <Tag size="small" color={kindColor(r.noticeKind)}>
          {kindLabel(r.noticeKind)}
        </Tag>
      )
    },
  },
  {
    title: '适用范围',
    width: 108,
    render: ({ record }) => <span>{scopeLabel(record as NoticeRow)}</span>,
  },
  { title: '侧栏标识', dataIndex: 'sidebarKey', width: 140, ellipsis: true, tooltip: true },
  { title: '模板', dataIndex: 'templateId', width: 140, ellipsis: true, tooltip: true },
  { title: '标题', dataIndex: 'title', width: 180, ellipsis: true, tooltip: true },
  {
    title: '正文摘要',
    width: 200,
    ellipsis: true,
    tooltip: true,
    render: ({ record }) => {
      const r = record as NoticeRow
      const text = r.contentPreview || r.bodyPreview || '—'
      return (
        <a-link type="text" class="notice-preview-link" onClick={() => openNoticePreview(r)}>
          {text}
        </a-link>
      )
    },
  },
  {
    title: '置顶',
    width: 64,
    align: 'center',
    render: ({ record }) => renderCheckCell((record as NoticeRow).pinTop === 1, '已置顶'),
  },
  {
    title: '发布',
    width: 64,
    align: 'center',
    render: ({ record }) => renderCheckCell((record as NoticeRow).publishState === 1, '已发布'),
  },
  {
    title: '删除',
    width: 64,
    align: 'center',
    render: ({ record }) => renderCheckCell((record as NoticeRow).deleteState === 1, '已删除'),
  },
  { title: '更新时间', dataIndex: 'updateTime', width: 200 },
  {
    title: '操作',
    width: 420,
    align: 'center',
    fixed: fixed.value,
    render: ({ record }) => {
      const r = record as NoticeRow
      const pub = r.publishState === 1
      const pinned = r.pinTop === 1
      return (
        <div class="notice-actions-wrap" role="presentation" onClick={(e: Event) => e.stopPropagation()}>
          <Space>
            <GiButton type="edit" size="mini" onClick={() => onEdit(r)} />
            {pinned ? (
              <Popconfirm content="取消置顶？" onBeforeOk={() => togglePin(r, 0)}>
                <GiButton size="mini">取消置顶</GiButton>
              </Popconfirm>
            ) : (
              <Popconfirm content="置顶该公告？" onBeforeOk={() => togglePin(r, 1)}>
                <GiButton size="mini">置顶</GiButton>
              </Popconfirm>
            )}
            <Popconfirm content="设为草稿？" onBeforeOk={() => togglePublish(r, 0)}>
              <GiButton size="mini" disabled={!pub}>
                草稿
              </GiButton>
            </Popconfirm>
            <Popconfirm content="立即发布？" onBeforeOk={() => togglePublish(r, 1)}>
              <GiButton size="mini" disabled={pub}>
                发布
              </GiButton>
            </Popconfirm>
            {r.deleteState === 1 ? (
              <Popconfirm content="恢复该公告？" onBeforeOk={() => doSetDelete(r, 0)}>
                <GiButton size="mini">恢复</GiButton>
              </Popconfirm>
            ) : (
              <Popconfirm content="确认删除该公告？" onBeforeOk={() => doSetDelete(r, 1)}>
                <GiButton type="delete" size="mini" />
              </Popconfirm>
            )}
          </Space>
        </div>
      )
    },
  },
]
</script>

<style lang="scss" scoped>
.notice-preview-link {
  display: inline;
  max-width: 100%;
}

:deep(.notice-check-icon) {
  display: block;
  width: 20px;
  height: 20px;
  margin: 0 auto;
  object-fit: contain;
}

:deep(.notice-cell-dash) {
  font-size: 13px;
  color: var(--color-text-4);
}
</style>
