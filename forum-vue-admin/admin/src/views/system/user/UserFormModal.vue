<template>
  <a-modal
    v-model:visible="visible"
    :title="title"
    width="calc(100% - 20px)"
    :mask-closable="false"
    :modal-style="{ maxWidth: '560px' }"
    :body-style="{ maxHeight: '70vh' }"
    @before-ok="save"
    @close="close"
  >
    <GiForm
      ref="GiFormRef"
      :model-value="form"
      :columns="formColumns"
      :grid-item-props="{ span: { xs: 24, sm: 24, md: 12, lg: 12, xl: 12, xxl: 12 } }"
      @update:model-value="Object.assign(form, $event)"
    />
  </a-modal>
</template>

<script setup lang="ts">
import type { FormColumnItem } from '@/components/index'
import { Message } from '@arco-design/web-vue'
import { baseAPI, updateUserRemark } from '@/apis/system/user'
import { GiForm } from '@/components/index'
import { useResetReactive } from '@/hooks'

const emit = defineEmits<{
  (e: 'save-success'): void
}>()

const GiFormRef = useTemplateRef<InstanceType<typeof GiForm>>('GiFormRef')
const detailId = ref('')
const isEdit = computed(() => !!detailId.value)
const title = computed(() => (isEdit.value ? '编辑用户' : '编辑用户'))
const visible = ref(false)

const [form, resetForm] = useResetReactive({
  id: '',
  username: '',
  nickname: '',
  description: '',
  disabled: false,
})

const formColumns = computed<FormColumnItem[]>(() => [
  {
    type: 'input',
    label: '用户名',
    field: 'username',
    props: { disabled: true },
    span: 24,
  },
  {
    type: 'input',
    label: '昵称',
    field: 'nickname',
    props: { disabled: true },
    span: 24,
  },
  {
    type: 'textarea',
    label: '管理员标签',
    field: 'description',
    props: {
      maxLength: 500,
      autoSize: { minRows: 3 },
      showWordLimit: true,
      placeholder: '仅管理端可见的管理员标签',
    },
    span: 24,
  },
])

const edit = async (id: string) => {
  visible.value = true
  detailId.value = id
  const res = await baseAPI.getDetail({ id })
  Object.assign(form, {
    id: res.data?.id ?? id,
    username: res.data?.username ?? '',
    nickname: res.data?.nickname ?? '',
    description: res.data?.description ?? '',
    disabled: res.data?.disabled ?? false,
  })
}

const close = () => {
  GiFormRef.value?.formRef?.resetFields()
  resetForm()
  detailId.value = ''
}

const save = async () => {
  if (!form.id)
    return false
  try {
    await updateUserRemark({
      id: form.id,
      remark: (form.description || '').trim(),
    })
    Message.success('已保存')
    emit('save-success')
    return true
  }
  catch {
    return false
  }
}

defineExpose({ edit })
</script>
