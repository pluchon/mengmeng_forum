<template>
  <GiPageLayout margin class="lottery-prize-page">
    <div class="prize-toolbar">
      <a-space wrap :size="8">
        <a-input
          v-model="queryParams.keyword"
          placeholder="奖品名称"
          allow-clear
          class="prize-toolbar__input"
        />
        <a-select
          v-model="queryParams.prizeType"
          :options="prizeTypeFilterOptions"
          placeholder="全部类型"
          allow-clear
          class="prize-toolbar__select"
        />
        <a-select
          v-model="queryParams.catalogStatus"
          :options="shelfStatusOptions"
          placeholder="全部状态"
          allow-clear
          class="prize-toolbar__select"
        />
        <a-select
          v-model="queryParams.deleteState"
          :options="delOptions"
          placeholder="删除标记"
          allow-clear
          class="prize-toolbar__select"
        />
        <GiButton type="search" @click="search" />
        <GiButton type="reset" @click="reset" />
      </a-space>
      <div class="prize-toolbar__sep" />
      <GiButton type="add" @click="openCreate">
        新建奖品
      </GiButton>
    </div>

    <a-table
      row-key="id"
      class="g-table prize-table"
      :loading="loading"
      :data="tableData"
      :columns="columns"
      :pagination="pagination"
      :bordered="{ cell: true }"
      :scroll="{ x: '100%', y: '100%', minWidth: 1100 }"
    />

    <LotteryPrizeFormModal v-model:visible="modalVisible" :edit-id="editingId" @success="search" />
  </GiPageLayout>
</template>

<script setup lang="tsx">
import type { TableColumnData } from '@arco-design/web-vue'
import { Message, Modal, Popconfirm, Space, Tag } from '@arco-design/web-vue'
import type { LotteryPrizeCatalogRow } from '@/apis/content/lotteryPrize'
import {
  getLotteryPrizeList,
  setLotteryPrizeCatalogStatus,
  setLotteryPrizeDeleteState,
} from '@/apis/content/lotteryPrize'
import iconDeleted from '@/assets/svg/已删除.svg'
import { useTable } from '@/hooks'
import LotteryPrizeFormModal from './LotteryPrizeFormModal.vue'

defineOptions({ name: 'ContentLotteryPrize' })

const PRIZE_TYPE_LABELS: Record<number, string> = {
  0: '谢谢',
  1: '大奖',
  2: '小奖',
  3: '安慰',
  4: '积分',
  5: 'VIP天',
}

/** 类型 Tag 颜色，参考 prize_management_ui.html */
const PRIZE_TYPE_COLORS: Record<number, string> = {
  0: 'gray',
  1: 'magenta',
  2: 'green',
  3: 'purple',
  4: 'orangered',
  5: 'arcoblue',
}

function typeLabel(row: LotteryPrizeCatalogRow) {
  if (row.prizeType === 1 && row.isMysteryBundle === 1)
    return '神秘大奖'
  return PRIZE_TYPE_LABELS[row.prizeType] ?? String(row.prizeType)
}

function typeColor(row: LotteryPrizeCatalogRow) {
  if (row.prizeType === 1 && row.isMysteryBundle === 1)
    return 'magenta'
  return PRIZE_TYPE_COLORS[row.prizeType] ?? 'gray'
}

const prizeTypeFilterOptions = Object.entries(PRIZE_TYPE_LABELS).map(([v, l]) => ({
  label: l,
  value: Number(v),
}))

const shelfStatusOptions = [
  { label: '已上架', value: 1 },
  { label: '已下架', value: 2 },
]

const delOptions = [
  { label: '未删除', value: 0 },
  { label: '已删除', value: 1 },
]

const queryParams = reactive({
  keyword: '',
  prizeType: undefined as number | undefined,
  catalogStatus: undefined as number | undefined,
  deleteState: undefined as number | undefined,
})

function buildParams(page: { page: number, size: number }) {
  const q: Record<string, unknown> = {
    pageNum: page.page,
    pageSize: page.size,
  }
  if (queryParams.keyword.trim())
    q.keyword = queryParams.keyword.trim()
  if (queryParams.prizeType != null)
    q.prizeType = queryParams.prizeType
  if (queryParams.catalogStatus != null)
    q.catalogStatus = queryParams.catalogStatus
  if (queryParams.deleteState != null)
    q.deleteState = queryParams.deleteState
  return q
}

const { loading, tableData, pagination, search } = useTable<LotteryPrizeCatalogRow>({
  listAPI: page => getLotteryPrizeList(buildParams(page)),
  immediate: true,
})

const reset = () => {
  queryParams.keyword = ''
  queryParams.prizeType = undefined
  queryParams.catalogStatus = undefined
  queryParams.deleteState = undefined
  search()
}

const modalVisible = ref(false)
const editingId = ref<string | null>(null)

function openCreate() {
  editingId.value = null
  modalVisible.value = true
}

function openEdit(id: string) {
  editingId.value = id
  modalVisible.value = true
}

function formatStock(stock: number | undefined | null) {
  if (stock == null || stock === -1)
    return '不限'
  return String(stock)
}

function formatPrizeValue(row: LotteryPrizeCatalogRow) {
  if (row.prizeType === 4)
    return String(row.prizeValue ?? 0)
  return '—'
}

async function toggleShelf(row: LotteryPrizeCatalogRow) {
  const onShelf = row.catalogStatus === 1
  const next = onShelf ? 2 : 1
  await setLotteryPrizeCatalogStatus({ id: row.id, catalogStatus: next })
  Message.success(onShelf ? '已下架' : '已上架')
  search()
}

async function doSetDelete(row: LotteryPrizeCatalogRow, v: 0 | 1) {
  await setLotteryPrizeDeleteState({ id: row.id, deleteState: v })
  Message.success(v === 1 ? '已删除' : '已恢复')
  search()
}

function confirmDelete(row: LotteryPrizeCatalogRow) {
  Modal.confirm({
    title: '确认删除该奖品？',
    content: '删除后可在「已删除」筛选中恢复。',
    onOk: () => doSetDelete(row, 1),
  })
}

const columns: TableColumnData[] = [
  { title: 'ID', dataIndex: 'id', width: 64, align: 'center' },
  {
    title: '名称',
    dataIndex: 'name',
    width: 160,
    align: 'center',
    ellipsis: true,
    tooltip: true,
    render: ({ record }) => {
      const r = record as LotteryPrizeCatalogRow
      const isMystery = r.isMysteryBundle === 1
      if (!isMystery)
        return <span class="prize-name">{r.name}</span>
      return (
        <a-tooltip content="神秘奖品">
          <span class="prize-name prize-name--mystery">{r.name}</span>
        </a-tooltip>
      )
    },
  },
  {
    title: '类型',
    width: 108,
    align: 'center',
    render: ({ record }) => {
      const r = record as LotteryPrizeCatalogRow
      return (
        <Tag size="small" color={typeColor(r)}>
          {typeLabel(r)}
        </Tag>
      )
    },
  },
  {
    title: '数值',
    width: 72,
    align: 'center',
    render: ({ record }) => <span>{formatPrizeValue(record as LotteryPrizeCatalogRow)}</span>,
  },
  {
    title: '库存',
    width: 88,
    align: 'center',
    render: ({ record }) => <span>{formatStock((record as LotteryPrizeCatalogRow).stockQuantity)}</span>,
  },
  {
    title: '操作',
    width: 200,
    align: 'center',
    fixed: 'right',
    render: ({ record }) => {
      const r = record as LotteryPrizeCatalogRow
      const onShelf = r.catalogStatus === 1
      return (
        <div class="prize-actions" role="presentation" onClick={(e: Event) => e.stopPropagation()}>
          <Space size={4}>
            <a-button type="text" size="mini" onClick={() => openEdit(r.id)}>
              编辑
            </a-button>
            <Popconfirm
              content={onShelf ? '确认下架该奖品？' : '确认上架该奖品？'}
              onBeforeOk={async () => {
                await toggleShelf(r)
                return true
              }}
            >
              <a-button type="text" size="mini">
                {onShelf ? '下架' : '上架'}
              </a-button>
            </Popconfirm>
            {r.deleteState === 1 ? (
              <Popconfirm content="恢复该奖品？" onBeforeOk={async () => { await doSetDelete(r, 0); return true }}>
                <a-button type="text" size="mini">恢复</a-button>
              </Popconfirm>
            ) : (
              <a-tooltip content="删除">
                <a-button type="text" size="mini" class="act-btn-del" onClick={() => confirmDelete(r)}>
                  <img class="act-icon-del" src={iconDeleted} alt="删除" />
                </a-button>
              </a-tooltip>
            )}
          </Space>
        </div>
      )
    },
  },
]
</script>

<style scoped lang="scss">
.lottery-prize-page {
  :deep(.gi-page-layout__body) {
    scrollbar-width: none;
    -ms-overflow-style: none;

    &::-webkit-scrollbar {
      display: none;
      width: 0;
      height: 0;
    }
  }
}

.prize-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}

.prize-toolbar__input {
  width: 160px;
}

.prize-toolbar__select {
  width: 130px;
}

.prize-toolbar__sep {
  flex: 1;
  min-width: 12px;
}

:deep(.prize-name--mystery) {
  font-weight: 500;
  color: rgb(var(--primary-6));
  cursor: default;
}

.prize-table {
  :deep(.arco-table-th),
  :deep(.arco-table-td) {
    text-align: center;
  }

  :deep(.arco-table-cell) {
    justify-content: center;
  }
}

.prize-actions {
  display: inline-flex;
  justify-content: center;
}

:deep(.act-icon-del) {
  display: block;
  width: 18px;
  height: 18px;
  object-fit: contain;
}

:deep(.act-btn-del) {
  padding: 0 4px;
}
</style>
