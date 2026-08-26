<template>
  <div
    ref="containerRef"
    class="ui-masonry"
    :style="{ height: `${containerHeight}px` }"
  >
    <div
      v-for="item in layout.grid"
      :key="item.id"
      :data-key="item.id"
      class="ui-masonry-item"
      :style="{ width: `${item.w}px` }"
      @mouseenter="(e) => onItemEnter(item.id, e.currentTarget)"
      @mouseleave="(e) => onItemLeave(item.id, e.currentTarget)"
    >
      <slot :item="item.original" />
    </div>
  </div>
</template>

<script setup>
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  useTemplateRef,
  watch,
} from 'vue'
import { gsap } from 'gsap'

const props = defineProps({
  items: {
    type: Array,
    default: () => [],
  },
  ease: {
    type: String,
    default: 'power3.out',
  },
  duration: {
    type: Number,
    default: 0.6,
  },
  stagger: {
    type: Number,
    default: 0.05,
  },
  animateFrom: {
    type: String,
    default: 'bottom',
  },
  scaleOnHover: {
    type: Boolean,
    default: true,
  },
  hoverScale: {
    type: Number,
    default: 0.95,
  },
  blurToFocus: {
    type: Boolean,
    default: true,
  },
  colorShiftOnHover: {
    type: Boolean,
    default: false,
  },
  columnWidth: {
    type: Number,
    default: 282,
  },
  gap: {
    type: Number,
    default: 16,
  },
  maxColumns: {
    type: Number,
    default: 8,
  },
  defaultItemHeight: {
    type: Number,
    default: 360,
  },
  minItemHeight: {
    type: Number,
    default: 140,
  },
  reloadKey: {
    type: [String, Number],
    default: '',
  },
})

const containerRef = useTemplateRef('containerRef')
const size = ref({ width: 0, height: 0 })
const hasMounted = ref(false)
const measuredHeights = reactive({})
// 非响应式：在 computed 内写入粘性列，避免触发无限重算
const stickyColumnById = new Map()
const previousPositionById = new Map()
let lastColumnCount = 0
let resizeObserver = null
let itemResizeObserver = null
let measureScheduled = false

const normalizedItems = computed(() => props.items.map((item, index) => {
  const rawHeight = Number(item?.height)
  const fallbackId = String(item?.id || `masonry-item-${index}`)
  const id = String(item?.id ?? fallbackId)
  const estimated = Number.isFinite(rawHeight) && rawHeight > 0 ? rawHeight : props.defaultItemHeight
  const measured = Number(measuredHeights[id])
  const hasMeasured = Number.isFinite(measured) && measured > 0
  // 有实测就用实测：未结算时不再用估算撑高，避免同列下方出现空洞
  return {
    ...item,
    id,
    h: hasMeasured ? measured : estimated,
  }
}))

const layout = computed(() => {
  const list = normalizedItems.value
  if (!size.value.width || !list.length) {
    return { grid: [], height: 0, columns: 0 }
  }

  const byWidth = Math.floor((size.value.width + props.gap) / (props.columnWidth + props.gap))
  const columns = Math.max(1, Math.min(props.maxColumns, byWidth))
  if (columns !== lastColumnCount) {
    stickyColumnById.clear()
    lastColumnCount = columns
  }

  const colHeights = new Array(columns).fill(0)
  const columnWidth = (size.value.width - (columns - 1) * props.gap) / columns
  let totalHeight = 0

  const grid = list.map((item) => {
    let targetColumn = stickyColumnById.get(item.id)
    if (
      targetColumn == null
      || !Number.isInteger(targetColumn)
      || targetColumn < 0
      || targetColumn >= columns
    ) {
      targetColumn = colHeights.indexOf(Math.min(...colHeights))
      stickyColumnById.set(item.id, targetColumn)
    }

    const x = targetColumn * (columnWidth + props.gap)
    const h = Math.max(props.minItemHeight, Number(item.h) || props.minItemHeight)
    const y = colHeights[targetColumn]
    colHeights[targetColumn] += h + props.gap
    totalHeight = Math.max(totalHeight, y + h)

    return {
      ...item,
      x,
      y,
      w: columnWidth,
      h,
      column: targetColumn,
      original: item,
    }
  })

  return {
    grid,
    height: totalHeight,
    columns,
  }
})

const containerHeight = computed(() => Math.max(0, layout.value.height))
const grid = computed(() => layout.value.grid)

const layoutSignature = computed(() => grid.value
  .map((item) => `${item.id}:${Math.round(item.x)}:${Math.round(item.y)}:${Math.round(item.w)}:${Math.round(item.h)}`)
  .join('|'))

function clearLayoutState() {
  hasMounted.value = false
  lastColumnCount = 0
  Object.keys(measuredHeights).forEach((key) => {
    delete measuredHeights[key]
  })
  stickyColumnById.clear()
  previousPositionById.clear()
}

function getInitialPosition(item) {
  const rect = containerRef.value?.getBoundingClientRect()
  if (!rect) return { x: item.x, y: item.y }
  let direction = props.animateFrom
  if (props.animateFrom === 'random') {
    const directions = ['top', 'bottom', 'left', 'right']
    direction = directions[Math.floor(Math.random() * directions.length)]
  }
  switch (direction) {
    case 'top':
      return { x: item.x, y: -200 }
    case 'bottom':
      return { x: item.x, y: window.innerHeight + 200 }
    case 'left':
      return { x: -200, y: item.y }
    case 'right':
      return { x: window.innerWidth + 200, y: item.y }
    case 'center':
      return {
        x: rect.width / 2 - item.w / 2,
        y: rect.height / 2 - item.h / 2,
      }
    default:
      return { x: item.x, y: item.y + 100 }
  }
}

function itemSelector(id) {
  return `[data-key="${CSS.escape(String(id))}"]`
}

function onItemEnter(id, element) {
  if (!element || !props.scaleOnHover) return
  gsap.to(itemSelector(id), {
    scale: props.hoverScale,
    duration: 0.3,
    ease: 'power2.out',
  })
  if (!props.colorShiftOnHover) return
  const overlay = element.querySelector('.color-shift-overlay')
  if (overlay) gsap.to(overlay, { opacity: 0.3, duration: 0.3 })
}

function onItemLeave(id, element) {
  if (!element || !props.scaleOnHover) return
  gsap.to(itemSelector(id), {
    scale: 1,
    duration: 0.3,
    ease: 'power2.out',
  })
  if (!props.colorShiftOnHover) return
  const overlay = element.querySelector('.color-shift-overlay')
  if (overlay) gsap.to(overlay, { opacity: 0, duration: 0.3 })
}

function runLayoutAnimation() {
  const currentGrid = grid.value
  if (!currentGrid.length) return

  nextTick(() => {
    currentGrid.forEach((item, index) => {
      const selector = itemSelector(item.id)
      const targetProps = {
        x: item.x,
        y: item.y,
        width: item.w,
      }
      const prev = previousPositionById.get(item.id)
      if (!hasMounted.value || !prev) {
        const start = getInitialPosition(item)
        gsap.fromTo(
          selector,
          {
            opacity: 0,
            x: start.x,
            y: start.y,
            width: item.w,
            ...(props.blurToFocus ? { filter: 'blur(10px)' } : {}),
          },
          {
            opacity: 1,
            ...targetProps,
            ...(props.blurToFocus ? { filter: 'blur(0px)' } : {}),
            duration: 0.8,
            ease: 'power3.out',
            delay: index * props.stagger,
            overwrite: 'auto',
          },
        )
      } else {
        const sameColumn = Math.abs(prev.x - item.x) < 1
        const yDelta = item.y - prev.y
        // 同列高度变化时立刻贴合，避免上移缓动留下空隙、下移缓动造成重叠
        const snapY = sameColumn && Math.abs(yDelta) > 1
        gsap.to(selector, {
          ...targetProps,
          duration: snapY ? 0 : (sameColumn ? Math.min(0.28, props.duration) : props.duration),
          ease: props.ease,
          overwrite: 'auto',
        })
      }
      previousPositionById.set(item.id, { x: item.x, y: item.y })
    })
    hasMounted.value = true
    scheduleMeasure()
  })
}

function scheduleMeasure() {
  if (measureScheduled) return
  measureScheduled = true
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      measureScheduled = false
      measureItemHeights()
    })
  })
}

function measureItemHeights() {
  const root = containerRef.value
  if (!root) return
  for (const item of grid.value) {
    const node = root.querySelector(itemSelector(item.id))
    if (!node) continue
    // offsetHeight 不受 gsap scale/transform 影响，避免悬停缩放污染测高
    const nextH = Math.ceil(node.offsetHeight)
    if (!Number.isFinite(nextH) || nextH <= 0) continue
    if (Math.abs((measuredHeights[item.id] || 0) - nextH) >= 2) {
      measuredHeights[item.id] = nextH
    }
  }
}

function bindItemObservers() {
  if (typeof ResizeObserver === 'undefined') return
  itemResizeObserver?.disconnect()
  itemResizeObserver = new ResizeObserver(() => scheduleMeasure())
  const root = containerRef.value
  if (!root) return
  root.querySelectorAll('.ui-masonry-item').forEach((node) => {
    itemResizeObserver.observe(node)
  })
}

watch(
  layoutSignature,
  () => {
    if (!grid.value.length) {
      hasMounted.value = false
      return
    }
    runLayoutAnimation()
    nextTick(bindItemObservers)
  },
  { immediate: true },
)

watch(
  () => props.reloadKey,
  () => {
    clearLayoutState()
  },
)

watch(
  () => props.items.map((item) => String(item?.id ?? '')).join(','),
  () => {
    const alive = new Set((props.items || []).map((item) => String(item?.id ?? '')))
    Object.keys(measuredHeights).forEach((key) => {
      if (!alive.has(key)) delete measuredHeights[key]
    })
    for (const key of [...stickyColumnById.keys()]) {
      if (!alive.has(key)) stickyColumnById.delete(key)
    }
    for (const key of [...previousPositionById.keys()]) {
      if (!alive.has(key)) previousPositionById.delete(key)
    }
  },
)

onMounted(() => {
  const el = containerRef.value
  if (!el || typeof ResizeObserver === 'undefined') return
  resizeObserver = new ResizeObserver(([entry]) => {
    const nextSize = entry?.contentRect
    if (!nextSize) return
    const nextWidth = Math.round(nextSize.width)
    if (nextWidth === size.value.width) return
    size.value = {
      width: nextWidth,
      height: Math.round(nextSize.height),
    }
  })
  resizeObserver.observe(el)
  size.value = {
    width: el.clientWidth,
    height: el.clientHeight,
  }
  nextTick(() => {
    bindItemObservers()
    scheduleMeasure()
  })
})

onBeforeUnmount(() => {
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  if (itemResizeObserver) {
    itemResizeObserver.disconnect()
    itemResizeObserver = null
  }
})
</script>

<style scoped>
.ui-masonry {
  position: relative;
  width: 100%;
  height: auto;
  min-height: 10px;
  overflow: visible;
}

.ui-masonry-item {
  position: absolute;
  top: 0;
  left: 0;
  height: auto;
  transform: translate3d(0px, 0px, 0px);
  will-change: transform, width, opacity;
}
</style>
