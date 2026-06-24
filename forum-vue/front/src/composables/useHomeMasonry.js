import { ref, watch, onMounted, onActivated, onBeforeUnmount, nextTick, unref, computed } from 'vue'

/**
 * 首页瀑布流：flex 等宽多列 + 轮询分配，列宽铺满容器（避免 CSS column-count 右侧留白）。
 */
export function useHomeMasonry(itemsSource, options = {}) {
  const columnWidth = options.columnWidth ?? 220
  const gap = options.gap ?? 16
  const containerRef = ref(null)
  const columnCount = ref(1)
  const columns = ref([])

  function measureWidth() {
    const el = unref(containerRef)
    if (!el) return 0
    const outlet = el.closest?.('.shell-main-outlet') || el.closest?.('.home-xhs-main')
    const w = outlet?.clientWidth || el.clientWidth || 0
    return w > 0 ? w : 0
  }

  function columnCountForWidth(width) {
    if (!width || width < columnWidth) return 1
    return Math.max(1, Math.floor((width + gap) / (columnWidth + gap)))
  }

  function redistribute() {
    const items = unref(itemsSource) || []
    const w = measureWidth()
    // keep-alive 隐藏时宽度为 0，此时重算会把全部帖子塞进一列导致回到首页样式崩坏
    if (!w || !items.length) return

    const n = columnCountForWidth(w)
    columnCount.value = n
    const cols = Array.from({ length: n }, () => [])
    items.forEach((item, i) => {
      cols[i % n].push(item)
    })
    columns.value = cols
  }

  let ro

  function bindObserver(el) {
    ro?.disconnect()
    if (!el || typeof ResizeObserver === 'undefined') return
    ro = new ResizeObserver(() => redistribute())
    ro.observe(el)
    const outlet = el.closest?.('.shell-main-outlet')
    const main = el.closest?.('.home-xhs-main')
    if (outlet && outlet !== el) ro.observe(outlet)
    if (main && main !== el && main !== outlet) ro.observe(main)
  }

  watch(
    () => unref(containerRef),
    (el) => {
      if (!el) return
      nextTick(() => {
        redistribute()
        bindObserver(el)
      })
    },
    { flush: 'post' },
  )

  watch(
    () => unref(itemsSource),
    () => nextTick(redistribute),
    { deep: true },
  )

  onMounted(() => {
    nextTick(redistribute)
    window.addEventListener('resize', redistribute)
  })

  onActivated(() => {
    nextTick(() => {
      redistribute()
      requestAnimationFrame(redistribute)
    })
  })

  onBeforeUnmount(() => {
    ro?.disconnect()
    window.removeEventListener('resize', redistribute)
  })

  const isEmpty = computed(() => !(unref(itemsSource) || []).length)

  return { containerRef, columns, columnCount, relayout: redistribute, isEmpty }
}
