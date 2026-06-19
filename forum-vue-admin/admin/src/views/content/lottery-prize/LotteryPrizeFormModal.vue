<template>
  <a-modal
    v-model:visible="visible"
    :title="modalTitle"
    width="min(520px, 94vw)"
    :mask-closable="false"
    :ok-loading="saveLoading"
    unmount-on-close
    class="lottery-prize-form-modal"
    @cancel="visible = false"
    @before-ok="submitSave"
  >
    <a-form :model="form" layout="vertical" class="prize-form">
      <a-form-item label="奖品名称" required>
        <a-input v-model="form.name" placeholder="如：神秘大奖、50积分" allow-clear />
      </a-form-item>

      <a-row :gutter="12">
        <a-col :span="formTypeKey === 4 ? 12 : 24">
          <a-form-item label="类型" required>
            <a-select
              v-model="formTypeKey"
              :options="prizeTypeFormOptions"
              :disabled="!!form.id"
              placeholder="选择类型"
            />
          </a-form-item>
        </a-col>
        <a-col v-if="formTypeKey === 4" :span="12">
          <a-form-item label="数值" required>
            <a-input-number
              v-model="form.prizeValue"
              :min="1"
              :max="100"
              style="width: 100%"
              placeholder="积分数"
            />
          </a-form-item>
        </a-col>
      </a-row>

      <a-form-item label="库存" required>
        <a-input-number
          v-model="form.stockQuantity"
          :min="-1"
          style="width: 100%"
          placeholder="-1 表示不限量"
        />
        <template #extra>
          <span class="field-extra">-1 为不限量；0 表示无库存</span>
        </template>
      </a-form-item>

      <a-form-item v-if="isMysteryType" label="大奖子池" required>
        <div class="mystery-list">
          <div
            v-for="(m, idx) in form.mysteryItems"
            :key="idx"
            class="mystery-item-card"
          >
            <div class="mystery-item-card__main">
              <a-tag size="small" :color="m.itemType === 4 ? 'orangered' : 'arcoblue'">
                {{ m.itemType === 4 ? '积分' : 'VIP天' }}
              </a-tag>
              <span class="mystery-item-card__text">
                {{ mysteryItemSummary(m) }}
              </span>
            </div>
            <a-space :size="4">
              <a-button type="text" size="mini" @click="openMysteryEdit(idx)">
                编辑
              </a-button>
              <a-button type="text" size="mini" status="danger" @click="form.mysteryItems.splice(idx, 1)">
                删除
              </a-button>
            </a-space>
          </div>
          <button type="button" class="mystery-add-row" @click="openMysteryAdd">
            <span class="mystery-add-row__plus">+</span>
            添加子项
          </button>
        </div>
        <template #extra>
          <span class="field-extra">子项支持 VIP 天 / 积分（单项积分 ≤ 100）</span>
        </template>
      </a-form-item>

      <a-form-item label="奖品图">
        <template #extra>
          <span class="field-extra">可选</span>
        </template>
        <div class="upload-row">
          <a-upload :custom-request="onPrizeImageCustomRequest" accept="image/*" :show-file-list="false">
            <template #upload-button>
              <a-button type="outline" size="small" :loading="prizeImageUploading" class="upload-btn">
                上传图片
              </a-button>
            </template>
          </a-upload>
          <a-input
            v-model="form.imagePath"
            class="upload-row__input"
            placeholder="上传后自动填入 URL，也可手动粘贴外链"
            allow-clear
            size="small"
          />
        </div>
        <div v-if="form.imagePath?.trim()" class="prize-thumb-wrap">
          <img class="prize-thumb" :src="form.imagePath.trim()" alt="奖品图预览">
        </div>
      </a-form-item>
    </a-form>

    <LotteryMysteryItemDialog
      v-model:visible="mysteryDialogVisible"
      :edit-index="mysteryEditIndex"
      :initial="mysteryEditInitial"
      @confirm="onMysteryConfirm"
    />
  </a-modal>
</template>

<script setup lang="ts">
import { Message } from '@arco-design/web-vue'
import type { RequestOption, UploadRequest } from '@arco-design/web-vue/es/upload/interfaces'
import { uploadLotteryPrizePicture } from '@/apis/file'
import { getLotteryPrizeDetail, saveLotteryPrize } from '@/apis/content/lotteryPrize'
import LotteryMysteryItemDialog, { type MysteryItemForm } from './LotteryMysteryItemDialog.vue'

/** 表单类型：101 = 神秘大奖（存库为 prizeType=1 + isMysteryBundle=1） */
const MYSTERY_FORM_TYPE = 101

const PRIZE_TYPE_LABELS: Record<number, string> = {
  0: '谢谢',
  1: '大奖',
  2: '小奖',
  3: '安慰',
  4: '积分',
  5: 'VIP天',
}

const prizeTypeFormOptions = [
  ...Object.entries(PRIZE_TYPE_LABELS).map(([v, l]) => ({ label: l, value: Number(v) })),
  { label: '神秘大奖', value: MYSTERY_FORM_TYPE },
]

const formTypeKey = ref(0)

const isMysteryType = computed(() => formTypeKey.value === MYSTERY_FORM_TYPE)

const props = defineProps<{
  editId: string | null
}>()

const visible = defineModel<boolean>('visible', { default: false })

const emit = defineEmits<{
  (e: 'success'): void
}>()

const modalTitle = computed(() => (props.editId ? '编辑奖品' : '新建奖品'))

const saveLoading = ref(false)
const prizeImageUploading = ref(false)

const form = reactive({
  id: undefined as string | undefined,
  name: '',
  prizeType: 0,
  prizeValue: 0,
  stockQuantity: -1,
  catalogStatus: 2,
  imagePath: '',
  mysterySwitch: false,
  mysteryItems: [] as MysteryItemForm[],
})

const mysteryDialogVisible = ref(false)
const mysteryEditIndex = ref<number | null>(null)
const mysteryEditInitial = ref<MysteryItemForm | null>(null)

function mysteryItemSummary(m: MysteryItemForm) {
  const val = m.itemType === 4 ? `${m.itemValue} 积分` : `${m.itemValue} 天 VIP`
  return `${val} · 数量 ${m.weight}`
}

function resetForm() {
  form.id = undefined
  form.name = ''
  form.prizeType = 0
  form.prizeValue = 0
  form.stockQuantity = -1
  form.catalogStatus = 2
  form.imagePath = ''
  form.mysterySwitch = false
  form.mysteryItems = []
  formTypeKey.value = 0
}

function syncTypeFromFormKey(key: number) {
  if (key === MYSTERY_FORM_TYPE) {
    form.prizeType = 1
    form.mysterySwitch = true
    return
  }
  form.prizeType = key
  form.mysterySwitch = false
  if (key !== MYSTERY_FORM_TYPE)
    form.mysteryItems = []
}

function formKeyFromDetail(prizeType: number, isMysteryBundle: number) {
  if (prizeType === 1 && isMysteryBundle === 1)
    return MYSTERY_FORM_TYPE
  return prizeType
}

watch(
  () => [visible.value, props.editId] as const,
  async ([vis, id]) => {
    if (!vis)
      return
    if (id) {
      const res = await getLotteryPrizeDetail({ id })
      const d = res?.data
      if (!d) {
        Message.error('加载失败')
        visible.value = false
        return
      }
      form.id = String(d.id)
      form.name = d.name
      form.prizeType = d.prizeType
      form.prizeValue = d.prizeValue
      form.stockQuantity = d.stockQuantity ?? -1
      form.catalogStatus = d.catalogStatus
      form.imagePath = d.imagePath ?? ''
      formTypeKey.value = formKeyFromDetail(d.prizeType, d.isMysteryBundle)
      form.mysterySwitch = formTypeKey.value === MYSTERY_FORM_TYPE
      form.mysteryItems = (d.mysteryItems ?? []).map(m => ({
        itemType: m.itemType,
        itemValue: m.itemValue,
        weight: m.weight,
      }))
    }
    else {
      resetForm()
    }
  },
)

watch(formTypeKey, (key) => {
  syncTypeFromFormKey(key)
  if (key === 4)
    form.prizeValue = form.prizeValue > 0 ? form.prizeValue : 10
  else if (key === 5)
    form.prizeValue = form.prizeValue > 0 ? form.prizeValue : 1
  else if (key !== MYSTERY_FORM_TYPE)
    form.prizeValue = 0
})

function openMysteryAdd() {
  mysteryEditIndex.value = null
  mysteryEditInitial.value = null
  mysteryDialogVisible.value = true
}

function openMysteryEdit(idx: number) {
  mysteryEditIndex.value = idx
  mysteryEditInitial.value = { ...form.mysteryItems[idx] }
  mysteryDialogVisible.value = true
}

function onMysteryConfirm(payload: MysteryItemForm, index: number | null) {
  if (index === null)
    form.mysteryItems.push(payload)
  else
    form.mysteryItems[index] = payload
}

function onPrizeImageCustomRequest(option: RequestOption): UploadRequest {
  const file = option.fileItem?.file as File | undefined
  if (!file) {
    option.onError(new Error('no file'))
    return {}
  }
  prizeImageUploading.value = true
  const pid = form.id ? Number(form.id) : 0
  uploadLotteryPrizePicture(file, 0, pid)
    .then((url) => {
      if (!url) {
        Message.error('上传返回无 URL')
        option.onError(new Error('no url'))
        return
      }
      form.imagePath = url
      Message.success('图片已上传')
      option.onSuccess({ data: url })
    })
    .catch(() => {
      Message.error('上传失败')
      option.onError(new Error('upload'))
    })
    .finally(() => {
      prizeImageUploading.value = false
    })
  return {}
}

async function submitSave(): Promise<boolean> {
  if (!form.name.trim()) {
    Message.warning('请填写奖品名称')
    return false
  }
  if (formTypeKey.value === 4) {
    const pv = form.prizeValue ?? 0
    if (pv < 1 || pv > 100) {
      Message.warning('积分须为 1～100')
      return false
    }
  }
  if (form.stockQuantity < -1) {
    Message.warning('库存不能小于 -1')
    return false
  }
  if (isMysteryType.value && !form.mysteryItems.length) {
    Message.warning('神秘大奖请至少添加一条子项')
    return false
  }
  saveLoading.value = true
  try {
    const mystery = isMysteryType.value
    let prizeValue = 0
    if (formTypeKey.value === 4)
      prizeValue = form.prizeValue
    else if (formTypeKey.value === 5)
      prizeValue = Math.max(1, form.prizeValue || 1)

    const body: Record<string, unknown> = {
      name: form.name.trim(),
      prizeType: mystery ? 1 : formTypeKey.value,
      prizeValue,
      stockQuantity: form.stockQuantity,
      catalogStatus: form.id ? form.catalogStatus : 2,
      imagePath: form.imagePath?.trim() || null,
      isMysteryBundle: mystery ? 1 : 0,
      mysteryItems: mystery
        ? form.mysteryItems.map(m => ({ itemType: m.itemType, itemValue: m.itemValue, weight: m.weight }))
        : [],
    }
    if (form.id)
      body.id = Number(form.id)
    await saveLotteryPrize(body)
    Message.success('已保存')
    visible.value = false
    emit('success')
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
.lottery-prize-form-modal {
  :deep(.arco-modal-body) {
    max-height: min(82vh, 640px);
    padding: 18px 20px;
    overflow: auto;
    scrollbar-width: none;
    -ms-overflow-style: none;

    &::-webkit-scrollbar {
      display: none;
      width: 0;
      height: 0;
    }
  }
}

.prize-form {
  :deep(.arco-form-item) {
    margin-bottom: 14px;
  }
}

.field-extra {
  font-size: 11px;
  color: var(--color-text-3);
}

.mystery-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mystery-item-card {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.mystery-add-row {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: 10px 12px;
  font-size: 13px;
  color: var(--color-text-3);
  cursor: pointer;
  background: var(--color-fill-1);
  border: 1px dashed var(--color-border-3);
  border-radius: 8px;
  transition: border-color 0.15s, color 0.15s, background 0.15s;

  &:hover {
    color: rgb(var(--primary-6));
    background: rgb(var(--primary-1));
    border-color: rgb(var(--primary-4));
  }
}

.mystery-add-row__plus {
  font-size: 16px;
  font-weight: 600;
  line-height: 1;
}

.mystery-item-card__main {
  display: flex;
  flex: 1;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.mystery-item-card__text {
  font-size: 12px;
  color: var(--color-text-2);
}

.upload-row {
  display: flex;
  gap: 10px;
  align-items: center;
  width: 100%;
}

.upload-row__input {
  flex: 1;
  min-width: 0;
}

.upload-btn {
  flex-shrink: 0;
  color: rgb(var(--primary-6));
  background: rgb(var(--primary-1));
  border-color: rgb(var(--primary-3));
}

.prize-thumb-wrap {
  margin-top: 10px;
}

.prize-thumb {
  max-width: 100%;
  max-height: 140px;
  object-fit: contain;
  border-radius: 8px;
}
</style>
