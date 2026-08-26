// 幸运收集册：80 个展示图标（水果 / 物品 / 自然 / 食物）
export const COLLECT_TOTAL = 80
export const COLLECT_PAGE_SIZE = 16

const FRUITS = [
  '🍎', '🍐', '🍊', '🍋', '🍌', '🍉', '🍇', '🍓', '🫐', '🍈',
  '🍒', '🍑', '🥭', '🍍', '🥥', '🥝', '🍅', '🥑', '🫒', '🌶️',
]
const ITEMS = [
  '🎁', '🎀', '🎈', '🎉', '🎊', '🧸', '🪀', '🪁', '🔮', '💎',
  '🔑', '🕹️', '🎧', '📷', '⌚', '💡', '📚', '🧩', '🎯', '🏆',
]
const NATURE = [
  '⚡', '🔥', '💧', '❄️', '🌈', '⭐', '🌙', '☀️', '☁️', '🌪️',
  '🌊', '🍀', '🌸', '🌺', '🌻', '🌼', '🌷', '🌹', '🌿', '🌵',
]
const FOODS = [
  '🍞', '🧀', '🥚', '🍳', '🥓', '🥩', '🍗', '🍔', '🍟', '🍕',
  '🌭', '🥪', '🌮', '🌯', '🥙', '🧆', '🍝', '🍜', '🍣', '🍩',
]

export const COLLECT_ICONS = [...FRUITS, ...ITEMS, ...NATURE, ...FOODS]
  .slice(0, COLLECT_TOTAL)
  .map((emoji, index) => ({
    id: index + 1,
    emoji,
    group: index < 20 ? 'fruit' : index < 40 ? 'item' : index < 60 ? 'nature' : 'food',
  }))
