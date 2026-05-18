<template>
  <a-modal
    v-model:visible="visible"
    :title="editIndex === null ? '添加神秘子项' : '编辑神秘子项'"
    width="min(420px, 92vw)"
    :mask-closable="false"
    unmount-on-close
    class="lottery-mystery-item-dialog"
    @cancel="visible = false"
    @before-ok="handleOk"
  >
    <a-form :model="form" layout="vertical">
      <a-form-item label="奖品类型" required>
        <a-select v-model="form.itemType" :options="itemTypeOptions" />
      </a-form-item>
      <a-form-item label="奖品数量（权重）" required>
        <a-input-number v-model="form.weight" :min="1" style="width: 100%" placeholder="开奖权重，建议 ≥ 1" />
      </a-form-item>
      <a-form-item v-if="form.itemType === 4" label="积分数值" required>
        <a-input-number v-model="form.itemValue" :min="1" :max="100" style="width: 100%" placeholder="1～100" />
      </a-form-item>
      <a-form-item v-else label="VIP 天数" required>
        <a-input-number v-model="form.itemValue" :min="1" style="width: 100%" placeholder="体验天数" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { Message } from '@arco-design/web-vue'

export interface MysteryItemForm {
  itemType: number
  itemValue: number
  weight: number
}

const props = defineProps<{
  editIndex: number | null
  initial?: MysteryItemForm | null
}>()

const visible = defineModel<boolean>('visible', { default: false })

const emit = defineEmits<{
  (e: 'confirm', payload: MysteryItemForm, index: number | null): void
}>()

const itemTypeOptions = [
  { label: '积分', value: 4 },
  { label: 'VIP天', value: 5 },
]

const form = reactive<MysteryItemForm>({
  itemType: 4,
  itemValue: 10,
  weight: 1,
})

watch(
  () => [visible.value, props.editIndex, props.initial] as const,
  ([vis]) => {
    if (!vis)
      return
    const init = props.initial
    form.itemType = init?.itemType ?? 4
    form.itemValue = init?.itemValue ?? (form.itemType === 4 ? 10 : 1)
    form.weight = init?.weight ?? 1
  },
)

watch(
  () => form.itemType,
  (t) => {
    if (t === 4 && (form.itemValue < 1 || form.itemValue > 100))
      form.itemValue = 10
    if (t === 5 && form.itemValue < 1)
      form.itemValue = 1
  },
)

function handleOk(): boolean {
  if (form.weight < 1) {
    Message.warning('请填写奖品数量（权重）')
    return false
  }
  if (form.itemType === 4) {
    if (form.itemValue < 1 || form.itemValue > 100) {
      Message.warning('积分须为 1～100')
      return false
    }
  }
  else if (form.itemValue < 1) {
    Message.warning('请填写 VIP 天数')
    return false
  }
  emit('confirm', { ...form }, props.editIndex)
  visible.value = false
  return true
}
</script>

<style scoped lang="scss">
.lottery-mystery-item-dialog {
  :deep(.arco-modal-body) {
    max-height: min(70vh, 480px);
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
</style>
