import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  ArrowRight,
  Check,
  CircleCheck,
  Medal,
  Opportunity,
  QuestionFilled,
} from '@element-plus/icons-vue'
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import {
  getGrowthChallenges,
  getGrowthOverview,
  startGrowthChallenge,
  submitGrowthChallenge,
} from '@/api/growth'
import { useUserStore } from '@/stores/user'

const CHALLENGE_PAGE_SIZE = 4
const QUESTION_MAP_PAGE_SIZE = 10

export function useGrowthCenter() {
  const userStore = useUserStore()
  const loading = ref(true)
  const error = ref('')
  const overview = ref(null)
  const challenges = ref([])
  const challengeLoading = ref(false)
  const challengeError = ref('')
  const challengePage = ref(1)
  const challengePages = ref(0)
  const challengeTotal = ref(0)
  const active = ref(null)
  const answers = ref({})
  const activeQuestionIndex = ref(0)
  const mapPage = ref(1)
  const submitting = ref(false)
  const levelDialogVisible = ref(false)

  const progress = computed(() => {
    const exp = Number(overview.value?.experience) || 0
    const next = Number(overview.value?.nextLevelExperience) || 100
    return Math.min(100, Math.round(exp * 100 / next))
  })
  const userTypeLabel = computed(() => overview.value?.formalUser ? '正式用户' : '非正式用户')
  const milestoneLevels = computed(() => {
    const currentLevel = Math.min(6, Math.max(1, Number(overview.value?.growthLevel) || 1))
    return Array.from({ length: 6 }, (_, index) => {
      const level = index + 1
      return {
        level,
        title: `Lv.${level}`,
        requirement: level === 1 ? '成长起点' : `累计 ${index * 100} XP`,
        ability: '等级能力与专属权益待规划',
        status: level === currentLevel ? 'current' : level < currentLevel ? 'complete' : 'upcoming',
      }
    })
  })
  const activeQuestion = computed(() => active.value?.questions?.[activeQuestionIndex.value] || null)
  const activeQuestionNo = computed(() => activeQuestionIndex.value + 1)
  const activeQuestionTotal = computed(() => active.value?.questions?.length || 0)
  const activeOptions = computed(() => {
    try {
      return JSON.parse(activeQuestion.value?.optionsJson || '[]')
    } catch {
      return []
    }
  })
  const answeredCount = computed(() => {
    const questions = active.value?.questions || []
    return questions.filter(question => String(answers.value[question.id] || '').trim()).length
  })
  const questionProgress = computed(() => {
    if (!activeQuestionTotal.value) return 0
    return Math.round(activeQuestionNo.value * 100 / activeQuestionTotal.value)
  })
  const mapPageCount = computed(() => Math.max(1, Math.ceil(activeQuestionTotal.value / QUESTION_MAP_PAGE_SIZE)))
  const pagedQuestions = computed(() => {
    const questions = active.value?.questions || []
    const start = (mapPage.value - 1) * QUESTION_MAP_PAGE_SIZE
    return questions.slice(start, start + QUESTION_MAP_PAGE_SIZE).map((question, offset) => ({
      question,
      index: start + offset,
    }))
  })

  async function loadChallengePage(pageNum = 1) {
    challengeLoading.value = true
    challengeError.value = ''
    try {
      const res = await getGrowthChallenges(pageNum, CHALLENGE_PAGE_SIZE)
      if (res.code !== 0) {
        challengeError.value = res.message || '成长挑战加载失败'
        return
      }
      challenges.value = res.data?.records || []
      challengePage.value = Number(res.data?.pageNum) || 1
      challengePages.value = Number(res.data?.pages) || 0
      challengeTotal.value = Number(res.data?.total) || 0
    } catch {
      challengeError.value = '成长挑战加载失败，请稍后重试'
    } finally {
      challengeLoading.value = false
    }
  }

  async function load() {
    loading.value = true
    error.value = ''
    try {
      const res = await getGrowthOverview()
      if (res.code === 0) {
        overview.value = res.data
        await loadChallengePage(1)
      } else {
        error.value = res.message || '成长中心加载失败'
      }
    } catch {
      error.value = '成长中心加载失败，请稍后重试'
    } finally {
      loading.value = false
    }
  }

  async function start(item) {
    try {
      const res = await startGrowthChallenge(item.challengeCode)
      if (res.code !== 0) {
        ElMessage.warning(res.message || '暂时无法开始挑战')
        return
      }
      active.value = res.data
      answers.value = {}
      activeQuestionIndex.value = 0
      mapPage.value = 1
    } catch {
      ElMessage.error('开始挑战失败')
    }
  }

  async function submit() {
    const questions = active.value?.questions || []
    if (questions.some(question => !String(answers.value[question.id] || '').trim())) {
      ElMessage.warning('请完成全部题目')
      return
    }
    submitting.value = true
    try {
      const res = await submitGrowthChallenge(active.value.challengeCode, {
        attemptId: active.value.attemptId,
        answers: questions.map(question => ({
          questionId: question.id,
          answer: answers.value[question.id],
        })),
      })
      if (res.code !== 0) {
        ElMessage.error(res.message || '提交失败')
        return
      }
      ElMessage.success(res.data.message)
      active.value = null
      await load()
    } catch {
      ElMessage.error('提交失败，请稍后重试')
    } finally {
      submitting.value = false
    }
  }

  function chooseAnswer(answer) {
    if (activeQuestion.value) {
      answers.value[activeQuestion.value.id] = answer
    }
  }

  function exitChallenge() {
    active.value = null
    answers.value = {}
    activeQuestionIndex.value = 0
    mapPage.value = 1
  }

  function selectQuestion(index) {
    activeQuestionIndex.value = index
    mapPage.value = Math.floor(index / QUESTION_MAP_PAGE_SIZE) + 1
  }

  function prevQuestion() {
    if (activeQuestionIndex.value > 0) {
      selectQuestion(activeQuestionIndex.value - 1)
    }
  }

  function nextQuestion() {
    if (activeQuestionIndex.value < activeQuestionTotal.value - 1) {
      selectQuestion(activeQuestionIndex.value + 1)
    }
  }

  function prevMapPage() {
    if (mapPage.value > 1) {
      mapPage.value -= 1
    }
  }

  function nextMapPage() {
    if (mapPage.value < mapPageCount.value) {
      mapPage.value += 1
    }
  }

  onMounted(load)

  return {
    active,
    activeOptions,
    activeQuestion,
    activeQuestionIndex,
    activeQuestionNo,
    activeQuestionTotal,
    answeredCount,
    answers,
    challengeError,
    challengeLoading,
    challengePage,
    challengePages,
    challenges,
    challengeTotal,
    chooseAnswer,
    error,
    exitChallenge,
    load,
    loadChallengePage,
    loading,
    levelDialogVisible,
    mapPage,
    mapPageCount,
    milestoneLevels,
    nextMapPage,
    nextQuestion,
    overview,
    pagedQuestions,
    prevMapPage,
    prevQuestion,
    progress,
    questionProgress,
    selectQuestion,
    start,
    submit,
    submitting,
    userStore,
    userTypeLabel,
    ArrowLeft,
    ArrowRight,
    Check,
    CircleCheck,
    Medal,
    Opportunity,
    QuestionFilled,
  }
}

export default {
  components: {
    UserAvatarVip,
  },
  setup() {
    return useGrowthCenter()
  },
}
