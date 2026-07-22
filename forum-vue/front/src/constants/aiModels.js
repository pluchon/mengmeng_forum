/**
 * AI 模型元数据：文本对话 vs 生图分离，图标与 assets/svg 文件名对应。
 */
import iconQwen from '@/assets/svg/qwen-color.svg'
import iconDeepseek from '@/assets/svg/deepseek-color.svg'
import iconOpenai from '@/assets/svg/openai.svg'

/** 按 provider 字段（会员配额 icon_provider） */
export const AI_PROVIDER_ICONS = {
  qwen: iconQwen,
  dashscope: iconQwen,
  deepseek: iconDeepseek,
  openai: iconOpenai,
  huanapi: iconOpenai,
}

/** 按 model_code（精确匹配） */
export const AI_MODEL_ICONS_BY_CODE = {
  'qwen3.6-flash': iconQwen,
  'qwen3.7-max': iconQwen,
  'deepseek-v4-flash': iconDeepseek,
  'deepseek-v4-pro': iconDeepseek,
  'z-image-turbo': iconQwen,
  'gpt-image-2': iconOpenai,
  'wanx2.1-t2i-plus': iconQwen,
}

/** 看板娘 · 文本写作 / 站点帮助 / 帖子 AI 写作（不含生图） */
export const MASCOT_TEXT_LLM_OPTIONS = [
  { id: 'qwen-flash', label: '通义千问', hint: 'qwen3.6-flash', icon: iconQwen, kind: 'text', vipOnly: false },
  { id: 'deepseek-flash', label: 'DeepSeek', hint: 'deepseek-v4-flash', icon: iconDeepseek, kind: 'text', vipOnly: false },
  { id: 'qwen-deep', label: '通义千问 · 深度', hint: 'qwen3.7-max', icon: iconQwen, kind: 'text', vipOnly: true },
  { id: 'deepseek-deep', label: 'DeepSeek · 深度', hint: 'deepseek-v4-pro', icon: iconDeepseek, kind: 'text', vipOnly: true },
]

/** 看板娘 · 画图模式（仅生图模型） */
export const MASCOT_IMAGE_QUALITY_OPTIONS = [
  {
    id: 'normal',
    label: 'Z-Image Turbo',
    hint: 'z-image-turbo',
    icon: iconQwen,
    kind: 'image',
    modelCode: 'z-image-turbo',
    vipOnly: false,
  },
  {
    id: 'premium',
    label: 'GPT Image 2',
    hint: 'gpt-image-2',
    icon: iconOpenai,
    kind: 'image',
    modelCode: 'gpt-image-2',
    vipOnly: true,
  },
]

/** 封面 AI 生图（与看板娘普通/进阶一致） */
export const COVER_IMAGE_QUALITY_OPTIONS = [
  { value: 'normal', label: 'Z-Image Turbo（标准）', short: 'Z-Image', icon: iconQwen, modelCode: 'z-image-turbo' },
  { value: 'premium', label: 'GPT Image 2（进阶）', short: 'GPT Image', icon: iconOpenai, modelCode: 'gpt-image-2' },
]

export const MASCOT_FLASH_LLM_IDS = ['qwen-flash', 'deepseek-flash']

/** 帖子编辑页 AI 写作：路由 id -> Java /ai/write kind */
export function llmRouteToWriteKind(routeId) {
  const map = {
    'qwen-flash': 'qwen_flash',
    'qwen-deep': 'qwen_pro',
    'deepseek-flash': 'deepseek_flash',
    'deepseek-deep': 'deepseek_pro',
  }
  return map[routeId] || null
}

export function resolveAiIcon(provider, modelCode) {
  if (modelCode && AI_MODEL_ICONS_BY_CODE[modelCode]) {
    return AI_MODEL_ICONS_BY_CODE[modelCode]
  }
  if (provider && AI_PROVIDER_ICONS[provider]) {
    return AI_PROVIDER_ICONS[provider]
  }
  return iconQwen
}

export function providerIcon(provider) {
  return resolveAiIcon(provider, null)
}

export function modelIcon(modelCode) {
  return resolveAiIcon(null, modelCode)
}

export function findTextLlmOption(id) {
  return MASCOT_TEXT_LLM_OPTIONS.find((o) => o.id === id)
}

export function findImageQualityOption(id) {
  return MASCOT_IMAGE_QUALITY_OPTIONS.find((o) => o.id === id)
}
