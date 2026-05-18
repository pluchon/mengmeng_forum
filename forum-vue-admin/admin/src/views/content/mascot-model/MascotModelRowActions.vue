<template>
  <div class="mascot-table-actions">
    <a-link type="primary" @click="emit('edit')">
      编辑
    </a-link>
    <Popconfirm
      :content="onShelf ? '确认下架该模型？' : '确认上架该模型？'"
      @before-ok="onShelfToggle"
    >
      <a-link>{{ onShelf ? '下架' : '上架' }}</a-link>
    </Popconfirm>
    <template v-if="row.deleteState === 0">
      <Popconfirm content="确认标记删除？" @before-ok="onDelete">
        <a-link class="mascot-del-btn">
          <img class="mascot-del-icon" :src="iconDeleted" alt="删除">
        </a-link>
      </Popconfirm>
    </template>
    <Popconfirm v-else content="确认恢复？" @before-ok="onRestore">
      <a-link>恢复</a-link>
    </Popconfirm>
  </div>
</template>

<script setup lang="ts">
import { Popconfirm } from '@arco-design/web-vue'
import type { MascotModelRow } from '@/apis/content/mascotModel'
import iconDeleted from '@/assets/svg/已删除.svg'

const props = defineProps<{
  row: MascotModelRow
}>()

const emit = defineEmits<{
  edit: []
  shelf: [shelfStatus: number]
  delete: []
  restore: []
}>()

const onShelf = computed(() => props.row.shelfStatus === 1)

async function onShelfToggle() {
  emit('shelf', onShelf.value ? 2 : 1)
  return true
}

async function onDelete() {
  emit('delete')
  return true
}

async function onRestore() {
  emit('restore')
  return true
}
</script>

<style scoped>
.mascot-table-actions {
  display: inline-flex;
  flex-wrap: nowrap;
  gap: 12px;
  align-items: center;
  justify-content: center;
}

.mascot-del-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.mascot-del-icon {
  display: block;
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  object-fit: contain;
}
</style>
