<template>
  <GiPageLayout margin>
    <a-row justify="space-between" class="g-row-tool">
      <a-space wrap>
        <a-input v-model="queryParams.articleId" placeholder="帖子 ID" allow-clear style="width: 140px" />
        <a-input v-model="queryParams.contentKeyword" placeholder="内容关键词" allow-clear style="width: 200px" />
        <a-select v-model="queryParams.state" :options="stateOptions" placeholder="审核禁用" allow-clear style="width: 120px" />
        <a-select v-model="queryParams.deleteState" :options="delOptions" placeholder="删除标记" allow-clear style="width: 120px" />
        <GiButton type="search" @click="search" />
        <GiButton type="reset" @click="reset" />
      </a-space>
    </a-row>

    <a-table
      class="g-table"
      row-key="id"
      :loading="loading"
      :data="tableData"
      :columns="tableColumns"
      :bordered="{ cell: true }"
      :scroll="{ x: '100%', y: '100%', minWidth: 1180 }"
      :pagination="pagination"
    />

    <AdminForumMemberDrawer v-model:visible="memberDrawerVisible" :user-id="memberUserId" />
  </GiPageLayout>
</template>

<script setup lang="tsx">
import type { TableColumnData } from '@arco-design/web-vue'
import { Message, Popconfirm, Space, Tag } from '@arco-design/web-vue'
import type { ReplyRow } from '@/apis/content/reply'
import { getReplyList, setReplyDeleteState, setReplyState } from '@/apis/content/reply'
import { useTable } from '@/hooks'
import { useRoute, useRouter } from 'vue-router'
import AdminForumMemberDrawer from '../components/AdminForumMemberDrawer.vue'

defineOptions({ name: 'ContentReply' })

const route = useRoute()
const router = useRouter()

const memberDrawerVisible = ref(false)
const memberUserId = ref<string | null>(null)

function goReplyListForArticle(articleId: string) {
  const q = { articleId: String(articleId) }
  if (route.path === '/content/reply')
    void router.replace({ path: '/content/reply', query: q })
  else
    void router.push({ path: '/content/reply', query: q })
}

function openMemberDrawer(userId: string) {
  memberUserId.value = userId
  memberDrawerVisible.value = true
}

const stateOptions = [
  { label: '正常', value: 0 },
  { label: '禁用', value: 1 }
]
const delOptions = [
  { label: '未删', value: 0 },
  { label: '已删', value: 1 }
]

const queryParams = reactive({
  articleId: '',
  contentKeyword: '',
  state: undefined as number | undefined,
  deleteState: undefined as number | undefined
})

function buildListParams(page: { page: number, size: number }) {
  const q: Record<string, unknown> = {
    pageNum: page.page,
    pageSize: page.size
  }
  if (queryParams.articleId.trim()) {
    const n = Number(queryParams.articleId)
    if (!Number.isNaN(n))
      q.articleId = n
  }
  if (queryParams.contentKeyword.trim())
    q.contentKeyword = queryParams.contentKeyword.trim()
  if (queryParams.state !== undefined && queryParams.state !== null)
    q.state = queryParams.state
  if (queryParams.deleteState !== undefined && queryParams.deleteState !== null)
    q.deleteState = queryParams.deleteState
  return q
}

const { loading, tableData, pagination, search, fixed } = useTable<ReplyRow>({
  listAPI: page => getReplyList(buildListParams(page)),
  immediate: true
})

function applyRouteArticleQuery() {
  const raw = route.query.articleId
  const s = raw == null ? '' : Array.isArray(raw) ? String(raw[0] ?? '') : String(raw)
  if (s.trim()) {
    queryParams.articleId = s.trim()
    search()
  }
}

onMounted(() => {
  applyRouteArticleQuery()
})

watch(
  () => route.query.articleId,
  () => {
    applyRouteArticleQuery()
  }
)

const reset = () => {
  queryParams.articleId = ''
  queryParams.contentKeyword = ''
  queryParams.state = undefined
  queryParams.deleteState = undefined
  search()
}

async function doSetDelete(row: ReplyRow, v: 0 | 1) {
  await setReplyDeleteState({ id: row.id, deleteState: v })
  Message.success(v === 1 ? '已标记删除' : '已恢复')
  search()
}

async function doSetState(row: ReplyRow, v: 0 | 1) {
  await setReplyState({ id: row.id, state: v })
  Message.success(v === 1 ? '已禁用展示' : '已恢复展示')
  search()
}

const tableColumns: TableColumnData[] = [
  { title: 'ID', dataIndex: 'id', width: 88 },
  {
    title: '帖子',
    width: 200,
    render: ({ record }) => {
      const r = record as ReplyRow
      return (
        <Space size={4}>
          <span>ID {r.articleId}</span>
          <a-link type="primary" onClick={() => goReplyListForArticle(r.articleId)}>
            查看该帖评论
          </a-link>
        </Space>
      )
    }
  },
  {
    title: '用户',
    width: 120,
    render: ({ record }) => {
      const r = record as ReplyRow
      const label = r.nickname || r.username || '—'
      return (
        <a-link type="text" onClick={() => openMemberDrawer(r.postUserId)}>
          {label}
        </a-link>
      )
    }
  },
  { title: '内容', dataIndex: 'contentPreview', width: 300, ellipsis: true, tooltip: true },
  {
    title: '禁用',
    width: 72,
    align: 'center',
    render: ({ record }) => <Tag color={(record as ReplyRow).state === 1 ? 'red' : 'green'}>{(record as ReplyRow).state}</Tag>
  },
  {
    title: '删除',
    width: 72,
    align: 'center',
    render: ({ record }) => <Tag color={(record as ReplyRow).deleteState === 1 ? 'red' : 'gray'}>{(record as ReplyRow).deleteState}</Tag>
  },
  { title: '创建时间', dataIndex: 'createTime', width: 170 },
  {
    title: '操作',
    width: 220,
    align: 'center',
    fixed: fixed.value,
    render: ({ record }) => {
      const r = record as ReplyRow
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
            <Popconfirm content="恢复回复展示？" onBeforeOk={() => doSetState(r, 0)}>
              <GiButton size="mini">恢复展示</GiButton>
            </Popconfirm>
          ) : (
            <Popconfirm content="禁用回复展示？" onBeforeOk={() => doSetState(r, 1)}>
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
