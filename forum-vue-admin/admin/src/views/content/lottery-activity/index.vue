<template>
  <GiPageLayout margin>
    <a-row justify="space-between" align="center" class="g-row-tool act-toolbar-row" :wrap="false">
      <a-col flex="auto">
        <a-space :size="8" wrap>
      <a-input
        v-model="queryParams.title"
        placeholder="活动标题"
        allow-clear
        class="act-toolbar__input"
        @press-enter="search"
        @clear="search"
      />
      <a-select
        v-model="queryParams.activityFilter"
        :options="filterOptions"
        placeholder="活动状态"
        class="act-toolbar__select"
        @change="search"
      />
      <a-select
        v-model="queryParams.sortMode"
        :options="sortOptions"
        placeholder="排序"
        class="act-toolbar__select act-toolbar__select--sort"
        @change="search"
      />
          <GiButton type="reset" @click="reset" />
        </a-space>
      </a-col>
      <a-col flex="none">
        <a-button type="primary" @click="openCreate">
          新建活动
        </a-button>
      </a-col>
    </a-row>

    <div class="act-chrome-ring">
      <div class="act-chrome-inner">
        <div class="act-split">
        <div class="act-split__left">
          <div class="act-split__panel-head">
            <span class="act-split__panel-title">活动列表</span>
            <span class="act-split__panel-count">共 {{ pagination.total }} 条</span>
          </div>
          <a-spin :loading="loading" class="act-item-list-wrap">
            <a-empty v-if="!loading && !tableData.length" description="暂无活动" />
            <ul v-else class="act-item-list">
              <li
                v-for="row in tableData"
                :key="row.id"
                class="act-item"
                :class="{ 'act-item--active': selectedId === row.id }"
                @click="selectActivity(row)"
              >
                <div class="act-item__main">
                  <div class="act-item__title">{{ row.title }}</div>
                  <div class="act-item__meta">
                    <a-tag size="small" :color="phaseTagColor(row.phase)">
                      {{ phaseLabel(row.phase) }}
                    </a-tag>
                    <a-tag v-if="row.deleteState === 1" size="small" color="red">
                      已删除
                    </a-tag>
                  </div>
                </div>
                <a-button type="outline" size="mini" @click.stop="openListAction(row)">
                  操作
                </a-button>
              </li>
            </ul>
          </a-spin>
          <div v-if="pagination.total > 0" class="act-list-pager">
            <a-pagination
              :current="pagination.current"
              :total="pagination.total"
              :page-size="LIST_PAGE_SIZE"
              :show-page-size="false"
              :show-total="true"
              size="small"
              @change="onListPageChange"
            />
          </div>
        </div>

        <div class="act-split__right">
          <template v-if="!selectedId">
            <a-empty description="请从左侧选择活动" class="act-detail-empty" />
          </template>
          <template v-else>
            <a-spin :loading="detailLoading" class="act-detail-spin">
              <div class="act-detail-head">
                <div class="act-detail-head__title">
                  {{ detail?.title }}
                  <a-tag v-if="detail" size="small" :color="phaseTagColor(detail.phase)">
                    {{ phaseLabel(detail.phase) }}
                  </a-tag>
                  <a-tag v-if="detail?.deleteState === 1" size="small" color="red">
                    已删除
                  </a-tag>
                </div>
              </div>

              <div class="act-info-cards">
                <div class="act-info-card">
                  <div class="act-info-card__label">
                    单次消耗积分
                  </div>
                  <div class="act-info-card__value act-info-card__value--accent">
                    {{ detail?.costPointsPerDraw }} 积分
                  </div>
                </div>
                <div class="act-info-card">
                  <div class="act-info-card__label">
                    对用户开放
                  </div>
                  <div class="act-info-card__value" :class="{ 'act-info-card__value--green': detail?.status === 1 }">
                    {{ detail?.status === 1 ? '已开放' : '已关闭' }}
                  </div>
                </div>
                <div class="act-info-card">
                  <div class="act-info-card__label">
                    活动时间
                  </div>
                  <div class="act-info-card__value act-info-card__value--sm">
                    <template v-if="detail?.startTime || detail?.endTime">
                      {{ detail?.startTime || '—' }} ~ {{ detail?.endTime || '—' }}
                    </template>
                    <template v-else>
                      未设置期限
                    </template>
                  </div>
                </div>
              </div>

              <div v-if="detail?.description" class="act-desc-block">
                <div class="act-desc-block__label">
                  活动说明
                </div>
                {{ detail.description }}
              </div>

              <div class="act-prize-section">
                <div class="act-prize-section__head">
                  <span class="act-prize-section__title">奖池（剩余库存）</span>
                  <div class="act-prize-section__actions">
                    <a-tooltip content="中奖记录">
                      <button type="button" class="act-icon-btn" @click="drawRecordsVisible = true">
                        <img :src="iconDrawRecords" alt="中奖记录" class="act-icon-btn__img">
                      </button>
                    </a-tooltip>
                    <a-button
                      type="outline"
                      size="small"
                      :disabled="!detail || detail.deleteState === 1"
                      @click="poolModalVisible = true"
                    >
                      编辑奖池
                    </a-button>
                  </div>
                </div>
                <a-table
                  row-key="activityPrizeId"
                  :data="detail?.prizeLines ?? []"
                  :columns="prizeColumns"
                  :pagination="false"
                  size="small"
                  :bordered="{ cell: true }"
                />
              </div>
            </a-spin>
          </template>
        </div>
      </div>
      </div>
    </div>

    <ActivityCreateModal
      v-model:visible="createModalVisible"
      :prize-options="prizeOptionList"
      :shelf-prize-id-set="shelfPrizeIdSet"
      @success="onCreateSuccess"
    />
    <ActivityListActionDialog
      v-model:visible="listActionVisible"
      :activity-id="listActionId"
      @success="onListActionSuccess"
    />
    <ActivityPoolEditModal
      v-model:visible="poolModalVisible"
      :detail="detail"
      :prize-options="prizeOptionList"
      :shelf-prize-id-set="shelfPrizeIdSet"
      @saved="onPoolSaved"
    />
    <ActivityDrawRecordsDialog
      v-model:visible="drawRecordsVisible"
      :activity-id="selectedId"
    />
  </GiPageLayout>
</template>

<script setup lang="tsx">
import type { TableColumnData } from '@arco-design/web-vue'
import type { LotteryActivityDetail, LotteryActivityRow, LotteryPrizeLine } from '@/apis/content/lotteryActivity'
import {
  getLotteryActivityDetail,
  getLotteryActivityList,
} from '@/apis/content/lotteryActivity'
import type { LotteryPrizeOption } from '@/apis/content/lotteryPrize'
import { getLotteryPrizeOptionsOnShelf } from '@/apis/content/lotteryPrize'
import iconDrawRecords from '@/assets/svg/抽奖记录.svg'
import { useTable } from '@/hooks'
import ActivityCreateModal from './ActivityCreateModal.vue'
import ActivityDrawRecordsDialog from './ActivityDrawRecordsDialog.vue'
import ActivityListActionDialog from './ActivityListActionDialog.vue'
import ActivityPoolEditModal from './ActivityPoolEditModal.vue'
import {
  ACTIVITY_FILTER_OPTIONS,
  ACTIVITY_SORT_OPTIONS,
  PRIZE_TYPE_LABELS,
  type ActivityFilterKey,
  type ActivitySortMode,
  parseActivityFilter,
  parseActivitySort,
  phaseLabel,
} from './lotteryActivityShared'

defineOptions({ name: 'ContentLotteryActivity' })

const LIST_PAGE_SIZE = 10

const filterOptions = ACTIVITY_FILTER_OPTIONS
const sortOptions = ACTIVITY_SORT_OPTIONS

const queryParams = reactive({
  title: '',
  activityFilter: 'all' as ActivityFilterKey,
  sortMode: 'id_asc' as ActivitySortMode,
})

let searchTimer: ReturnType<typeof setTimeout> | null = null
watch(
  () => queryParams.title,
  () => {
    if (searchTimer)
      clearTimeout(searchTimer)
    searchTimer = setTimeout(() => search(), 400)
  },
)

function buildListParams(page: { page: number, size: number }) {
  const filter = parseActivityFilter(queryParams.activityFilter)
  const sort = parseActivitySort(queryParams.sortMode)
  const q: Record<string, unknown> = {
    pageNum: page.page,
    pageSize: page.size,
    ...sort,
    ...filter,
  }
  if (queryParams.title.trim())
    q.title = queryParams.title.trim()
  return q
}

const { loading, tableData, pagination, search } = useTable<LotteryActivityRow>({
  listAPI: page => getLotteryActivityList(buildListParams({ page: page.page, size: LIST_PAGE_SIZE })),
  immediate: true,
  onSuccess: () => {
    const first = tableData.value[0]
    if (first && (!selectedId.value || !tableData.value.some(r => r.id === selectedId.value))) {
      void selectActivity(first)
    }
    if (!tableData.value.length) {
      selectedId.value = null
      detail.value = null
    }
  },
})

pagination.showPageSize = false
pagination.pageSize = LIST_PAGE_SIZE

function onListPageChange(page: number) {
  pagination.current = page
  search()
}

const reset = () => {
  queryParams.title = ''
  queryParams.activityFilter = 'all'
  queryParams.sortMode = 'id_asc'
  search()
}

const prizeOptionList = ref<LotteryPrizeOption[]>([])
const shelfPrizeIdSet = ref<Set<string>>(new Set())

async function refreshPrizeOptions() {
  try {
    const res = await getLotteryPrizeOptionsOnShelf()
    const list = Array.isArray(res?.data) ? res.data : []
    prizeOptionList.value = list
    shelfPrizeIdSet.value = new Set(list.map(p => String(p.id)))
  }
  catch {
    prizeOptionList.value = []
    shelfPrizeIdSet.value = new Set()
  }
}

onMounted(() => {
  void refreshPrizeOptions()
})

const selectedId = ref<string | null>(null)
const detail = ref<LotteryActivityDetail | null>(null)
const detailLoading = ref(false)

const createModalVisible = ref(false)
const listActionVisible = ref(false)
const listActionId = ref<string | null>(null)
const poolModalVisible = ref(false)
const drawRecordsVisible = ref(false)

async function loadDetail() {
  if (!selectedId.value) {
    detail.value = null
    return
  }
  detailLoading.value = true
  try {
    const res = await getLotteryActivityDetail({ id: selectedId.value })
    detail.value = (res?.data as LotteryActivityDetail) ?? null
  }
  finally {
    detailLoading.value = false
  }
}

async function selectActivity(row: LotteryActivityRow) {
  selectedId.value = row.id
  await loadDetail()
}

function openListAction(row: LotteryActivityRow) {
  listActionId.value = row.id
  listActionVisible.value = true
}

function phaseTagColor(phase: number) {
  if (phase === 1)
    return 'green'
  if (phase === 0)
    return 'arcoblue'
  return 'gray'
}

const prizeColumns: TableColumnData[] = [
  { title: '奖品', dataIndex: 'name', width: 120 },
  {
    title: '类型',
    width: 88,
    render: ({ record }) => PRIZE_TYPE_LABELS[(record as LotteryPrizeLine).prizeType] ?? (record as LotteryPrizeLine).prizeType,
  },
  { title: '数值', dataIndex: 'prizeValue', width: 64, align: 'center' },
  {
    title: '概率(%)',
    width: 80,
    align: 'center',
    render: ({ record }) => {
      const w = Number((record as LotteryPrizeLine).weight) || 0
      const lines = detail.value?.prizeLines ?? []
      const total = lines.reduce((s, r) => s + (Number(r.weight) || 0), 0)
      if (!total) return '—'
      return `${((w / total) * 100).toFixed(2)}%`
    },
  },
  {
    title: '剩余库存',
    dataIndex: 'stockRemaining',
    width: 88,
    align: 'center',
    render: ({ record }) => {
      const v = (record as LotteryPrizeLine).stockRemaining
      return v === -1 ? '不限' : v
    },
  },
  {
    title: '神秘',
    width: 52,
    align: 'center',
    render: ({ record }) => ((record as LotteryPrizeLine).isMysteryBundle === 1 ? '是' : ''),
  },
  {
    title: '头奖',
    width: 52,
    align: 'center',
    render: ({ record }) => ((record as LotteryPrizeLine).isJackpot === 1 ? '是' : ''),
  },
]

async function openCreate() {
  await refreshPrizeOptions()
  createModalVisible.value = true
}

async function onCreateSuccess(activityId: string) {
  search()
  if (activityId) {
    selectedId.value = activityId
    await loadDetail()
  }
}

async function onListActionSuccess() {
  search()
  if (listActionId.value && selectedId.value === listActionId.value)
    await loadDetail()
}

async function onPoolSaved() {
  await loadDetail()
}
</script>

<style scoped lang="scss">
.act-toolbar__input {
  width: 168px;
}

.act-toolbar__select {
  width: 120px;

  &--sort {
    width: 136px;
  }
}

.act-chrome-ring {
  position: relative;
  border-radius: 14px;
  padding: 2px;
  box-shadow: 0 2px 12px rgba(60, 64, 67, 0.1);
  background: conic-gradient(
    from -45deg at 50% 50%,
    #4285f4 0deg 90deg,
    #34a853 90deg 180deg,
    #fbbc04 180deg 270deg,
    #ea4335 270deg 360deg
  );
}

.act-chrome-inner {
  position: relative;
  z-index: 1;
  border-radius: 12px;
  background: var(--color-bg-2);
  overflow: hidden;
  min-height: calc(100vh - 220px);
}

.act-split {
  display: grid;
  grid-template-columns: minmax(240px, 25%) 1fr;
  min-height: calc(100vh - 200px);
  &__left {
    display: flex;
    flex-direction: column;
    border-right: 1px solid var(--color-border-2);
    min-width: 0;
  }
  &__right {
    min-width: 0;
    padding: 16px;
  }
  &__panel-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 14px;
    border-bottom: 1px solid var(--color-border-2);
  }

  &__panel-title {
    font-size: 15px;
    font-weight: 600;
    color: var(--color-text-1);
  }

  &__panel-count {
    font-size: 12px;
    color: var(--color-text-3);
  }
}

.act-toolbar-row {
  width: 100%;
  margin-bottom: 12px;
}

.act-item-list-wrap {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.act-item-list {
  list-style: none;
  margin: 0;
  padding: 0;
  flex: 1;
  overflow-y: auto;
}

.act-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--color-border-2);
  cursor: pointer;
  transition: background 0.12s;

  &:hover {
    background: var(--color-fill-2);
  }

  &--active {
    background: rgb(var(--primary-1));

    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 0;
      bottom: 0;
      width: 3px;
      background: rgb(var(--primary-6));
    }
  }

  position: relative;

  &__main {
    min-width: 0;
    flex: 1;
  }

  &__title {
    font-size: 13px;
    font-weight: 500;
    color: var(--color-text-1);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    margin-bottom: 6px;
  }

  &__meta {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
  }
}

.act-list-pager {
  padding: 10px 14px;
  border-top: 1px solid var(--color-border-2);
  display: flex;
  justify-content: center;
}

.act-detail-empty {
  padding: 80px 0;
}

.act-detail-spin {
  width: 100%;
}

.act-detail-head {
  margin-bottom: 14px;
  &__title {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
    font-size: 15px;
    font-weight: 500;
    color: var(--color-text-1);
  }
}

.act-info-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}

.act-info-card {
  background: var(--color-fill-2);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  padding: 10px 12px;
  &__label {
    font-size: 11px;
    color: var(--color-text-3);
    margin-bottom: 4px;
  }
  &__value {
    font-size: 14px;
    font-weight: 500;
    color: var(--color-text-1);
    &--accent {
      color: rgb(var(--primary-6));
    }
    &--green {
      color: rgb(var(--green-6));
    }
    &--sm {
      font-size: 12px;
      font-weight: 400;
      color: var(--color-text-2);
    }
  }
}

.act-desc-block {
  background: var(--color-fill-2);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 14px;
  font-size: 12px;
  color: var(--color-text-2);
  line-height: 1.7;
  &__label {
    font-size: 11px;
    font-weight: 500;
    color: var(--color-text-3);
    margin-bottom: 6px;
  }
}

.act-prize-section {
  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
  }
  &__title {
    font-size: 13px;
    font-weight: 500;
    color: var(--color-text-2);
  }
  &__actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.act-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
  transition: background 0.15s;
  &:hover {
    background: var(--color-fill-2);
  }
  &__img {
    width: 18px;
    height: 18px;
    display: block;
  }
}

@media (max-width: 900px) {
  .act-split {
    grid-template-columns: 1fr;
    &__left {
      border-right: none;
      border-bottom: 1px solid var(--color-border-2);
    }
  }
  .act-info-cards {
    grid-template-columns: 1fr;
  }
}
</style>
