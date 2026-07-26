/** 看板娘 · 模型思考态文案（随机语义） */

const PERSONA_BY_LLM = {
  'qwen-flash': 'Qwen',
  'qwen-deep': 'Qwen',
}

const PHRASE_TEMPLATES = [
  '{name}正在努力思考中......',
  '{name}思考中...',
  '{name}大脑正在飞速运转....',
  '{name}正在组织语言～',
  '{name}正在查阅资料......',
  '{name}灵光一闪中 ✨',
  '{name}正在为你琢磨最佳回复......',
  '{name}全力运转中......',
]

export function resolveThinkingPersona(llmId) {
  const id = (llmId || '').trim()
  if (PERSONA_BY_LLM[id]) return PERSONA_BY_LLM[id]
  if (id.startsWith('qwen')) return 'Qwen'
  return '小萌'
}

export function pickThinkingPhrase(llmId) {
  const name = resolveThinkingPersona(llmId)
  const tpl = PHRASE_TEMPLATES[Math.floor(Math.random() * PHRASE_TEMPLATES.length)]
  return tpl.replace(/\{name\}/g, name)
}

/** 思考阶段轮换文案，返回 stop 函数 */
export function startThinkingRotation(llmId, onUpdate, intervalMs = 2800) {
  onUpdate(pickThinkingPhrase(llmId))
  const timer = setInterval(() => {
    onUpdate(pickThinkingPhrase(llmId))
  }, intervalMs)
  return () => clearInterval(timer)
}
