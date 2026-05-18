<template>
  <span class="mascot-shelf-icon">
    <icon-check-circle-fill v-if="onShelf" class="mascot-shelf-icon__ok" />
    <icon-close-circle-fill v-else class="mascot-shelf-icon__no" />
  </span>
</template>

<script setup lang="ts">
const props = defineProps<{
  shelfStatus: number
}>()

const onShelf = computed(() => props.shelfStatus === 1)
</script>

<style scoped>
.mascot-shelf-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  line-height: 1;
}

.mascot-shelf-icon__ok {
  color: rgb(var(--green-6));
}

.mascot-shelf-icon__no {
  color: rgb(var(--red-6));
}
</style>
