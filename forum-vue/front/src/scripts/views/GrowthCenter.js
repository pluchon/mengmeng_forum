import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowRight, CircleCheck, Medal, Opportunity, Trophy } from '@element-plus/icons-vue'
import { getGrowthOverview, startGrowthChallenge, submitGrowthChallenge } from '@/api/growth'

export function useGrowthCenter() {
  const loading = ref(true)
  const error = ref('')
  const overview = ref(null)
  const active = ref(null)
  const answers = ref({})
  const activeQuestionIndex = ref(0)
  const submitting = ref(false)
  const progress = computed(() => {
    const exp = Number(overview.value?.experience) || 0
    const next = Number(overview.value?.nextLevelExperience) || 100
    return Math.min(100, Math.round(exp * 100 / next))
  })
  const activeQuestion = computed(() => active.value?.questions?.[activeQuestionIndex.value] || null)
  const activeQuestionNo = computed(() => activeQuestionIndex.value + 1)
  const activeQuestionTotal = computed(() => active.value?.questions?.length || 0)
  async function load() {
    loading.value = true; error.value = ''
    try { const res = await getGrowthOverview(); if (res.code === 0) overview.value = res.data; else error.value = res.message || '成长中心加载失败' } catch { error.value = '成长中心加载失败，请稍后重试' } finally { loading.value = false }
  }
  async function start(item) {
    try { const res = await startGrowthChallenge(item.challengeCode); if (res.code !== 0) return ElMessage.warning(res.message || '暂时无法开始挑战'); active.value = res.data; answers.value = {}; activeQuestionIndex.value = 0 } catch { ElMessage.error('开始挑战失败') }
  }
  async function submit() {
    const questions = active.value?.questions || []
    if (questions.some(q => !String(answers.value[q.id] || '').trim())) return ElMessage.warning('请完成全部题目')
    submitting.value = true
    try { const res = await submitGrowthChallenge(active.value.challengeCode, { attemptId: active.value.attemptId, answers: questions.map(q => ({ questionId: q.id, answer: answers.value[q.id] })) }); if (res.code !== 0) return ElMessage.error(res.message || '提交失败'); ElMessage.success(res.data.message); active.value = null; await load() } catch { ElMessage.error('提交失败，请稍后重试') } finally { submitting.value = false }
  }
  onMounted(load)
  function selectQuestion(index) { activeQuestionIndex.value = index }
  function prevQuestion() { if (activeQuestionIndex.value > 0) activeQuestionIndex.value-- }
  function nextQuestion() { if (activeQuestionIndex.value < activeQuestionTotal.value - 1) activeQuestionIndex.value++ }
  return { active, activeQuestion, activeQuestionIndex, activeQuestionNo, activeQuestionTotal, answers, error, load, loading, nextQuestion, overview, prevQuestion, progress, selectQuestion, start, submit, submitting, ArrowRight, CircleCheck, Medal, Opportunity, Trophy }
}

export default {
  setup() {
    return useGrowthCenter()
  },
}
