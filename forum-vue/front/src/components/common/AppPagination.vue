<template>
  <div
    v-if="visible"
    ref="hostRef"
    class="app-pagination-host"
  >
    <nav
      class="app-pagination"
      :class="{
        'is-disabled': disabled,
        'is-small': isCompact,
      }"
      aria-label="分页"
    >
      <button
        type="button"
        class="app-pagination__btn"
        aria-label="首页"
        :disabled="disabled || current <= 1"
        @click="goFirst"
      >
        «
      </button>
      <button
        type="button"
        class="app-pagination__btn"
        aria-label="上一页"
        :disabled="disabled || current <= 1"
        @click="goPrev"
      >
        ‹
      </button>

      <template v-for="item in pageItems" :key="item.type === 'page' ? `p-${item.page}` : `e-${item.key}`">
        <button
          v-if="item.type === 'page'"
          type="button"
          class="app-pagination__btn app-pagination__page"
          :class="{ 'is-active': item.page === current }"
          :aria-current="item.page === current ? 'page' : undefined"
          :disabled="disabled"
          @click="goTo(item.page)"
        >
          {{ item.page }}
        </button>
        <span v-else class="app-pagination__ellipsis" aria-hidden="true">…</span>
      </template>

      <button
        type="button"
        class="app-pagination__btn"
        aria-label="下一页"
        :disabled="disabled || current >= pageCount"
        @click="goNext"
      >
        ›
      </button>
      <button
        type="button"
        class="app-pagination__btn"
        aria-label="尾页"
        :disabled="disabled || current >= pageCount"
        @click="goLast"
      >
        »
      </button>

      <div v-if="shouldShowJumper" class="app-pagination__jumper">
        <span class="app-pagination__jumper-label">跳至</span>
        <input
          v-model="jumpInput"
          class="app-pagination__jumper-input"
          type="text"
          inputmode="numeric"
          :disabled="disabled"
          aria-label="跳转页码"
          @keydown="onJumpKeydown"
          @blur="applyJump"
        >
        <span class="app-pagination__jumper-label">页</span>
      </div>
    </nav>
  </div>
</template>

<script setup>
import { useAppPagination } from '@scripts/components/common/AppPagination'

const props = defineProps({
  total: { type: Number, default: 0 },
  pageSize: { type: Number, default: 10 },
  currentPage: { type: Number, default: 1 },
  disabled: { type: Boolean, default: false },
  pagerCount: { type: Number, default: 7 },
  // true / false 强制；不传则总页数 > 7 且宽度够时显示
  showJumper: { type: Boolean, default: undefined },
  hideOnSinglePage: { type: Boolean, default: true },
  // auto | default | small；auto 按容器宽度切换
  size: {
    type: String,
    default: 'auto',
    validator: (v) => ['auto', 'default', 'small'].includes(v),
  },
})

const emit = defineEmits(['update:currentPage', 'current-change'])

const {
  applyJump,
  current,
  goFirst,
  goLast,
  goNext,
  goPrev,
  goTo,
  hostRef,
  isCompact,
  jumpInput,
  onJumpKeydown,
  pageCount,
  pageItems,
  shouldShowJumper,
  visible,
} = useAppPagination(props, emit)
</script>

<style scoped src="@/assets/styles/app-pagination.css"></style>
