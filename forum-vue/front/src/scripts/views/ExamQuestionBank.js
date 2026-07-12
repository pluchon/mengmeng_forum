import { computed, defineComponent, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Aim,
  Check,
  CircleCheck,
  CircleClose,
  Document,
  DocumentChecked,
  Edit,
  Lock,
  MagicStick,
  Reading,
  RefreshLeft,
  School,
  UploadFilled,
} from '@element-plus/icons-vue'
import {
  analyzeExamWord,
  getExamBankSubjects,
  getExamQuestionProgress,
  getLatestExamBank,
  judgeSubjectiveAnswer,
  saveExamQuestionProgress,
  updateExamQuestion,
} from '@/api/examQuestionBank'
import { maogaiQuestionBank } from '@/data/maogaiQuestionBank'
import { useUserStore } from '@/stores/user'
import '@/views/ExamQuestionBank.scss'

const OBJECTIVE_TYPES = ['single', 'multiple', 'judgement']
const MAP_PAGE_SIZE = 35
const BOOK_FILTERS = ['focusBook', 'wrongBook']
const DEFAULT_SUBJECT = '毛概'

const filterOptions = [
  { label: '全部', value: 'all' },
  { label: '选择', value: 'choice' },
  { label: '判断', value: 'judgement' },
  { label: '大题', value: 'subjective' },
]

const singleFilterOptions = {
  choice: [{ label: '选择', value: 'all' }],
  judgement: [{ label: '判断', value: 'all' }],
  subjective: [{ label: '大题', value: 'all' }],
}

export default defineComponent({
  name: 'ExamQuestionBank',
  components: {
    Aim,
    Check,
    CircleCheck,
    CircleClose,
    Document,
    DocumentChecked,
    Edit,
    Lock,
    MagicStick,
    Reading,
    RefreshLeft,
    School,
    UploadFilled,
  },
  setup() {
const userStore = useUserStore()
const pageLoading = ref(false)
const analyzing = ref(false)
const showManagePanel = ref(false)
const subject = ref(DEFAULT_SUBJECT)
const subjectOptions = ref([DEFAULT_SUBJECT])
const currentSubject = ref(DEFAULT_SUBJECT)
const selectedFile = ref(null)
const uploadFiles = ref([])
const questions = ref([])
const bankId = ref(null)
const warnings = ref([])
const errorMessage = ref('')
const permissionError = ref('')
const activeFilter = ref('all')
const activeMapFilter = ref('all')
const studyMode = ref('practice')
const modeView = ref('overview')
const activeIndex = ref(0)
const mapPage = ref(1)
const answers = reactive({})
const objectiveChecked = reactive({})
const answerRevealed = reactive({})
const judgeResults = reactive({})
const scoring = reactive({})
const progressRecords = reactive({})
const showEditDialog = ref(false)
const editSaving = ref(false)
let freezeWatchdogTimer = null
let lastFreezeReportAt = 0
const editForm = reactive({
  stem: '',
  answer: '',
  explanation: '',
  options: [],
})
const isExamAdmin = computed(() => Number(userStore.isAdmin) === 1)

const questionBuckets = computed(() => {
  const choice = []
  const judgement = []
  const subjective = []
  questions.value.forEach((item) => {
    if (item.type === 'single' || item.type === 'multiple') {
      choice.push(item)
    } else if (item.type === 'judgement') {
      judgement.push(item)
    } else if (item.type === 'subjective') {
      subjective.push(item)
    }
  })
  return {
    all: questions.value,
    choice,
    judgement,
    subjective,
  }
})

const questionById = computed(() => {
  const result = new Map()
  questions.value.forEach((item) => {
    result.set(String(item.id), item)
  })
  return result
})

const progressSummary = computed(() => {
  let answered = 0
  const focus = []
  const wrong = []
  questions.value.forEach((item) => {
    if (isAnswered(item)) {
      answered++
    }
    if (isFocusQuestion(item)) {
      focus.push(item)
    }
    if (isWrongQuestion(item)) {
      wrong.push(item)
    }
  })
  return { answered, focus, wrong }
})

const stats = computed(() => {
  const buckets = questionBuckets.value
  return {
    total: buckets.all.length,
    choice: buckets.choice.length,
    judgement: buckets.judgement.length,
    subjective: buckets.subjective.length,
    focus: progressSummary.value.focus.length,
    wrong: progressSummary.value.wrong.length,
  }
})

const topicCards = computed(() => [
  {
    title: '选择题训练',
    value: 'choice',
    count: stats.value.choice,
    percent: percent(stats.value.choice, stats.value.total),
  },
  {
    title: '判断题训练',
    value: 'judgement',
    count: stats.value.judgement,
    percent: percent(stats.value.judgement, stats.value.total),
  },
  {
    title: '大题背诵',
    value: 'subjective',
    count: stats.value.subjective,
    percent: percent(stats.value.subjective, stats.value.total),
  },
  {
    title: '重点记忆本',
    value: 'focusBook',
    count: stats.value.focus,
    percent: percent(stats.value.focus, stats.value.total),
  },
  {
    title: '错题本',
    value: 'wrongBook',
    count: stats.value.wrong,
    percent: percent(stats.value.wrong, stats.value.total),
  },
])

const visibleQuestions = computed(() => {
  return filterQuestions(activeFilter.value, activeMapFilter.value)
})

const activeQuestion = computed(() => visibleQuestions.value[activeIndex.value] || null)
const emptyState = computed(() => !pageLoading.value && questions.value.length === 0)
const completionPercent = computed(() => percent(answeredCount.value, stats.value.total))
const answeredCount = computed(() => progressSummary.value.answered)
const warningText = computed(() => {
  if (!warnings.value.length) return ''
  const head = warnings.value.slice(0, 2).join('；')
  return warnings.value.length > 2 ? `${head}；另有 ${warnings.value.length - 2} 条提醒` : head
})
const isObjectiveActive = computed(() => activeQuestion.value && OBJECTIVE_TYPES.includes(activeQuestion.value.type))
const isSubjectiveActive = computed(() => activeQuestion.value?.type === 'subjective')
const activeTypeLabel = computed(() => typeLabel(activeQuestion.value?.type))
const activeDisplayNo = computed(() => activeIndex.value + 1)
const activeObjectiveChecked = computed(() => !!activeQuestion.value && !!objectiveChecked[activeQuestion.value.id])
const activeCorrect = computed(() => isActiveObjectiveCorrect())
const activeObjectiveResultText = computed(() => {
  if (!activeQuestion.value || !activeObjectiveChecked.value) return ''
  return activeCorrect.value ? '回答正确' : '回答错误'
})
const activeObjectiveResultClass = computed(() => ({
  'is-correct': activeObjectiveChecked.value && activeCorrect.value,
  'is-wrong': activeObjectiveChecked.value && !activeCorrect.value,
}))
const activeJudgeResult = computed(() => activeQuestion.value ? judgeResults[activeQuestion.value.id] : null)
const activeScoring = computed(() => activeQuestion.value ? !!scoring[activeQuestion.value.id] : false)
const activeProgress = computed(() => activeQuestion.value ? progressRecords[activeQuestion.value.id] : null)
const shouldRevealAnswer = computed(() => {
  const question = activeQuestion.value
  if (!question) return false
  return studyMode.value === 'memory'
})
const isOptionLocked = computed(() => {
  const question = activeQuestion.value
  if (!question) return true
  return studyMode.value === 'memory' || !!objectiveChecked[question.id]
})
const showExplanationPanel = computed(() => {
  const question = activeQuestion.value
  if (!question || !shouldRevealAnswer.value) return false
  return isSubjectiveActive.value || !!question.explanation
})
const isBookMode = computed(() => BOOK_FILTERS.includes(activeFilter.value))
const bookActionLabel = computed(() => activeFilter.value === 'wrongBook' ? '从错题本移除这道题' : '从重点记忆本移除这道题')
const navFilterOptions = computed(() => singleFilterOptions[activeFilter.value] || filterOptions)
const showNavFilterRow = computed(() => !singleFilterOptions[activeFilter.value])
const questionProgress = computed(() => percent(activeIndex.value + 1, Math.max(visibleQuestions.value.length, 1)))
const mapPageCount = computed(() => Math.max(1, Math.ceil(visibleQuestions.value.length / MAP_PAGE_SIZE)))
const mapRangeText = computed(() => {
  if (!visibleQuestions.value.length) return '0 题'
  const start = (mapPage.value - 1) * MAP_PAGE_SIZE + 1
  const end = Math.min(mapPage.value * MAP_PAGE_SIZE, visibleQuestions.value.length)
  return `${start}-${end} / ${visibleQuestions.value.length}`
})
const pagedQuestions = computed(() => {
  const start = (mapPage.value - 1) * MAP_PAGE_SIZE
  return visibleQuestions.value.slice(start, start + MAP_PAGE_SIZE).map((question, offset) => ({
    question,
    index: start + offset,
  }))
})

const singleAnswer = computed({
  get() {
    const question = activeQuestion.value
    if (!question) return ''
    const value = answers[question.id]
    return Array.isArray(value) ? value.join('') : (value || '')
  },
  set(value) {
    const question = activeQuestion.value
    if (!question) return
    answers[question.id] = value
  },
})

const multiAnswer = computed({
  get() {
    const question = activeQuestion.value
    if (!question) return []
    const value = answers[question.id]
    if (Array.isArray(value)) return value
    return value ? String(value).split('') : []
  },
  set(value) {
    const question = activeQuestion.value
    if (!question) return
    answers[question.id] = [...value].sort()
  },
})

const shortAnswer = computed({
  get() {
    const question = activeQuestion.value
    if (!question) return ''
    const value = answers[question.id]
    return Array.isArray(value) ? value.join('') : (value || '')
  },
  set(value) {
    const question = activeQuestion.value
    if (!question) return
    answers[question.id] = value
  },
})

const subjectiveAnswer = computed({
  get() {
    const question = activeQuestion.value
    if (!question) return ''
    return answers[question.id] || ''
  },
  set(value) {
    const question = activeQuestion.value
    if (!question) return
    answers[question.id] = value
  },
})

watch(visibleQuestions, (list) => {
  if (activeIndex.value >= list.length) {
    activeIndex.value = 0
  }
  mapPage.value = 1
})

watch(activeIndex, (index) => {
  mapPage.value = Math.floor(index / MAP_PAGE_SIZE) + 1
})

onMounted(async () => {
  startFreezeWatchdog()
  await loadSubjectOptions()
  await loadLatestBank(subject.value, true)
})

onBeforeUnmount(() => {
  stopFreezeWatchdog()
})

function toggleManagePanel() {
  showManagePanel.value = !showManagePanel.value
}

function onFileChange(file, fileList) {
  selectedFile.value = file.raw
  uploadFiles.value = fileList.slice(-1)
  const filename = file.name || file.raw?.name || ''
  if (/习近平|习概|习思想/i.test(filename)) {
    subject.value = '习概'
    addSubjectOption(subject.value)
  } else if (/毛概|毛泽东思想/i.test(filename)) {
    subject.value = '毛概'
    addSubjectOption(subject.value)
  } else if (/java|程序设计/i.test(filename)) {
    subject.value = 'Java程序设计'
    addSubjectOption(subject.value)
  } else if (/组成原理|计算机组成|计组/i.test(filename)) {
    subject.value = '组成原理'
    addSubjectOption(subject.value)
  }
}

function onFileRemove() {
  selectedFile.value = null
  uploadFiles.value = []
}

async function analyzeFile() {
  if (!isExamAdmin.value) {
    ElMessage.warning('只有管理员可以上传题库')
    return
  }
  if (!selectedFile.value) {
    ElMessage.warning('请先选择题库文件')
    return
  }
  analyzing.value = true
  errorMessage.value = ''
  permissionError.value = ''
  try {
    const res = await analyzeExamWord({
      subject: subject.value.trim(),
      file: selectedFile.value,
    })
    await applyBank(res.data)
    addSubjectOption(res.data?.subject || subject.value)
    modeView.value = studyMode.value || 'practice'
    showManagePanel.value = false
    ElMessage.success('题库已解析入库')
  } catch (err) {
    handleRequestError(err, '题库整理失败')
  } finally {
    analyzing.value = false
  }
}

async function loadMaogaiSeed() {
  await applyBank(maogaiQuestionBank)
  subject.value = maogaiQuestionBank.subject
  addSubjectOption(maogaiQuestionBank.subject)
}

async function loadSubjectOptions() {
  try {
    const res = await getExamBankSubjects()
    subjectOptions.value = normalizeSubjectOptions(Array.isArray(res.data) ? res.data : [])
    if (!subjectOptions.value.length) {
      subjectOptions.value = [DEFAULT_SUBJECT]
    }
    if (!subjectOptions.value.includes(subject.value)) {
      subject.value = subjectOptions.value[0]
    }
  } catch {
    subjectOptions.value = [DEFAULT_SUBJECT]
  }
}

async function loadLatestBank(targetSubject = subject.value, allowSeedFallback = false) {
  const normalizedSubject = String(targetSubject || '').trim() || DEFAULT_SUBJECT
  pageLoading.value = true
  errorMessage.value = ''
  permissionError.value = ''
  try {
    const res = await getLatestExamBank(normalizedSubject)
    if (Array.isArray(res.data?.questions) && res.data.questions.length > 0) {
      await applyBank(res.data)
      subject.value = res.data.subject || subject.value
      addSubjectOption(subject.value)
      return
    }
    if (allowSeedFallback && normalizedSubject === DEFAULT_SUBJECT) {
      await loadMaogaiSeed()
      return
    }
    clearBank(normalizedSubject)
  } catch (err) {
    if (err?.code === 1106) {
      permissionError.value = '登录后才能使用考试题库'
      return
    }
    if (allowSeedFallback && normalizedSubject === DEFAULT_SUBJECT) {
      await loadMaogaiSeed()
      errorMessage.value = err?.message || '读取已保存题库失败，已显示本地题库'
      return
    }
    clearBank(normalizedSubject)
    errorMessage.value = err?.message || '读取已保存题库失败'
  } finally {
    pageLoading.value = false
  }
}

async function onSubjectChange(value) {
  const nextSubject = String(value || '').trim()
  if (!nextSubject || nextSubject === currentSubject.value) return
  await loadLatestBank(nextSubject, false)
}

async function applyBank(bank) {
  currentSubject.value = bank?.subject || subject.value.trim() || '考试'
  bankId.value = bank?.bankId || null
  questions.value = Array.isArray(bank?.questions) ? bank.questions : []
  warnings.value = Array.isArray(bank?.warnings) ? bank.warnings : []
  activeFilter.value = 'all'
  activeMapFilter.value = 'all'
  activeIndex.value = 0
  mapPage.value = 1
  modeView.value = 'overview'
  clearRecords()
  if (bankId.value) {
    await loadProgress()
  }
}

function clearBank(nextSubject) {
  currentSubject.value = nextSubject || DEFAULT_SUBJECT
  subject.value = currentSubject.value
  bankId.value = null
  questions.value = []
  warnings.value = []
  activeFilter.value = 'all'
  activeMapFilter.value = 'all'
  activeIndex.value = 0
  mapPage.value = 1
  modeView.value = 'overview'
  clearRecords()
}

function addSubjectOption(value) {
  const nextSubject = String(value || '').trim()
  if (!nextSubject || subjectOptions.value.includes(nextSubject)) return
  subjectOptions.value = [nextSubject, ...subjectOptions.value]
}

function normalizeSubjectOptions(values) {
  const result = []
  values.forEach((item) => {
    const nextSubject = String(item || '').trim()
    if (nextSubject && !result.includes(nextSubject)) {
      result.push(nextSubject)
    }
  })
  return result
}

function clearRecords() {
  for (const key of Object.keys(answers)) delete answers[key]
  for (const key of Object.keys(objectiveChecked)) delete objectiveChecked[key]
  for (const key of Object.keys(answerRevealed)) delete answerRevealed[key]
  for (const key of Object.keys(judgeResults)) delete judgeResults[key]
  for (const key of Object.keys(scoring)) delete scoring[key]
  for (const key of Object.keys(progressRecords)) delete progressRecords[key]
}

function filterQuestions(filter, mapFilter = 'all') {
  const buckets = questionBuckets.value
  let result = buckets.all
  if (filter === 'choice') {
    result = buckets.choice
  } else if (filter === 'judgement') {
    result = buckets.judgement
  } else if (filter === 'subjective') {
    result = buckets.subjective
  } else if (filter === 'focusBook') {
    result = progressSummary.value.focus
  } else if (filter === 'wrongBook') {
    result = progressSummary.value.wrong
  }
  if (filter !== 'all' && !BOOK_FILTERS.includes(filter)) {
    return result
  }
  if (mapFilter === 'choice') {
    return result.filter((item) => item.type === 'single' || item.type === 'multiple')
  }
  if (mapFilter === 'judgement') {
    return result.filter((item) => item.type === 'judgement')
  }
  if (mapFilter === 'subjective') {
    return result.filter((item) => item.type === 'subjective')
  }
  return result
}

async function loadProgress() {
  if (!bankId.value) return
  try {
    const res = await getExamQuestionProgress(bankId.value)
    const records = Array.isArray(res.data?.records) ? res.data.records : []
    records.forEach((record) => applyProgressRecord(record))
  } catch (err) {
    errorMessage.value = err?.message || '读取答题进度失败'
  }
}

function applyProgressRecord(record) {
  if (!record?.questionId) return
  const key = String(record.questionId)
  const question = questionById.value.get(key)
  progressRecords[key] = {
    answerText: record.answerText || '',
    answered: !!record.answered,
    correct: typeof record.correct === 'boolean' ? record.correct : null,
    wrong: !!record.wrong,
    focus: !!record.focus,
    judgeScore: record.judgeScore ?? null,
  }
  if (!question) return
  if (record.answerText) {
    answers[key] = question.type === 'multiple'
      ? normalizeAnswer(record.answerText).split('')
      : record.answerText
  }
  if (record.answered && OBJECTIVE_TYPES.includes(question.type)) {
    objectiveChecked[key] = true
  }
  if (record.answered && question.type === 'subjective' && record.judgeScore !== null && record.judgeScore !== undefined) {
    judgeResults[key] = {
      score: record.judgeScore,
      passed: record.correct === true,
      comment: '已保存上次评分结果。',
      matchedPoints: [],
      missedPoints: [],
      fallback: true,
    }
  }
}

function enterMode(mode) {
  studyMode.value = mode
  modeView.value = mode
  if (!activeQuestion.value) {
    activeIndex.value = 0
  }
}

function returnOverview() {
  modeView.value = 'overview'
}

function switchMode(mode) {
  studyMode.value = mode
  modeView.value = mode
}

function enterTopic(filter) {
  const nextList = filterQuestions(filter, 'all')
  if (!nextList.length) {
    ElMessage.info('暂无题目')
    return
  }
  activeFilter.value = filter
  activeMapFilter.value = 'all'
  activeIndex.value = 0
  mapPage.value = 1
  enterMode('practice')
}

function selectFilter(filter) {
  activeMapFilter.value = filter
  activeIndex.value = 0
  mapPage.value = 1
}

function selectQuestion(index) {
  activeIndex.value = index
}

function prevMapPage() {
  if (mapPage.value > 1) {
    mapPage.value--
  }
}

function nextMapPage() {
  if (mapPage.value < mapPageCount.value) {
    mapPage.value++
  }
}

function chooseOption(label) {
  const question = activeQuestion.value
  if (!question || isOptionLocked.value) return
  if (question.type === 'multiple') {
    const current = multiAnswer.value
    multiAnswer.value = current.includes(label)
      ? current.filter((item) => item !== label)
      : [...current, label]
    return
  }
  singleAnswer.value = label
}

async function submitObjective() {
  const question = activeQuestion.value
  if (!question) return
  const answer = normalizeAnswer(answers[question.id])
  if (!answer) {
    ElMessage.warning('请先作答')
    return
  }
  objectiveChecked[question.id] = true
  const correct = answer === normalizeAnswer(question.answer)
  const payload = {
    answerText: answer,
    answered: true,
    correct,
  }
  if (activeFilter.value === 'wrongBook') {
    payload.wrong = true
  }
  const saved = await persistQuestionProgress(question, {
    ...payload,
  })
  if (saved) {
    ElMessage.success('已提交，答题状态已记录')
  }
}

async function scoreSubjective() {
  const question = activeQuestion.value
  if (!question) return
  const userAnswer = String(answers[question.id] || '').trim()
  if (!userAnswer) {
    ElMessage.warning('请先填写答案')
    return
  }
  scoring[question.id] = true
  try {
    const res = await judgeSubjectiveAnswer({
      subject: currentSubject.value,
      question: question.stem,
      standardAnswer: question.answer,
      userAnswer,
    })
    judgeResults[question.id] = res.data
    const passed = !!res.data?.passed
    const payload = {
      answerText: userAnswer,
      answered: true,
      correct: passed,
      wrong: activeFilter.value === 'wrongBook' ? true : !passed,
      judgeScore: res.data?.score ?? null,
    }
    await persistQuestionProgress(question, payload)
  } catch {
    const localResult = buildLocalSubjectiveResult(question.answer, userAnswer)
    judgeResults[question.id] = localResult
    await persistQuestionProgress(question, {
      answerText: userAnswer,
      answered: true,
      correct: localResult.passed,
      wrong: activeFilter.value === 'wrongBook' ? true : !localResult.passed,
      judgeScore: localResult.score,
    })
  } finally {
    answerRevealed[question.id] = true
    scoring[question.id] = false
  }
}

async function resetActiveAnswer() {
  const question = activeQuestion.value
  if (!question) return
  delete answers[question.id]
  delete objectiveChecked[question.id]
  delete answerRevealed[question.id]
  delete judgeResults[question.id]
  const payload = {
    answered: false,
  }
  if (activeFilter.value === 'wrongBook') {
    payload.wrong = true
  }
  await persistQuestionProgress(question, payload)
}

function openEditQuestion() {
  if (!isExamAdmin.value) {
    ElMessage.warning('只有管理员可以修改题目')
    return
  }
  const question = activeQuestion.value
  if (!question) return
  editForm.stem = question.stem || ''
  editForm.answer = question.answer || ''
  editForm.explanation = question.explanation || ''
  editForm.options = Array.isArray(question.options)
    ? question.options.map((item) => ({ label: item.label, text: item.text }))
    : []
  showEditDialog.value = true
}

function addEditOption() {
  const nextLabel = String.fromCharCode(65 + editForm.options.length)
  editForm.options.push({ label: nextLabel, text: '' })
}

function removeEditOption(index) {
  editForm.options.splice(index, 1)
}

async function submitEditQuestion() {
  const question = activeQuestion.value
  if (!question || !bankId.value) return
  if (!editForm.stem.trim()) {
    ElMessage.warning('题干不能为空')
    return
  }
  try {
    await ElMessageBox.confirm('确认保存这道题目的修改吗？保存后会更新当前题库中的题干、选项和答案。', '确认修改题目', {
      confirmButtonText: '确认保存',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  editSaving.value = true
  try {
    const res = await updateExamQuestion({
      bankId: bankId.value,
      questionId: Number(question.id),
      stem: editForm.stem,
      options: editForm.options,
      answer: editForm.answer,
      explanation: editForm.explanation,
    })
    const index = questions.value.findIndex((item) => String(item.id) === String(question.id))
    if (index >= 0 && res.data) {
      questions.value.splice(index, 1, res.data)
    }
    showEditDialog.value = false
    ElMessage.success('题目已更新')
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(err?.message || '题目修改失败')
    }
  } finally {
    editSaving.value = false
  }
}

async function markFocusQuestion() {
  const question = activeQuestion.value
  if (!question) return
  await persistQuestionProgress(question, {
    focus: true,
  })
  ElMessage.success('已加入重点记忆本')
}

async function removeFromCurrentBook() {
  const question = activeQuestion.value
  if (!question) return
  if (activeFilter.value === 'wrongBook') {
    await persistQuestionProgress(question, {
      wrong: false,
    })
  } else if (activeFilter.value === 'focusBook') {
    await persistQuestionProgress(question, {
      focus: false,
    })
  }
  if (activeIndex.value >= visibleQuestions.value.length) {
    activeIndex.value = Math.max(0, visibleQuestions.value.length - 1)
  }
}

async function persistQuestionProgress(question, payload) {
  if (!question) return false
  const key = String(question.id)
  const previous = progressRecords[key] || {}
  if (payload.answered === false) {
    progressRecords[key] = {
      answerText: '',
      answered: false,
      correct: null,
      wrong: payload.wrong ?? false,
      focus: previous.focus ?? false,
      judgeScore: null,
    }
  } else {
    progressRecords[key] = {
      answerText: payload.answerText ?? previous.answerText ?? '',
      answered: payload.answered ?? previous.answered ?? false,
      correct: Object.prototype.hasOwnProperty.call(payload, 'correct') ? payload.correct : (previous.correct ?? null),
      wrong: payload.wrong ?? previous.wrong ?? false,
      focus: payload.focus ?? previous.focus ?? false,
      judgeScore: payload.judgeScore ?? previous.judgeScore ?? null,
    }
  }
  if (!bankId.value || Number.isNaN(Number(question.id))) {
    return true
  }
  try {
    const res = await saveExamQuestionProgress({
      bankId: bankId.value,
      questionId: Number(question.id),
      ...payload,
    })
    applyProgressRecord(res.data)
    return true
  } catch (err) {
    ElMessage.error(err?.message || '答题状态保存失败')
    return false
  }
}

function isAnswered(question) {
  if (!question) return false
  const record = progressRecords[question.id]
  return !!record?.answered || !!objectiveChecked[question.id] || !!answerRevealed[question.id] || !!judgeResults[question.id]
}

function isFocusQuestion(question) {
  if (!question) return false
  return !!progressRecords[question.id]?.focus
}

function isWrongQuestion(question) {
  if (!question) return false
  return !!progressRecords[question.id]?.wrong
}

function isActiveObjectiveCorrect() {
  const question = activeQuestion.value
  if (!question || !isObjectiveActive.value) return false
  return normalizeAnswer(answers[question.id]) === normalizeAnswer(question.answer)
}

function isOptionSelected(label) {
  const question = activeQuestion.value
  if (!question) return false
  const value = answers[question.id]
  if (Array.isArray(value)) {
    return value.includes(label)
  }
  return value === label
}

function isCorrectOption(label) {
  const question = activeQuestion.value
  if (!question) return false
  return normalizeAnswer(question.answer).includes(label)
}

function isWrongSelectedOption(label) {
  return optionVisualState(label) === 'wrong'
}

function isFeedbackAnswerOption(label) {
  const visualState = optionVisualState(label)
  return visualState === 'correct' || visualState === 'answer-outline'
}

function optionCardClass(option) {
  const visualState = optionVisualState(option.label)
  return {
    'is-selected': isOptionSelected(option.label),
    'is-correct': visualState === 'correct',
    'is-wrong': visualState === 'wrong',
    'is-answer-outline': visualState === 'answer-outline',
  }
}

function optionVisualState(label) {
  const question = activeQuestion.value
  if (!question) return ''
  const selected = selectedAnswerSet(question)
  const correct = correctAnswerSet(question)
  const hasSubmitted = studyMode.value === 'memory' || !!objectiveChecked[question.id]
  if (!hasSubmitted) return ''
  const hasWrongSelected = [...selected].some((item) => !correct.has(item))
  const hasCorrectSelected = [...selected].some((item) => correct.has(item))
  if (studyMode.value === 'memory') {
    if (correct.has(label)) return 'correct'
    if (selected.has(label) && !correct.has(label)) return 'wrong'
    return ''
  }
  if (question.type !== 'multiple') {
    if (correct.has(label)) return 'correct'
    if (selected.has(label) && !correct.has(label)) return 'wrong'
    return ''
  }
  if (!hasWrongSelected) {
    return correct.has(label) ? 'correct' : ''
  }
  if (hasCorrectSelected) {
    return correct.has(label) ? 'answer-outline' : ''
  }
  if (selected.has(label) && !correct.has(label)) return 'wrong'
  if (correct.has(label)) return 'correct'
  return ''
}

function selectedAnswerSet(question) {
  const value = answers[question.id]
  if (Array.isArray(value)) {
    return new Set(value.map((item) => String(item).trim().toUpperCase()).filter(Boolean))
  }
  return new Set(String(value || '').trim().toUpperCase().split('').filter(Boolean))
}

function correctAnswerSet(question) {
  return new Set(normalizeAnswer(question.answer).split('').filter(Boolean))
}

function mapButtonClass(item) {
  const record = progressRecords[item.question.id] || {}
  const isCorrect = record.correct === true
  const isWrong = record.correct === false || (!!record.wrong && !isCorrect)
  return {
    'is-current': item.index === activeIndex.value,
    'is-correct': isCorrect,
    'is-wrong': isWrong,
    'is-unread': !isCorrect && !isWrong,
  }
}

function normalizeAnswer(value) {
  if (Array.isArray(value)) {
    return [...value].sort().join('').toUpperCase()
  }
  const text = String(value || '').trim().toUpperCase()
  if (text === '对') return 'A'
  if (text === '错') return 'B'
  return text.split('').sort().join('')
}

function buildLocalSubjectiveResult(standardAnswer, userAnswer) {
  const standardPoints = tokenizeAnswer(standardAnswer)
  const userText = String(userAnswer || '')
  const matchedPoints = standardPoints.filter((point) => userText.includes(point))
  const missedPoints = standardPoints.filter((point) => !userText.includes(point))
  const score = standardPoints.length ? Math.round((matchedPoints.length * 100) / standardPoints.length) : 0
  return {
    score,
    passed: score >= 60,
    comment: '已根据参考答案关键词完成估分。',
    matchedPoints,
    missedPoints,
    fallback: true,
  }
}

function tokenizeAnswer(answer) {
  return Array.from(new Set(String(answer || '')
    .split(/[，,；;、。\s（）()：:]+/)
    .map((item) => item.trim())
    .filter((item) => item.length >= 2)))
}

function startFreezeWatchdog() {
  if (freezeWatchdogTimer || typeof window === 'undefined') return
  let lastTick = performance.now()
  freezeWatchdogTimer = window.setInterval(() => {
    const now = performance.now()
    const lag = now - lastTick - 5000
    lastTick = now
    if (lag < 1500 || now - lastFreezeReportAt < 30000) return
    lastFreezeReportAt = now
    console.warn('[exam-freeze-watchdog]', {
      lag: Math.round(lag),
      subject: currentSubject.value,
      mode: modeView.value,
      filter: activeFilter.value,
      mapFilter: activeMapFilter.value,
      total: questions.value.length,
      visible: visibleQuestions.value.length,
      memory: performance.memory || null,
      time: new Date().toISOString(),
    })
  }, 5000)
}

function stopFreezeWatchdog() {
  if (!freezeWatchdogTimer) return
  window.clearInterval(freezeWatchdogTimer)
  freezeWatchdogTimer = null
}

function percent(value, total) {
  if (!total) return 0
  return Math.max(0, Math.min(100, Math.round((value * 100) / total)))
}

function typeLabel(type) {
  if (type === 'single') return '单选'
  if (type === 'multiple') return '多选'
  if (type === 'judgement') return '判断'
  if (type === 'subjective') return '大题'
  return '题目'
}

function handleRequestError(err, fallback) {
  if (err?.code === 1106) {
    permissionError.value = '登录后才能使用考试题库'
    return
  }
  errorMessage.value = err?.message || fallback
}

return {
  pageLoading,
  analyzing,
  showManagePanel,
  isExamAdmin,
  subject,
  subjectOptions,
  currentSubject,
  bankId,
  selectedFile,
  uploadFiles,
  questions,
  errorMessage,
  permissionError,
  activeFilter,
  activeMapFilter,
  studyMode,
  modeView,
  mapPage,
  stats,
  topicCards,
  visibleQuestions,
  activeQuestion,
  emptyState,
  completionPercent,
  warningText,
  isObjectiveActive,
  isSubjectiveActive,
  activeTypeLabel,
  activeDisplayNo,
  activeObjectiveChecked,
  activeCorrect,
  activeObjectiveResultText,
  activeObjectiveResultClass,
  activeJudgeResult,
  activeScoring,
  activeProgress,
  showEditDialog,
  editSaving,
  editForm,
  shouldRevealAnswer,
  isOptionLocked,
  showExplanationPanel,
  isBookMode,
  bookActionLabel,
  questionProgress,
  mapPageCount,
  mapRangeText,
  pagedQuestions,
  singleAnswer,
  multiAnswer,
  shortAnswer,
  subjectiveAnswer,
  filterOptions,
  navFilterOptions,
  showNavFilterRow,
  toggleManagePanel,
  onFileChange,
  onFileRemove,
  analyzeFile,
  onSubjectChange,
  loadMaogaiSeed,
  enterMode,
  returnOverview,
  switchMode,
  enterTopic,
  selectFilter,
  selectQuestion,
  prevMapPage,
  nextMapPage,
  chooseOption,
  submitObjective,
  scoreSubjective,
  resetActiveAnswer,
  openEditQuestion,
  addEditOption,
  removeEditOption,
  submitEditQuestion,
  markFocusQuestion,
  removeFromCurrentBook,
  isCorrectOption,
  isWrongSelectedOption,
  isFeedbackAnswerOption,
  optionCardClass,
  mapButtonClass,
}
  },
})
