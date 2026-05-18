<template>
  <a-modal
    v-model:visible="visible"
    :title="editIndex === null ? '添加奖项' : '编辑奖项'"
    width="min(520px, 94vw)"
    :mask-closable="false"
    unmount-on-close
    class="activity-prize-line-dialog"
    @cancel="visible = false"
    @before-ok="handleOk"
  >
    <a-form :model="form" layout="vertical">
      <a-form-item label="奖品（已上架）" required>
        <a-select
          v-model="form.prizeId"
          :options="prizeSelectOptions"
          allow-search
          placeholder="从奖品库选择"
        />
      </a-form-item>
      <a-row :gutter="12">
        <a-col :span="12">
          <a-form-item label="中奖概率(%)" required>
            <a-input-number
              v-model="form.probabilityPercent"
              :min="0"
              :max="100"
              :precision="2"
              style="width: 100%"
            />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="剩余库存" required>
            <a-input-number v-model="form.stockRemaining" style="width: 100%" placeholder="-1 不限" />
          </a-form-item>
        </a-col>
      </a-row>
      <a-form-item label="本行配图">
        <div class="upload-row">
          <a-upload :custom-request="onImageUpload" accept="image/*" :show-file-list="false">
            <template #upload-button>
              <a-button type="outline" size="small" :loading="imageUploading">
                上传图片
              </a-button>
            </template>
          </a-upload>
          <a-input
            v-model="form.imagePath"
            class="upload-row__input"
            placeholder="上传后自动填入 URL，也可粘贴外链"
            allow-clear
            size="small"
          />
        </div>
      </a-form-item>
      <div v-if="mysteryHint" class="mystery-hint">
        {{ mysteryHint }}
      </div>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import type { RequestOption, UploadRequest } from '@arco-design/web-vue'
import { Message } from '@arco-design/web-vue'
import axios from 'axios'
import type { LotteryPrizeOption } from '@/apis/content/lotteryPrize'
import { getLotteryPrizeDetail } from '@/apis/content/lotteryPrize'
import type { ActivityPrizeLineForm } from './lotteryActivityShared'
import { getToken } from '@/utils/auth'

const props = defineProps<{
  editIndex: number | null
  initial?: ActivityPrizeLineForm | null
  prizeOptions: LotteryPrizeOption[]
  activityId?: string | null
}>()

const visible = defineModel<boolean>('visible', { default: false })

const emit = defineEmits<{
  (e: 'confirm', payload: ActivityPrizeLineForm, index: number | null): void
}>()

const form = reactive<ActivityPrizeLineForm>({
  prizeId: '',
  probabilityPercent: 10,
  stockRemaining: -1,
  imagePath: '',
})

const imageUploading = ref(false)
const mysteryHint = ref('')

const prizeSelectOptions = computed(() =>
  props.prizeOptions.map(p => ({
    label: `${p.name} (#${p.id})${p.isMysteryBundle === 1 ? ' · 神秘' : ''}`,
    value: String(p.id),
  })),
)

watch(
  () => [visible.value, props.editIndex, props.initial] as const,
  ([vis]) => {
    if (!vis)
      return
    const init = props.initial
    form.activityPrizeId = init?.activityPrizeId
    form.prizeId = init?.prizeId ?? ''
    form.probabilityPercent = init?.probabilityPercent ?? 10
    form.stockRemaining = init?.stockRemaining ?? -1
    form.imagePath = init?.imagePath ?? ''
    mysteryHint.value = ''
    if (form.prizeId)
      void loadMysteryHint(form.prizeId)
  },
)

watch(
  () => form.prizeId,
  (pid) => {
    if (pid)
      void loadMysteryHint(pid)
    else
      mysteryHint.value = ''
  },
)

async function loadMysteryHint(prizeId: string) {
  const opt = props.prizeOptions.find(p => String(p.id) === prizeId)
  if (!opt || opt.isMysteryBundle !== 1) {
    mysteryHint.value = ''
    return
  }
  try {
    const res = await getLotteryPrizeDetail({ id: prizeId })
    const items = res?.data?.mysteryItems ?? []
    if (!items.length) {
      mysteryHint.value = '神秘大奖（子池在奖品管理中配置）'
      return
    }
    mysteryHint.value = `神秘子池：${items.map(m => (m.itemType === 4 ? `${m.itemValue}积分` : `VIP${m.itemValue}天`) + `×${m.weight}`).join('、')}`
  }
  catch {
    mysteryHint.value = '神秘大奖'
  }
}

function onImageUpload(option: RequestOption): UploadRequest {
  const file = option.fileItem?.file as File | undefined
  if (!file) {
    option.onError(new Error('no file'))
    return {}
  }
  imageUploading.value = true
  const fd = new FormData()
  fd.append('file', file)
  const base = import.meta.env.VITE_API_PREFIX || ''
  const aid = props.activityId ? Number(props.activityId) : 0
  const pid = form.prizeId ? Number(form.prizeId) : 0
  axios
    .post(`${base}/file/uploadLotteryPrizePicture?activityId=${aid}&prizeId=${pid}`, fd, {
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
      form.imagePath = url
      Message.success('图片已上传')
      option.onSuccess(res.data)
    })
    .catch(() => {
      Message.error('上传失败')
      option.onError(new Error('upload'))
    })
    .finally(() => {
      imageUploading.value = false
    })
  return {}
}

function handleOk(): boolean {
  if (!form.prizeId) {
    Message.warning('请选择奖品')
    return false
  }
  if (form.probabilityPercent < 0) {
    Message.warning('概率不能为负')
    return false
  }
  emit('confirm', { ...form }, props.editIndex)
  return true
}
</script>

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
.mystery-hint {
  font-size: 12px;
  color: var(--color-text-3);
  line-height: 1.5;
  padding: 8px 10px;
  background: var(--color-fill-2);
  border-radius: 6px;
}
</style>
