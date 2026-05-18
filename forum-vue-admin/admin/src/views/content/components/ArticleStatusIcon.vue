<template>
  <a-tooltip :content="tip" class="article-status-icon-wrap">
    <img class="article-status-icon" :src="iconSrc" :alt="tip">
  </a-tooltip>
</template>

<script setup lang="ts">
import iconDisabled from '@/assets/svg/禁用.svg'
import iconDeleted from '@/assets/svg/已删除.svg'
import iconAuditing from '@/assets/svg/审核中.svg'
import iconAuditError from '@/assets/svg/审核异常.svg'
import iconAuditFail from '@/assets/svg/审核失败.svg'
import iconPublished from '@/assets/svg/对勾.svg'

const props = defineProps<{
  status: number
  state: number
  deleteState: number
}>()

const resolved = computed(() => {
  if (props.deleteState === 1) {
    return { src: iconDeleted, tip: '已删除' }
  }
  if (props.state === 1) {
    return { src: iconDisabled, tip: '已禁用' }
  }
  switch (props.status) {
    case 1:
      return { src: iconAuditing, tip: '审核中' }
    case 3:
      return { src: iconAuditFail, tip: '审核未通过' }
    case 4:
      return { src: iconAuditError, tip: '审核异常' }
    case 5:
    case 2:
      return { src: iconPublished, tip: props.status === 5 ? '已发布' : '审核通过' }
    case 0:
      return { src: iconAuditing, tip: '草稿' }
    default:
      return { src: iconAuditing, tip: `状态 ${props.status}` }
  }
})

const iconSrc = computed(() => resolved.value.src)
const tip = computed(() => resolved.value.tip)
</script>

<style scoped>
.article-status-icon-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.article-status-icon {
  display: block;
  width: 22px;
  height: 22px;
  object-fit: contain;
}
</style>
