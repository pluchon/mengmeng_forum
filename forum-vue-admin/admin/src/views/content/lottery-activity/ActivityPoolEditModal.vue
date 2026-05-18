<template>
  <a-modal
    v-model:visible="visible"
    title="编辑奖池"
    width="min(820px, 96vw)"
    :mask-closable="false"
    :footer="false"
    unmount-on-close
    class="activity-pool-edit-modal"
  >
    <a-spin :loading="saving" class="pool-modal-body">
      <ActivityPrizePoolTable
        :lines="lines"
        :prize-options="prizeOptions"
        @add="openAddLine"
        @edit="openEditLine"
        @remove="removeLine"
      />
    </a-spin>

    <ActivityPrizeLineDialog
      v-model:visible="lineDialogVisible"
      :edit-index="lineEditIndex"
      :initial="lineEditInitial"
      :prize-options="prizeOptions"
      :activity-id="detail?.id ?? null"
      @confirm="onLineConfirm"
    />
  </a-modal>
</template>

<script setup lang="ts">
import { Message } from '@arco-design/web-vue'
import type { LotteryActivityDetail } from '@/apis/content/lotteryActivity'
import { saveLotteryActivity } from '@/apis/content/lotteryActivity'
import type { LotteryPrizeOption } from '@/apis/content/lotteryPrize'
import ActivityPrizeLineDialog from './ActivityPrizeLineDialog.vue'
import ActivityPrizePoolTable from './ActivityPrizePoolTable.vue'
import { buildActivitySaveBody, detailToLineForms } from './activitySaveUtils'
import type { ActivityPrizeLineForm } from './lotteryActivityShared'

const props = defineProps<{
  detail: LotteryActivityDetail | null
  prizeOptions: LotteryPrizeOption[]
  shelfPrizeIdSet: Set<string>
}>()

const visible = defineModel<boolean>('visible', { default: false })

const emit = defineEmits<{
  (e: 'saved'): void
}>()

const saving = ref(false)
const lines = ref<ActivityPrizeLineForm[]>([])

const lineDialogVisible = ref(false)
const lineEditIndex = ref<number | null>(null)
const lineEditInitial = ref<ActivityPrizeLineForm | null>(null)

watch(
  () => [visible.value, props.detail] as const,
  ([vis, d]) => {
    if (!vis || !d) {
      lines.value = []
      return
    }
    lines.value = detailToLineForms(d)
  },
)

function openAddLine() {
  lineEditIndex.value = null
  lineEditInitial.value = null
  lineDialogVisible.value = true
}

function openEditLine(idx: number) {
  lineEditIndex.value = idx
  lineEditInitial.value = { ...lines.value[idx] }
  lineDialogVisible.value = true
}

async function onLineConfirm(payload: ActivityPrizeLineForm, index: number | null) {
  if (!props.shelfPrizeIdSet.has(String(payload.prizeId))) {
    Message.warning('请选择已上架的奖品')
    return
  }
  const next = [...lines.value]
  if (index === null)
    next.push(payload)
  else
    next[index] = payload
  await persistLines(next)
}

async function removeLine(idx: number) {
  const next = lines.value.filter((_, i) => i !== idx)
  await persistLines(next)
}

async function persistLines(next: ActivityPrizeLineForm[]) {
  if (!props.detail)
    return
  if (!next.length) {
    Message.warning('奖池至少保留一条奖品')
    return
  }
  for (const l of next) {
    if (!l.prizeId) {
      Message.warning('每行须选择奖品')
      return
    }
  }
  saving.value = true
  try {
    await saveLotteryActivity(buildActivitySaveBody(props.detail, next))
    lines.value = next
    Message.success('奖池已更新')
    emit('saved')
  }
  finally {
    saving.value = false
  }
}
</script>

<style scoped lang="scss">
.pool-modal-body {
  display: block;
  max-height: min(68vh, 560px);
  overflow-y: auto;
  padding-right: 4px;
  scrollbar-width: thin;
}
</style>
