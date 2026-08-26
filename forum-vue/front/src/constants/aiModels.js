import iconQwen from '@/assets/svg/qwen-color.svg'

export const AI_PROVIDER_ICONS = {
  qwen: iconQwen,
  dashscope: iconQwen,
}

export const AI_MODEL_ICONS_BY_CODE = {
  'qwen3.7-flash': iconQwen,
  'qwen3.7-max': iconQwen,
  'wan2.7-image': iconQwen,
  'wan2.7-image-pro': iconQwen,
}

export const MASCOT_IMAGE_QUALITY_OPTIONS = [
  {
    id: 'normal',
    label: 'Wan 2.7 Image',
    hint: 'wan2.7-image',
    icon: iconQwen,
    kind: 'image',
    modelCode: 'wan2.7-image',
    vipOnly: false,
  },
]

export const COVER_IMAGE_QUALITY_OPTIONS = [
  { value: 'normal', label: 'Wan 2.7（标准）', short: 'Wan 2.7', icon: iconQwen, modelCode: 'wan2.7-image' },
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
