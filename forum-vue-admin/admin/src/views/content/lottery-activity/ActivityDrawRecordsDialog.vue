<template>
  <a-modal
    v-model:visible="visible"
    title="中奖记录"
    width="min(920px, 96vw)"
    :footer="false"
    unmount-on-close
    class="activity-draw-records-dialog"
  >
    <div class="draw-records-split">
      <div class="draw-records-users">
        <div class="draw-records-users__head">
          参与用户
        </div>
        <a-spin :loading="usersLoading" class="draw-records-users__spin">
          <a-empty v-if="!usersLoading && !userRows.length" description="暂无抽奖用户" />
          <ul v-else class="user-list">
            <li
              v-for="u in userRows"
              :key="u.userId"
              class="user-list__item"
              :class="{ 'user-list__item--on': selectedUserId === u.userId }"
              @click="selectUser(u)"
            >
              <AdminVipAvatar
                :src="u.avatarUrl"
                :vip-tier="u.vipTier"
                :vip-expire-at="u.vipExpireAt"
                :fallback-text="(u.nickname || '?').slice(0, 1)"
                :size="36"
              />
              <div class="user-list__info">
                <div class="user-list__name">
                  {{ u.nickname || `用户 #${u.userId}` }}
                </div>
                <div class="user-list__meta">
                  {{ u.drawCount }} 次 · {{ u.lastDrawTime }}
                </div>
              </div>
            </li>
          </ul>
          <div v-if="userRows.length" class="draw-records-pager">
            <a-pagination
              v-model:current="userPagination.current"
              v-model:page-size="userPagination.pageSize"
              :total="userPagination.total"
              size="small"
              simple
              @change="loadUsers"
            />
          </div>
        </a-spin>
      </div>

      <div class="draw-records-detail">
        <div class="draw-records-detail__head">
          <template v-if="selectedUser">
            {{ selectedUser.nickname || `用户 #${selectedUser.userId}` }} 的抽奖记录
          </template>
          <template v-else>
            请选择左侧用户
          </template>
        </div>
        <a-table
          row-key="id"
          :loading="winsLoading"
          :data="winRows"
          :columns="winColumns"
          :pagination="winsPagination"
          size="small"
          :bordered="{ cell: true }"
          class="draw-records-table"
          @page-change="onWinsPageChange"
          @page-size-change="onWinsPageSizeChange"
        />
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="tsx">
import type { TableColumnData } from '@arco-design/web-vue'
import type { LotteryDrawUserRow, LotteryWinRow } from '@/apis/content/lotteryActivity'
import { getLotteryDrawUserList, getLotteryWinList } from '@/apis/content/lotteryActivity'
import AdminVipAvatar from '@/components/AdminVipAvatar.vue'
import { PRIZE_TYPE_LABELS } from './lotteryActivityShared'

const props = defineProps<{
  activityId: string | null
}>()

const visible = defineModel<boolean>('visible', { default: false })

const usersLoading = ref(false)
const winsLoading = ref(false)
const userRows = ref<LotteryDrawUserRow[]>([])
const winRows = ref<LotteryWinRow[]>([])
const selectedUserId = ref<string | null>(null)

const selectedUser = computed(() =>
  userRows.value.find(u => u.userId === selectedUserId.value) ?? null,
)

const userPagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
})

const winsPagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: true,
  showPageSize: true,
  pageSizeOptions: [10, 20, 50],
})

const winColumns: TableColumnData[] = [
  { title: '日期', dataIndex: 'createTime', width: 168 },
  { title: '奖项内容', dataIndex: 'prizeName', ellipsis: true, tooltip: true },
  {
    title: '奖项类型',
    width: 96,
    align: 'center',
    render: ({ record }) => PRIZE_TYPE_LABELS[(record as LotteryWinRow).prizeType] ?? (record as LotteryWinRow).prizeType,
  },
  {
    title: '积分加减',
    width: 110,
    align: 'center',
    render: ({ record }) => {
      const r = record as LotteryWinRow
      if (r.prizeType === 4 && r.grantPoints)
        return <span class="win-pts-plus">+{r.grantPoints}</span>
      return <span class="win-pts-dash">-</span>
    },
  },
]

watch(
  () => [visible.value, props.activityId] as const,
  ([vis, aid]) => {
    if (!vis || !aid) {
      userRows.value = []
      winRows.value = []
      selectedUserId.value = null
      return
    }
    userPagination.current = 1
    void loadUsers()
  },
)

async function loadUsers() {
  if (!props.activityId)
    return
  usersLoading.value = true
  try {
    const res = await getLotteryDrawUserList({
      pageNum: userPagination.current,
      pageSize: userPagination.pageSize,
      activityId: Number(props.activityId),
    })
    const data = res?.data
    userRows.value = data?.records ?? []
    userPagination.total = data?.total ?? 0
    if (userRows.value.length) {
      const still = userRows.value.some(u => u.userId === selectedUserId.value)
      if (!still)
        selectUser(userRows.value[0])
    }
    else {
      selectedUserId.value = null
      winRows.value = []
      winsPagination.total = 0
    }
  }
  finally {
    usersLoading.value = false
  }
}

function selectUser(u: LotteryDrawUserRow) {
  selectedUserId.value = u.userId
  winsPagination.current = 1
  void loadWins()
}

async function loadWins() {
  if (!props.activityId || !selectedUserId.value) {
    winRows.value = []
    winsPagination.total = 0
    return
  }
  winsLoading.value = true
  try {
    const res = await getLotteryWinList({
      pageNum: winsPagination.current,
      pageSize: winsPagination.pageSize,
      activityId: Number(props.activityId),
      userId: Number(selectedUserId.value),
    })
    const data = res?.data
    winRows.value = data?.records ?? []
    winsPagination.total = data?.total ?? 0
  }
  finally {
    winsLoading.value = false
  }
}

function onWinsPageChange(page: number) {
  winsPagination.current = page
  void loadWins()
}

function onWinsPageSizeChange(size: number) {
  winsPagination.pageSize = size
  winsPagination.current = 1
  void loadWins()
}
</script>

<style scoped lang="scss">
.draw-records-split {
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: 14px;
  min-height: 420px;
  max-height: min(70vh, 560px);
}

.draw-records-users {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  overflow: hidden;
  min-height: 0;

  &__head {
    padding: 8px 12px;
    font-size: 12px;
    font-weight: 500;
    color: var(--color-text-3);
    background: var(--color-fill-2);
    border-bottom: 1px solid var(--color-border-2);
  }

  &__spin {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-height: 0;
  }
}

.user-list {
  list-style: none;
  margin: 0;
  padding: 0;
  flex: 1;
  overflow-y: auto;

  &__item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 12px;
    cursor: pointer;
    border-bottom: 1px solid var(--color-border-2);
    transition: background 0.12s;

    &:hover {
      background: var(--color-fill-2);
    }

    &--on {
      background: rgb(var(--primary-1));
    }
  }

  &__info {
    min-width: 0;
    flex: 1;
  }

  &__name {
    font-size: 13px;
    font-weight: 500;
    color: var(--color-text-1);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__meta {
    font-size: 11px;
    color: var(--color-text-3);
    margin-top: 2px;
  }
}

.draw-records-pager {
  padding: 8px;
  border-top: 1px solid var(--color-border-2);
  display: flex;
  justify-content: center;
}

.draw-records-detail {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;

  &__head {
    font-size: 13px;
    font-weight: 500;
    color: var(--color-text-2);
    margin-bottom: 8px;
    flex-shrink: 0;
  }
}

.draw-records-table {
  flex: 1;
  min-height: 0;

  :deep(.arco-table-th),
  :deep(.arco-table-td) {
    text-align: center;
  }

  :deep(.arco-table-th:nth-child(2)),
  :deep(.arco-table-td:nth-child(2)) {
    text-align: left;
  }
}

:deep(.win-pts-plus) {
  color: rgb(var(--orange-6));
  font-weight: 500;
}

:deep(.win-pts-dash) {
  color: var(--color-text-4);
}
</style>
