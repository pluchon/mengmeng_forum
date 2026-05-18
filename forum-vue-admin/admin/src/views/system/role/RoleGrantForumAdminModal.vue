<template>
  <a-modal
    v-model:visible="visible"
    title="论坛管理员权限"
    width="420px"
    :on-before-ok="handleBeforeOk"
    @cancel="visible = false"
  >
    <a-form :model="form" layout="vertical">
      <a-form-item label="用户 ID（forum.user.id）" required>
        <a-input v-model="form.userId" placeholder="在用户管理中查看" allow-clear />
      </a-form-item>
      <a-form-item label="权限">
        <a-radio-group v-model="form.asAdmin">
          <a-radio :value="true">设为论坛管理员（可登录本后台）</a-radio>
          <a-radio :value="false">取消论坛管理员</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-typography-paragraph type="secondary" style="margin: 0">
        仅当前已登录的管理员可执行；内置账号 admin 不可被取消管理员；至少保留一名管理员。
      </a-typography-paragraph>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { Message } from '@arco-design/web-vue'
import { setForumAdmin } from '@/apis/system/user'

const visible = ref(false)

const form = reactive({
  userId: '',
  asAdmin: true
})

watch(visible, (v) => {
  if (v) {
    form.userId = ''
    form.asAdmin = true
  }
})

async function handleBeforeOk(): Promise<boolean> {
  const id = Number(form.userId.trim())
  if (!Number.isFinite(id) || id <= 0) {
    Message.warning('请输入有效的用户 ID')
    return false
  }
  try {
    await setForumAdmin({ id, isAdmin: form.asAdmin ? 1 : 0 })
    Message.success(form.asAdmin ? '已设为论坛管理员' : '已取消论坛管理员')
    return true
  } catch {
    return false
  }
}

defineExpose({
  open: () => {
    visible.value = true
  }
})
</script>
