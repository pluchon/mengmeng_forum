<template>
  <GiPageLayout margin>
    <a-row justify="space-between" class="g-row-tool">
      <a-space wrap>
        <a-input v-model="queryParams.title" placeholder="标题关键词" allow-clear style="width: 200px" />
        <a-input v-model="queryParams.boardId" placeholder="版块 ID" allow-clear style="width: 120px" />
        <a-select
          v-model="queryParams.status"
          :options="statusOptions"
          placeholder="发布状态"
          allow-clear
          style="width: 140px"
        />
        <a-select v-model="queryParams.state" :options="stateOptions" placeholder="审核禁用" allow-clear style="width: 120px" />
        <a-select v-model="queryParams.deleteState" :options="delOptions" placeholder="删除标记" allow-clear style="width: 120px" />
        <GiButton type="search" @click="search" />
        <GiButton type="reset" @click="reset" />
      </a-space>
    </a-row>

    <a-table
      class="g-table article-table"
      row-key="id"
      :loading="loading"
      :data="tableData"
      :columns="tableColumns"
      :bordered="{ cell: true }"
      :scroll="{ x: '100%', y: '100%', minWidth: 1180 }"
      :pagination="pagination"
    />

    <AdminArticlePreviewModal v-model:visible="articlePreviewVisible" :article-id="articlePreviewId" />
    <AdminForumMemberDrawer v-model:visible="memberDrawerVisible" :user-id="memberUserId" />
  </GiPageLayout>
</template>

<script setup lang="tsx">
import type { TableColumnData } from '@arco-design/web-vue'
import { Message, Popconfirm, Space } from '@arco-design/web-vue'
import type { ArticleRow } from '@/apis/content/article'
import { getArticleList, setArticleDeleteState, setArticleState } from '@/apis/content/article'
import { useTable } from '@/hooks'
import AdminArticlePreviewModal from '../components/AdminArticlePreviewModal.vue'
import ArticleStatusIcon from '../components/ArticleStatusIcon.vue'
import AdminForumMemberDrawer from '../components/AdminForumMemberDrawer.vue'

defineOptions({ name: 'ContentArticle' })

const articlePreviewVisible = ref(false)
const articlePreviewId = ref<string | null>(null)
const memberDrawerVisible = ref(false)
const memberUserId = ref<string | null>(null)

function openArticlePreview(id: string) {
  articlePreviewId.value = id
  articlePreviewVisible.value = true
}

function openMemberDrawer(userId: string) {
  memberUserId.value = userId
  memberDrawerVisible.value = true
}

const statusOptions = [
  { label: '草稿', value: 0 },
  { label: '审核中', value: 1 },
  { label: '审核通过', value: 2 },
  { label: '未通过', value: 3 },
  { label: '异常', value: 4 },
  { label: '已发布', value: 5 }
]
const stateOptions = [
  { label: '正常', value: 0 },
  { label: '禁用', value: 1 }
]
const delOptions = [
  { label: '未删', value: 0 },
  { label: '已删', value: 1 }
]

const queryParams = reactive({
  title: '',
  boardId: '',
  status: undefined as number | undefined,
  state: undefined as number | undefined,
  deleteState: undefined as number | undefined
})

function buildListParams(page: { page: number, size: number }) {
  const q: Record<string, unknown> = {
    pageNum: page.page,
    pageSize: page.size
  }
  if (queryParams.title.trim())
    q.title = queryParams.title.trim()
  if (queryParams.boardId.trim()) {
    const n = Number(queryParams.boardId)
    if (!Number.isNaN(n))
      q.boardId = n
  }
  if (queryParams.status !== undefined && queryParams.status !== null)
    q.status = queryParams.status
  if (queryParams.state !== undefined && queryParams.state !== null)
    q.state = queryParams.state
  if (queryParams.deleteState !== undefined && queryParams.deleteState !== null)
    q.deleteState = queryParams.deleteState
  return q
}

const { loading, tableData, pagination, search, fixed } = useTable<ArticleRow>({
  listAPI: page => getArticleList(buildListParams(page)),
  immediate: true
})

const reset = () => {
  queryParams.title = ''
  queryParams.boardId = ''
  queryParams.status = undefined
  queryParams.state = undefined
  queryParams.deleteState = undefined
  search()
}

async function doSetDelete(row: ArticleRow, v: 0 | 1) {
  await setArticleDeleteState({ id: row.id, deleteState: v })
  Message.success(v === 1 ? '已标记删除' : '已恢复')
  search()
}

async function doSetState(row: ArticleRow, v: 0 | 1) {
  await setArticleState({ id: row.id, state: v })
  Message.success(v === 1 ? '已禁用展示' : '已恢复展示')
  search()
}

const colCenter = { align: 'center' as const }

const tableColumns: TableColumnData[] = [
  { title: 'ID', dataIndex: 'id', width: 88, ...colCenter },
  {
    title: '标题',
    width: 220,
    ellipsis: true,
    tooltip: true,
    ...colCenter,
    render: ({ record }) => {
      const r = record as ArticleRow
      return (
        <a-link type="primary" onClick={() => openArticlePreview(r.id)}>
          {r.title}
        </a-link>
      )
    }
  },
  { title: '版块', dataIndex: 'boardName', width: 100, ...colCenter },
  {
    title: '作者',
    width: 120,
    ...colCenter,
    render: ({ record }) => {
      const r = record as ArticleRow
      const label = r.nickname || r.username || '—'
      return (
        <a-link type="text" onClick={() => openMemberDrawer(r.userId)}>
          {label}
        </a-link>
      )
    }
  },
  {
    title: '状态',
    width: 88,
    ...colCenter,
    render: ({ record }) => {
      const r = record as ArticleRow
      return (
        <div class="article-table__status">
          <ArticleStatusIcon status={r.status} state={r.state} deleteState={r.deleteState} />
        </div>
      )
    }
  },
  { title: '浏览', dataIndex: 'visitCount', width: 72, ...colCenter },
  { title: '创建时间', dataIndex: 'createTime', width: 170, ...colCenter },
  {
    title: '操作',
    width: 220,
    ...colCenter,
    fixed: fixed.value,
    render: ({ record }) => {
      const r = record as ArticleRow
      const deleted = r.deleteState === 1
      const disabled = r.state === 1
      return (
        <Space>
          {deleted ? (
            <Popconfirm content="恢复删除？" onBeforeOk={() => doSetDelete(r, 0)}>
              <GiButton size="mini">恢复删除</GiButton>
            </Popconfirm>
          ) : (
            <Popconfirm content="标记为已删除？" onBeforeOk={() => doSetDelete(r, 1)}>
              <GiButton type="delete" size="mini" />
            </Popconfirm>
          )}
          {disabled ? (
            <Popconfirm content="恢复帖子展示？" onBeforeOk={() => doSetState(r, 0)}>
              <GiButton size="mini">恢复展示</GiButton>
            </Popconfirm>
          ) : (
            <Popconfirm content="禁用帖子展示？" onBeforeOk={() => doSetState(r, 1)}>
              <GiButton size="mini" status="danger">
                禁用
              </GiButton>
            </Popconfirm>
          )}
        </Space>
      )
    }
  }
]
</script>

<style lang="scss" scoped>
.article-table {
  :deep(.arco-table-th),
  :deep(.arco-table-td) {
    text-align: center;
  }

  :deep(.arco-table-cell) {
    justify-content: center;
  }

  &__status {
    display: inline-flex;
    justify-content: center;
    width: 100%;
  }
}
</style>
