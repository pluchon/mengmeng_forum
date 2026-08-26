import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

function clampOddPagerCount(value, min = 5, max = 21) {
  let n = Math.floor(Number(value) || min)
  if (n < min) n = min
  if (n > max) n = max
  if (n % 2 === 0) n -= 1
  if (n < min) n = min
  return n
}

function buildPageItems(totalPages, cur, pagerCount) {
  const count = clampOddPagerCount(pagerCount, 5)
  if (totalPages <= count) {
    return Array.from({ length: totalPages }, (_, i) => ({ type: 'page', page: i + 1 }))
  }

  const items = []
  const side = Math.floor((count - 3) / 2)
  let start = Math.max(2, cur - side)
  let end = Math.min(totalPages - 1, cur + side)

  if (cur - 1 <= side + 1) {
    start = 2
    end = count - 2
  } else if (totalPages - cur <= side + 1) {
    start = totalPages - (count - 3)
    end = totalPages - 1
  }

  items.push({ type: 'page', page: 1 })
  if (start > 2) items.push({ type: 'ellipsis', key: 'left' })
  for (let p = start; p <= end; p += 1) {
    items.push({ type: 'page', page: p })
  }
  if (end < totalPages - 1) items.push({ type: 'ellipsis', key: 'right' })
  items.push({ type: 'page', page: totalPages })
  return items
}

export function useAppPagination(props, emit) {
  const jumpInput = ref('')
  const hostRef = ref(null)
  const containerWidth = ref(0)
  let resizeObserver = null

  const pageCount = computed(() => {
    const size = Math.max(1, Number(props.pageSize) || 10)
    const total = Math.max(0, Number(props.total) || 0)
    return Math.max(1, Math.ceil(total / size))
  })

  const current = computed(() => {
    const page = Math.floor(Number(props.currentPage) || 1)
    return Math.min(pageCount.value, Math.max(1, page))
  })

  const isCompact = computed(() => {
    if (props.size === 'small') return true
    if (props.size === 'default') return false
    const w = containerWidth.value
    return w > 0 ? w < 440 : true
  })

  const maxPagerCount = computed(() => clampOddPagerCount(props.pagerCount || 7, 5))

  // 估算页码区最多能放几个「格子」（页码或省略号）；导航含 « ‹ › »
  const fittedPageSlots = computed(() => {
    const w = containerWidth.value
    if (!w) return Math.min(5, maxPagerCount.value)
    const btn = isCompact.value ? 26 : 32
    const gap = isCompact.value ? 4 : 6
    const navCount = 4
    let budget = w - navCount * btn - (navCount - 1) * gap - 8
    if (props.showJumper === true && w >= 520) budget -= 96
    const slots = Math.floor((budget + gap) / (btn + gap))
    // 至少预留 3 格，避免多页时塌成只剩当前页
    return Math.max(3, Math.min(maxPagerCount.value, Math.max(1, slots)))
  })

  const shouldShowJumper = computed(() => {
    if (props.showJumper === false) return false
    if (props.showJumper === true) {
      return containerWidth.value <= 0 || containerWidth.value >= 420
    }
    if (pageCount.value <= 7) return false
    return containerWidth.value <= 0 || containerWidth.value >= 520
  })

  const visible = computed(() => {
    // hideOnSinglePage=false：0 条 / 仅 1 页也展示，保证布局占位稳定
    if (!props.hideOnSinglePage) return true
    if (pageCount.value <= 1) return false
    return (Number(props.total) || 0) > 0 || pageCount.value > 1
  })

  const pageItems = computed(() => {
    const totalPages = pageCount.value
    const cur = current.value
    const slots = fittedPageSlots.value

    if (totalPages <= slots) {
      return Array.from({ length: totalPages }, (_, i) => ({ type: 'page', page: i + 1 }))
    }

    // 较窄：至少露出首页 / 当前 / 尾页，完整翻页靠 « ‹ › »
    if (slots < 5) {
      if (cur === 1) {
        return [
          { type: 'page', page: 1 },
          { type: 'ellipsis', key: 'right' },
          { type: 'page', page: totalPages },
        ]
      }
      if (cur === totalPages) {
        return [
          { type: 'page', page: 1 },
          { type: 'ellipsis', key: 'left' },
          { type: 'page', page: totalPages },
        ]
      }
      return [
        { type: 'page', page: 1 },
        { type: 'page', page: cur },
        { type: 'page', page: totalPages },
      ]
    }

    return buildPageItems(totalPages, cur, Math.min(slots, maxPagerCount.value))
  })

  watch(
    current,
    (page) => {
      jumpInput.value = String(page)
    },
    { immediate: true },
  )

  function measure() {
    const el = hostRef.value
    if (!el) return
    containerWidth.value = Math.floor(el.getBoundingClientRect().width || el.clientWidth || 0)
  }

  function bindObserver() {
    resizeObserver?.disconnect()
    const el = hostRef.value
    if (!el || typeof ResizeObserver === 'undefined') return
    resizeObserver = new ResizeObserver(() => measure())
    resizeObserver.observe(el)
  }

  onMounted(() => {
    measure()
    bindObserver()
    requestAnimationFrame(measure)
  })

  onBeforeUnmount(() => {
    resizeObserver?.disconnect()
    resizeObserver = null
  })

  function goTo(page) {
    if (props.disabled) return
    const target = Math.min(pageCount.value, Math.max(1, Math.floor(Number(page) || 0)))
    if (!target || target === current.value) return
    emit('update:currentPage', target)
    emit('current-change', target)
  }

  function goFirst() {
    goTo(1)
  }

  function goPrev() {
    goTo(current.value - 1)
  }

  function goNext() {
    goTo(current.value + 1)
  }

  function goLast() {
    goTo(pageCount.value)
  }

  function applyJump() {
    goTo(jumpInput.value)
    jumpInput.value = String(current.value)
  }

  function onJumpKeydown(event) {
    if (event.key === 'Enter') {
      event.preventDefault()
      applyJump()
    }
  }

  return {
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
  }
}
