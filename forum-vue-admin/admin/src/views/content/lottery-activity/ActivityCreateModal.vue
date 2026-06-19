<template>
  <a-modal
    v-model:visible="visible"
    title="新建活动"
    width="min(820px, 96vw)"
    :mask-closable="false"
    :ok-loading="saveLoading"
    unmount-on-close
    hide-cancel
    class="activity-create-modal"
    @before-ok="submitSave"
  >
    <div class="create-modal-body">
      <a-form :model="form" layout="vertical">
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="标题" required>
              <a-input v-model="form.title" placeholder="活动标题" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="单次消耗积分">
              <a-input-number v-model="form.costPointsPerDraw" :min="0" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="封面图">
          <div class="upload-row">
            <a-upload :custom-request="onCoverUpload" accept="image/*" :show-file-list="false">
              <template #upload-button>
                <a-button type="outline" size="small" :loading="coverUploading">
                  上传封面
                </a-button>
              </template>
            </a-upload>
            <a-input
              v-model="form.coverImageUrl"
              class="upload-row__input"
              placeholder="上传后自动填入 URL，也可粘贴外链"
              allow-clear
              size="small"
            />
          </div>
        </a-form-item>
        <a-form-item label="活动说明">
          <a-textarea v-model="form.description" :auto-size="{ minRows: 2, maxRows: 6 }" />
        </a-form-item>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="对用户开放">
              <a-select v-model="form.status" :options="statusOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="活动阶段">
              <a-select v-model="form.phase" :options="phaseOptions" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="开始时间（可选）">
              <a-date-picker
                v-model="form.startTime"
                show-time
                format="YYYY-MM-DD HH:mm:ss"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
                allow-clear
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="结束时间（可选）">
              <a-date-picker
                v-model="form.endTime"
                show-time
                format="YYYY-MM-DD HH:mm:ss"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
                allow-clear
              />
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item label="奖品配置" class="create-prize-form-item">
          <ActivityPrizePoolTable
            :lines="form.lines"
            :prize-options="prizeOptions"
            @add="openAddLine"
            @edit="openEditLine"
            @remove="idx => form.lines.splice(idx, 1)"
          />
        </a-form-item>
      </a-form>
    </div>

    <ActivityPrizeLineDialog
      v-model:visible="lineDialogVisible"
      :edit-index="lineEditIndex"
      :initial="lineEditInitial"
      :prize-options="prizeOptions"
      activity-id=""
      @confirm="onLineConfirm"
    />
  </a-modal>
</template>

<script setup lang="ts">
import type { RequestOption, UploadRequest } from '@arco-design/web-vue'
import { Message } from '@arco-design/web-vue'
import { uploadLotteryActivityPicture } from '@/apis/file'
import { saveLotteryActivity } from '@/apis/content/lotteryActivity'
import type { LotteryPrizeOption } from '@/apis/content/lotteryPrize'
import ActivityPrizeLineDialog from './ActivityPrizeLineDialog.vue'
import ActivityPrizePoolTable from './ActivityPrizePoolTable.vue'
import { linesToSavePayload } from './activitySaveUtils'
import type { ActivityPrizeLineForm } from './lotteryActivityShared'
import { PHASE_OPTIONS, STATUS_OPTIONS } from './lotteryActivityShared'

const props = defineProps<{
  prizeOptions: LotteryPrizeOption[]
  shelfPrizeIdSet: Set<string>
}>()

const visible = defineModel<boolean>('visible', { default: false })

const emit = defineEmits<{
  (e: 'success', activityId: string): void
}>()

const phaseOptions = PHASE_OPTIONS
const statusOptions = STATUS_OPTIONS

const saveLoading = ref(false)
const coverUploading = ref(false)

const form = reactive({
  title: '',
  description: '',
  coverImageUrl: '',
  costPointsPerDraw: 30,
  status: 0,
  phase: 0,
  startTime: undefined as string | undefined,
  endTime: undefined as string | undefined,
  lines: [] as ActivityPrizeLineForm[],
})

const lineDialogVisible = ref(false)
const lineEditIndex = ref<number | null>(null)
const lineEditInitial = ref<ActivityPrizeLineForm | null>(null)

watch(visible, (vis) => {
  if (!vis)
    return
  form.title = ''
  form.description = ''
  form.coverImageUrl = ''
  form.costPointsPerDraw = 30
  form.status = 0
  form.phase = 0
  form.startTime = undefined
  form.endTime = undefined
  form.lines = defaultLines()
})

function defaultLines(): ActivityPrizeLineForm[] {
  const list = props.prizeOptions
  if (list.length >= 3) {
    return [
      { prizeId: String(list[0].id), probabilityPercent: 70, stockRemaining: -1, imagePath: '' },
      { prizeId: String(list[1].id), probabilityPercent: 5, stockRemaining: 1, imagePath: '' },
      { prizeId: String(list[2].id), probabilityPercent: 25, stockRemaining: 500, imagePath: '' },
    ]
  }
  if (list.length === 2) {
    return [
      { prizeId: String(list[0].id), probabilityPercent: 60, stockRemaining: -1, imagePath: '' },
      { prizeId: String(list[1].id), probabilityPercent: 40, stockRemaining: -1, imagePath: '' },
    ]
  }
  if (list.length === 1) {
    return [{ prizeId: String(list[0].id), probabilityPercent: 100, stockRemaining: -1, imagePath: '' }]
  }
  return []
}

function openAddLine() {
  lineEditIndex.value = null
  lineEditInitial.value = null
  lineDialogVisible.value = true
}

function openEditLine(idx: number) {
  lineEditIndex.value = idx
  lineEditInitial.value = { ...form.lines[idx] }
  lineDialogVisible.value = true
}

function onLineConfirm(payload: ActivityPrizeLineForm, index: number | null) {
  if (index === null)
    form.lines.push(payload)
  else
    form.lines[index] = payload
}

function onCoverUpload(option: RequestOption): UploadRequest {
  const file = option.fileItem?.file as File | undefined
  if (!file) {
    option.onError(new Error('no file'))
    return {}
  }
  coverUploading.value = true
  uploadLotteryActivityPicture(file, 0)
    .then((url) => {
      if (!url) {
        Message.error('上传返回无 URL')
        option.onError(new Error('no url'))
        return
      }
      form.coverImageUrl = url
      Message.success('封面已上传')
      option.onSuccess({ data: url })
    })
    .catch(() => {
      Message.error('上传失败')
      option.onError(new Error('upload'))
    })
    .finally(() => {
      coverUploading.value = false
    })
  return {}
}

async function submitSave(): Promise<boolean> {
  if (!form.title.trim()) {
    Message.warning('请填写标题')
    return false
  }
  if (!form.lines.length) {
    Message.warning('请至少配置一条奖品')
    return false
  }
  for (const l of form.lines) {
    if (!l.prizeId) {
      Message.warning('每一行请选择奖品')
      return false
    }
    if (!props.shelfPrizeIdSet.has(String(l.prizeId))) {
      Message.warning('每行奖品须为奖品管理中已上架的条目')
      return false
    }
  }
  saveLoading.value = true
  try {
    const res = await saveLotteryActivity({
      title: form.title.trim(),
      description: form.description || null,
      coverImageUrl: form.coverImageUrl || null,
      costPointsPerDraw: form.costPointsPerDraw,
      status: form.status,
      phase: form.phase,
      startTime: form.startTime || null,
      endTime: form.endTime || null,
      lines: linesToSavePayload(form.lines),
    })
    const savedId = res?.data != null ? String(res.data) : ''
    Message.success('活动已创建')
    emit('success', savedId)
    return true
  }
  catch {
    return false
  }
  finally {
    saveLoading.value = false
  }
}
</script>

<style scoped lang="scss">
.create-modal-body {
  max-height: min(68vh, 560px);
  overflow-y: auto;
  padding-right: 4px;
}

.upload-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  &__input {
    flex: 1;
    min-width: 0;
  }
}

.create-prize-form-item {
  :deep(.arco-form-item-content) {
    display: block;
  }
}
</style>
