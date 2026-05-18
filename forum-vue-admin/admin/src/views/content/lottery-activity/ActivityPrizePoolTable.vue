<template>
  <div class="prize-pool-table-wrap">
    <a-table
      :data="tableRows"
      :columns="columns"
      :pagination="false"
      size="small"
      :bordered="{ cell: true }"
      row-key="rowKey"
      class="prize-pool-table"
    />
    <div v-if="showAddRow" class="prize-pool-table__add">
      <a-button type="dashed" long size="small" @click="emit('add')">
        添加奖项
      </a-button>
    </div>
  </div>
</template>

<script setup lang="tsx">
import type { TableColumnData } from '@arco-design/web-vue'
import type { LotteryPrizeOption } from '@/apis/content/lotteryPrize'
import iconDeleted from '@/assets/svg/已删除.svg'
import type { ActivityPrizeLineForm } from './lotteryActivityShared'

const props = withDefaults(
  defineProps<{
    lines: ActivityPrizeLineForm[]
    prizeOptions: LotteryPrizeOption[]
    showAddRow?: boolean
  }>(),
  { showAddRow: true },
)

const emit = defineEmits<{
  (e: 'add'): void
  (e: 'edit', index: number): void
  (e: 'remove', index: number): void
}>()

function lineTitle(line: ActivityPrizeLineForm) {
  const opt = props.prizeOptions.find(p => String(p.id) === line.prizeId)
  return opt?.name ?? (line.prizeId ? `奖品 #${line.prizeId}` : '未选择')
}

function stockText(v: number) {
  return v === -1 ? '不限' : String(v)
}

const tableRows = computed(() =>
  props.lines.map((line, idx) => ({
    ...line,
    rowKey: line.activityPrizeId ?? `idx-${idx}-${line.prizeId}`,
    _idx: idx,
  })),
)

const columns: TableColumnData[] = [
  {
    title: '奖品名称',
    ellipsis: true,
    tooltip: true,
    render: ({ record }) => lineTitle(record as ActivityPrizeLineForm),
  },
  {
    title: '概率(%)',
    width: 100,
    align: 'center',
    render: ({ record }) => {
      const p = (record as ActivityPrizeLineForm).probabilityPercent
      return p != null ? `${p}%` : '—'
    },
  },
  {
    title: '库存',
    width: 100,
    align: 'center',
    render: ({ record }) => stockText((record as ActivityPrizeLineForm).stockRemaining),
  },
  {
    title: '操作',
    width: 120,
    align: 'center',
    render: ({ record }) => {
      const idx = (record as ActivityPrizeLineForm & { _idx: number })._idx
      return (
        <div class="pool-table-actions">
          <a-button type="text" size="mini" onClick={() => emit('edit', idx)}>
            编辑
          </a-button>
          <a-button type="text" size="mini" class="pool-table-actions__del" onClick={() => emit('remove', idx)}>
            <img class="pool-table-icon-del" src={iconDeleted} alt="删除" />
          </a-button>
        </div>
      )
    },
  },
]

</script>

<style scoped lang="scss">
.prize-pool-table-wrap {
  width: 100%;
}

.prize-pool-table {
  width: 100%;

  :deep(.arco-table-th),
  :deep(.arco-table-td) {
    text-align: center;
  }

  :deep(.arco-table-th:first-child),
  :deep(.arco-table-td:first-child) {
    text-align: left;
  }
}

.prize-pool-table__add {
  margin-top: 10px;
}

:deep(.pool-table-actions) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

:deep(.pool-table-icon-del) {
  display: block;
  width: 16px;
  height: 16px;
  object-fit: contain;
}

:deep(.pool-table-actions__del) {
  padding: 0 4px;
}
</style>
