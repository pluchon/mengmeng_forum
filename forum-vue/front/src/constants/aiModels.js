/**
 * AI 模型元数据：文本对话 vs 生图分离，图标与 assets/svg 文件名对应。
 */
import iconQwen from '@/assets/svg/qwen-color.svg'
import iconOpenai from '@/assets/svg/openai.svg'

/** 按 provider 字段（会员配额 icon_provider） */
export const AI_PROVIDER_ICONS = {
  qwen: iconQwen,
  dashscope: iconQwen,
  openai: iconOpenai,
  huanapi: iconOpenai,
}

/** 按 model_code（精确匹配） */
export const AI_MODEL_ICONS_BY_CODE = {
  'qwen3.6-flash': iconQwen,
  'qwen3.7-max': iconQwen,
  'z-image-turbo': iconQwen,
  'gpt-image-2': iconOpenai,
  'wanx2.1-t2i-plus': iconQwen,
}

/** 看板娘 · 文本写作 / 站点帮助 / 帖子 AI 写作（不含生图） */
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

export function findImageQualityOption(id) {
  return MASCOT_IMAGE_QUALITY_OPTIONS.find((o) => o.id === id)
}
