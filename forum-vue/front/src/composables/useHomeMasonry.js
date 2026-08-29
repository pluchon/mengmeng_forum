import { ref, watch, onMounted, onActivated, onBeforeUnmount, nextTick, unref, computed } from 'vue'

// 首页瀑布流：固定列宽多列 + 轮询分配；宽屏目标 5 列，窄屏按可用宽度降列
export function useHomeMasonry(itemsSource, options = {}) {
  const columnWidth = options.columnWidth ?? 220
  const gap = options.gap ?? 16
  const maxColumns = options.maxColumns ?? 5
  const containerRef = ref(null)
  const columnCount = ref(1)
  const columns = ref([])

  function measureWidth() {
    const el = unref(containerRef)
    if (!el) return 0
    const outlet = el.closest?.('.shell-main-outlet') || el.closest?.('.home-xhs-main')
    const w = el.clientWidth || outlet?.clientWidth || 0
    return w > 0 ? w : 0
  }

  function columnCountForWidth(width) {
    if (!width || width < columnWidth) return 1
    const byWidth = Math.floor((width + gap) / (columnWidth + gap))
    return Math.min(maxColumns, Math.max(1, byWidth))
  }

  function redistribute() {
    const items = unref(itemsSource) || []
    const w = measureWidth()
    // keep alive 隐藏时宽度为 0，此时重算会把全部帖子塞进一列导致回到首页样式崩坏
    if (!w || !items.length) return

    const n = columnCountForWidth(w)
    columnCount.value = n
    const cols = Array.from({ length: n }, () => [])
    // 原本是 i % n 轮询分配，跟瀑布流没关系：连续几张长图落在同一列
    // 就会比别人长出一大截。item.height 是卡片自己算好的高度估算，
    // 之前一直没人用，这里拿来把下一张放进当前最矮的那列
    const heights = new Array(n).fill(0)
    items.forEach((item) => {
      let target = 0
      for (let c = 1; c < n; c += 1) {
        if (heights[c] < heights[target]) target = c
      }
      cols[target].push(item)
      heights[target] += Number(item?.height) > 0 ? Number(item.height) : 320
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

  // 不用 deep：itemsSource 是 computed，每次重算都是新数组，浅层比较已经会触发；
  // deep 只是白白把每个 entry 的所有属性都遍历一遍
  watch(
    () => unref(itemsSource),
    () => nextTick(redistribute),
  )

  onMounted(() => {
    nextTick(redistribute)
    window.addEventListener('resize', redistribute)
  })

  // 返回首页（keep-alive 激活）正好和详情关闭动画的落地时刻重合，
  // 连着全量重排两次会让那一下更明显，留 rAF 那次即可
  onActivated(() => {
    nextTick(() => {
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
