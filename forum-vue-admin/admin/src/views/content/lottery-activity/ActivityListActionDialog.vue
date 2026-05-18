<template>
  <a-modal
    v-model:visible="visible"
    title="活动操作"
    width="min(720px, 96vw)"
    :closable="false"
    :mask-closable="false"
    unmount-on-close
    modal-class="activity-list-action-dialog"
  >
    <a-spin :loading="loading">
      <a-form v-if="detail" :model="form" layout="vertical">
        <a-form-item label="标题" required>
          <a-input v-model="form.title" placeholder="活动标题" allow-clear />
        </a-form-item>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="活动阶段">
              <a-select v-model="form.phase" :options="phaseOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="对用户开放">
              <a-select v-model="form.status" :options="statusOptions" />
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
        <a-form-item label="单次消耗积分">
          <a-input-number v-model="form.costPointsPerDraw" :min="0" style="width: 100%" />
        </a-form-item>
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
      </a-form>
    </a-spin>

    <template #footer>
      <div class="action-dialog-footer">
        <a-popconfirm
          v-if="detail && detail.deleteState === 0"
          content="确认删除该活动？删除后可在「已删除」筛选中查看。"
          @ok="handleDelete"
        >
          <a-button status="danger" type="outline" :loading="deleteLoading">
            删除活动
          </a-button>
        </a-popconfirm>
        <a-popconfirm
          v-else-if="detail && detail.deleteState === 1"
          content="恢复该活动？"
          @ok="handleRestore"
        >
          <a-button type="outline" status="success" :loading="deleteLoading">
            恢复活动
          </a-button>
        </a-popconfirm>
        <div class="action-dialog-footer__right">
          <a-button @click="visible = false">
            取消
          </a-button>
          <a-button type="primary" :loading="saveLoading" @click="handleSave">
            保存
          </a-button>
        </div>
      </div>
    </template>
  </a-modal>
</template>

<script setup lang="ts">
import type { RequestOption, UploadRequest } from '@arco-design/web-vue'
import { Message } from '@arco-design/web-vue'
import axios from 'axios'
import type { LotteryActivityDetail } from '@/apis/content/lotteryActivity'
import {
  getLotteryActivityDetail,
  setLotteryActivityDeleteState,
  updateLotteryActivityMeta,
} from '@/apis/content/lotteryActivity'
import { PHASE_OPTIONS, STATUS_OPTIONS } from './lotteryActivityShared'
import { getToken } from '@/utils/auth'

const props = defineProps<{
  activityId: string | null
}>()

const visible = defineModel<boolean>('visible', { default: false })

const emit = defineEmits<{
  (e: 'success'): void
}>()

const phaseOptions = PHASE_OPTIONS
const statusOptions = STATUS_OPTIONS

const loading = ref(false)
const saveLoading = ref(false)
const deleteLoading = ref(false)
const coverUploading = ref(false)
const detail = ref<LotteryActivityDetail | null>(null)

const form = reactive({
  title: '',
  description: '',
  coverImageUrl: '',
  costPointsPerDraw: 30,
  status: 1,
  phase: 0,
  startTime: undefined as string | undefined,
  endTime: undefined as string | undefined,
})

watch(
  () => [visible.value, props.activityId] as const,
  ([vis, id]) => {
    if (!vis || !id) {
      detail.value = null
      return
    }
    void loadDetail(id)
  },
)

async function loadDetail(id: string) {
  loading.value = true
  try {
    const res = await getLotteryActivityDetail({ id })
    const d = (res?.data as LotteryActivityDetail) ?? null
    detail.value = d
    if (!d)
      return
    form.title = d.title
    form.description = d.description ?? ''
    form.coverImageUrl = d.coverImageUrl ?? ''
    form.costPointsPerDraw = d.costPointsPerDraw
    form.status = d.status
    form.phase = d.phase
    form.startTime = d.startTime ?? undefined
    form.endTime = d.endTime ?? undefined
  }
  finally {
    loading.value = false
  }
}

function onCoverUpload(option: RequestOption): UploadRequest {
  const file = option.fileItem?.file as File | undefined
  if (!file) {
    option.onError(new Error('no file'))
    return {}
  }
  coverUploading.value = true
  const fd = new FormData()
  fd.append('file', file)
  const base = import.meta.env.VITE_API_PREFIX || ''
  const aid = props.activityId ? Number(props.activityId) : 0
  axios
    .post(`${base}/file/uploadLotteryActivityPicture?activityId=${aid}`, fd, {
      headers: { Authorization: getToken() || '' },
    })
    .then((res) => {
      const body = res.data as { code?: number, message?: string, data?: string }
      if (body?.code !== undefined && body.code !== 0) {
        Message.error(body.message || '上传失败')
        option.onError(new Error(body.message))
        return
      }
      const url = typeof body?.data === 'string' ? body.data : ''
      if (!url) {
        Message.error('上传返回无 URL')
        option.onError(new Error('no url'))
        return
      }
      form.coverImageUrl = url
      Message.success('封面已上传')
      option.onSuccess(res.data)
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

async function handleSave() {
  if (!detail.value?.id) {
    Message.warning('活动不存在')
    return
  }
  if (!form.title.trim()) {
    Message.warning('请填写标题')
    return
  }
  saveLoading.value = true
  try {
    await updateLotteryActivityMeta({
      id: Number(detail.value.id),
      title: form.title.trim(),
      description: form.description || null,
      coverImageUrl: form.coverImageUrl || null,
      costPointsPerDraw: form.costPointsPerDraw,
      status: form.status,
      phase: form.phase,
      startTime: form.startTime || null,
      endTime: form.endTime || null,
    })
    Message.success('已保存')
    visible.value = false
    emit('success')
  }
  finally {
    saveLoading.value = false
  }
}

async function handleDelete() {
  if (!detail.value?.id)
    return
  deleteLoading.value = true
  try {
    await setLotteryActivityDeleteState({ id: detail.value.id, deleteState: 1 })
    Message.success('已标记删除')
    visible.value = false
    emit('success')
  }
  finally {
    deleteLoading.value = false
  }
}

async function handleRestore() {
  if (!detail.value?.id)
    return
  deleteLoading.value = true
  try {
    await setLotteryActivityDeleteState({ id: detail.value.id, deleteState: 0 })
    Message.success('已恢复')
    visible.value = false
    emit('success')
  }
  finally {
    deleteLoading.value = false
  }
}
</script>

<style lang="scss">
.activity-list-action-dialog {
  .arco-modal-body {
    .arco-spin,
    .arco-form {
      width: 100%;
    }
  }
}
</style>

<style scoped lang="scss">
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

.action-dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;

  &__right {
    display: flex;
    gap: 8px;
  }
}
</style>
