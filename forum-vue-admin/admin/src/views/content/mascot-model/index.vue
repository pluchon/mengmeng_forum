<template>
  <GiPageLayout margin>
    <a-row justify="space-between" class="g-row-tool g-mb">
      <a-space wrap>
        <a-input v-model="queryParams.name" placeholder="按名称搜索" allow-clear style="width: 180px" />
        <a-select v-model="queryParams.shelfStatus" :options="shelfOptions" placeholder="上架状态" allow-clear style="width: 120px" />
        <a-select v-model="queryParams.deleteState" :options="delOptions" placeholder="删除" allow-clear style="width: 100px" />
        <GiButton type="search" @click="search" />
        <GiButton type="reset" @click="reset" />
      </a-space>
    </a-row>

    <a-table
      row-key="id"
      class="g-table mascot-model-table"
      :loading="loading"
      :data="tableData"
      :columns="columns"
      :pagination="pagination"
      :bordered="{ cell: true }"
      :scroll="{ x: '100%' }"
    />

    <a-modal
      v-model:visible="modalVisible"
      title="编辑模型"
      width="720px"
      :ok-loading="saveLoading"
      :on-before-ok="submitSave"
    >
      <a-form :model="form" layout="vertical">
        <a-form-item label="模型名称" required>
          <a-input v-model="form.code" disabled />
        </a-form-item>
        <a-form-item label="模型相对路径" required>
          <a-input
            v-model="form.modelRelPath"
            placeholder="如 live2d_3/model/Azue Lane(JP)/lafei/lafei.model3.json"
          />
        </a-form-item>
        <a-row :gutter="12">
          <a-col :span="8">
            <a-form-item label="缩放">
              <a-input-number v-model="form.modelScale" :min="0.01" :step="0.01" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="posX / posY">
              <a-space>
                <a-input-number v-model="form.posX" style="width: 90px" />
                <a-input-number v-model="form.posY" style="width: 90px" />
              </a-space>
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="舞台宽/高">
              <a-space>
                <a-input-number v-model="form.stageWidth" :min="100" style="width: 90px" />
                <a-input-number v-model="form.stageHeight" :min="100" style="width: 90px" />
              </a-space>
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="上架状态">
          <a-select v-model="form.shelfStatus" :options="shelfOptionsAll" />
        </a-form-item>
      </a-form>
    </a-modal>
  </GiPageLayout>
</template>

<script setup lang="tsx">
import type { TableColumnData } from '@arco-design/web-vue'
import { Message } from '@arco-design/web-vue'
import type { MascotModelRow } from '@/apis/content/mascotModel'
import {
  getMascotModelList,
  saveMascotModel,
  setMascotModelDelete,
  setMascotModelShelf,
} from '@/apis/content/mascotModel'
import { useTable } from '@/hooks'
import MascotModelRowActions from './MascotModelRowActions.vue'
import MascotShelfIcon from './MascotShelfIcon.vue'

defineOptions({ name: 'ContentMascotModel' })

const shelfOptions = [
  { label: '草稿', value: 0 },
  { label: '上架', value: 1 },
  { label: '下架', value: 2 },
]
const shelfOptionsAll = shelfOptions
const delOptions = [
  { label: '未删', value: 0 },
  { label: '已删', value: 1 },
]

const colCenter = { align: 'center' as const }

const queryParams = reactive({
  name: '',
  shelfStatus: undefined as number | undefined,
  deleteState: undefined as number | undefined,
})

function buildParams(page: { page: number, size: number }) {
  const q: Record<string, unknown> = {
    pageNum: page.page,
    pageSize: page.size,
  }
  const name = queryParams.name.trim()
  if (name)
    q.keyword = name
  if (queryParams.shelfStatus != null)
    q.shelfStatus = queryParams.shelfStatus
  if (queryParams.deleteState != null)
    q.deleteState = queryParams.deleteState
  return q
}

const { loading, tableData, pagination, search } = useTable<MascotModelRow>({
  listAPI: page => getMascotModelList(buildParams(page)),
  immediate: true,
})

const reset = () => {
  queryParams.name = ''
  queryParams.shelfStatus = undefined
  queryParams.deleteState = undefined
  search()
}

const modalVisible = ref(false)
const saveLoading = ref(false)
const form = reactive({
  id: undefined as string | undefined,
  code: '',
  name: '',
  modelRelPath: '',
  modelScale: 0.1,
  posX: 0,
  posY: 72,
  stageWidth: 260,
  stageHeight: 320,
  shelfStatus: 0,
})

function openEdit(row: MascotModelRow) {
  form.id = row.id
  form.code = row.code
  form.name = row.name
  form.modelRelPath = row.modelRelPath
  form.modelScale = row.modelScale
  form.posX = row.posX
  form.posY = row.posY
  form.stageWidth = row.stageWidth
  form.stageHeight = row.stageHeight
  form.shelfStatus = row.shelfStatus
  modalVisible.value = true
}

async function submitSave(): Promise<boolean> {
  if (!form.code.trim() || !form.modelRelPath.trim()) {
    Message.warning('请填写模型名称与路径')
    return false
  }
  saveLoading.value = true
  try {
    const code = form.code.trim()
    const body: Record<string, unknown> = {
      code,
      name: form.name.trim() || code,
      modelRelPath: form.modelRelPath.trim(),
      modelScale: form.modelScale,
      posX: form.posX,
      posY: form.posY,
      stageWidth: form.stageWidth,
      stageHeight: form.stageHeight,
      shelfStatus: form.shelfStatus,
      sortOrder: 0,
    }
    if (form.id)
      body.id = Number(form.id)
    await saveMascotModel(body)
    Message.success('已保存')
    modalVisible.value = false
    search()
    return true
  }
  catch {
    return false
  }
  finally {
    saveLoading.value = false
  }
}

async function doShelf(row: MascotModelRow, st: number) {
  await setMascotModelShelf({ id: row.id, shelfStatus: st })
  Message.success(st === 1 ? '已上架' : '已下架')
  search()
}

async function doDelete(row: MascotModelRow, v: 0 | 1) {
  await setMascotModelDelete({ id: row.id, deleteState: v })
  Message.success(v === 1 ? '已标记删除' : '已恢复')
  search()
}

const columns: TableColumnData[] = [
  { title: 'ID', dataIndex: 'id', width: 80, ...colCenter },
  { title: '模型名称', dataIndex: 'code', minWidth: 240, ellipsis: true, tooltip: true, ...colCenter },
  {
    title: '上架',
    dataIndex: 'shelfStatus',
    width: 88,
    ...colCenter,
    render: ({ record }) => (
      <MascotShelfIcon shelfStatus={(record as MascotModelRow).shelfStatus} />
    ),
  },
  {
    title: '操作',
    width: 260,
    fixed: 'right',
    ...colCenter,
    render: ({ record }) => {
      const r = record as MascotModelRow
      return (
        <MascotModelRowActions
          row={r}
          onEdit={() => openEdit(r)}
          onShelf={(st) => { void doShelf(r, st) }}
          onDelete={() => { void doDelete(r, 1) }}
          onRestore={() => { void doDelete(r, 0) }}
        />
      )
    },
  },
]
</script>

<style lang="scss" scoped>
.mascot-model-table {
  :deep(.arco-table-th),
  :deep(.arco-table-td) {
    text-align: center;
  }

  :deep(.arco-table-cell) {
    justify-content: center;
  }

  :deep(.arco-table-td:last-child .arco-table-cell) {
    padding: 8px 12px;
  }
}
</style>
