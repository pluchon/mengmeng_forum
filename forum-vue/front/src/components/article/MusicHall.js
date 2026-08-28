import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  ArrowRight,
  Check,
  Close,
  DArrowLeft,
  DArrowRight,
  Document,
  EditPen,
  Folder,
  Headset,
  MagicStick,
  Picture,
  Promotion,
  Scissor,
  Search,
  Star,
  Upload,
  VideoPause,
  VideoPlay,
} from '@element-plus/icons-vue'
import { listMusicCatalog, listMusicMoodTags, listMyMusic, listMusicFavorites, toggleMusicFavorite, uploadArticleMusic, parseArticleMusic, trimArticleMusic, recommendArticleMusic, aiSearchArticleMusic, retryArticleMusicAudit, listMusicRecentPlays, recordMusicRecentPlay } from '@/api/article'
import { extractApiErrorMessage } from '@/api/httpError'
import { ensureLoggedIn } from '@/utils/loginPrompt'
import BorderGlow from '@/components/common/BorderGlow.vue'
import AppPagination from '@/components/common/AppPagination.vue'
import MusicHallDiscover from '@/components/music-hall/MusicHallDiscover.vue'
import {
  analyzeAudioPeaks,
  decodeAudioSource,
  peaksFromBuffer,
} from '@/utils/musicWaveform'
import { useUserStore } from '@/stores/user'
import emptyMusicUrl from '@/assets/images/musiuc_not.png'
import emptyRecentUrl from '@/assets/images/music_play_near.png'
import emptyLrcUrl from '@/assets/images/music_lrc_not.png'

const RECENT_PAGE_SIZE = 5
const RECENT_EQ_BARS = [0, 1, 2, 3]
const AUDIO_MAX_BYTES = 50 * 1024 * 1024
const AUDIO_ACCEPT = '.mp3,.wav,.flac,.m4a'
const AUDIO_EXT_PATTERN = /\.(mp3|wav|flac|m4a)$/i
const AUDIO_EXT_HINT = 'mp3 / wav / flac / m4a'
const CATALOG_SCOPES = [
  { id: 'all', label: '综合' },
  { id: 'title', label: '歌名' },
  { id: 'artist', label: '歌手' },
  { id: 'album', label: '专辑' },
]
// 氛围标签的唯一来源在后端 Nacos 配置，这里只保留请求失败时的兜底，
// 避免筛选栏整条消失。首项「热门」是默认态，后端视作不过滤。
const MOOD_TAGS_FALLBACK = ['热门', '治愈', '清新', '浪漫', '轻松', '深夜', '轻音乐', '适合配图']
const CATALOG_PAGE_SIZE = 10
const WAVE_BAR_COUNT = 96
const LRC_LINE_HEIGHT = 26

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  selected: { type: Object, default: null },
  articleTitle: { type: String, default: '' },
  articleContent: { type: String, default: '' },
  embedded: { type: Boolean, default: false },
  hallTab: { type: String, default: 'discover' },
})

const emit = defineEmits(['update:modelValue', 'confirm'])
const userStore = useUserStore()

const isPickerMode = computed(() => !props.embedded)
const isActive = computed(() => props.embedded || props.modelValue)
const showDiscoverPanel = computed(() => !props.embedded || props.hallTab === 'discover')
const showMinePanel = computed(() => !props.embedded || props.hallTab === 'mine')

const keyword = ref('')
const appliedKeyword = ref('')
const catalogScope = ref('all')
const aiSearchEnabled = ref(false)
const catalogSearchMode = ref(false)
const catalogAiMode = ref('none')
const aiLoading = ref(false)
const aiEmptyHint = ref('')
const catalogPageNum = ref(1)
const catalogPageTotal = ref(1)
const catalogTotal = ref(0)
const loading = ref(false)
const loadError = ref('')
const tracks = ref([])
const draftSelected = ref(null)
const previewTrack = ref(null)
const activeMood = ref('热门')
const moodTags = ref([...MOOD_TAGS_FALLBACK])
const recentTracks = ref([])
const recentPageNum = ref(1)
const recentPageTotal = ref(1)
const recentLoading = ref(false)
const audioRef = ref(null)
const playing = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const timedLrcLines = ref([])
const plainLrcLines = ref([])
const showLrc = ref(false)
const waveHeights = ref(Array(WAVE_BAR_COUNT).fill(10))
const composeWaveHeights = ref(Array(WAVE_BAR_COUNT).fill(10))
const waveAnalyzing = ref(false)
const localPreviewUrl = ref('')
const localCoverPreviewUrl = ref('')
const composeCoverPreviewUrl = ref('')
const parsing = ref(false)
const mineTab = ref('favorite')
const favoriteTracks = ref([])
const uploadTracks = ref([])
const publishTracks = ref([])
const uploadKeyword = ref('')
const uploadStatus = ref('all')
const uploadStatusFilters = [
  { id: 'all', label: '全部' },
  { id: 'uploading', label: '上传中' },
  { id: 'reviewing', label: '审核中' },
  { id: 'rejected', label: '未通过' },
  { id: 'draft', label: '未发布' },
]
const emptySongForm = () => ({
  id: null,
  audioName: '',
  coverName: '',
  lrcName: '',
  title: '',
  artist: '',
  album: '',
  durationText: '',
  lrcText: '',
  tags: [],
  status: '',
  parsed: false,
})
const songForm = ref(emptySongForm())
const showComposeLrc = ref(false)
const editingComposeLrc = ref(false)
const audioInputRef = ref(null)
const coverInputRef = ref(null)
const lrcInputRef = ref(null)
const parseInputRef = ref(null)
const audioFile = ref(null)
const coverFile = ref(null)
const lrcFile = ref(null)
const composeSubmitting = ref(false)
const mineLoading = ref(false)
const showTrimDialog = ref(false)
const trimLoading = ref(false)
const trimApplying = ref(false)
const trimAudioBuffer = ref(null)
const trimWaveHeights = ref(Array(WAVE_BAR_COUNT).fill(10))
const trimStartSec = ref(0)
const trimEndSec = ref(0)
const trimPlaying = ref(false)
const trimAudioRef = ref(null)
const trimBlobUrl = ref('')
const trimPlayheadSec = ref(0)

const filteredUploadTracks = computed(() => {
  const keyword = uploadKeyword.value.trim().toLowerCase()
  return uploadTracks.value.filter((track) => {
    const matchStatus = uploadStatus.value === 'all' || track.status === uploadStatus.value
    if (!matchStatus) return false
    if (!keyword) return true
    return `${track.title || ''} ${track.artist || ''}`.toLowerCase().includes(keyword)
  })
})

const composeLrcLines = computed(() => filterLyricDisplayLines(parseLrcLines(songForm.value.lrcText)))

const composeLrcPreview = computed(() => {
  const lines = composeLrcLines.value
  if (lines.length) return lines.slice(0, 3)
  const plain = filterLyricDisplayLines(
    String(songForm.value.lrcText || '')
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean)
      .map((text) => ({ time: '', text })),
  )
  return plain.slice(0, 3)
})

// 投稿快选与筛选栏共用同一份候选集，二者不再各持一份而漂移
const composeTagOptions = computed(() => moodTags.value)

const hidePreviewFavorite = computed(() => mineTab.value === 'compose')

const previewTags = computed(() => {
  if (mineTab.value === 'compose' && songForm.value.tags?.length) {
    return songForm.value.tags
  }
  if (Array.isArray(previewTrack.value?.moodTags) && previewTrack.value.moodTags.length) {
    return previewTrack.value.moodTags
  }
  if (previewTrack.value?.tags?.length) {
    return previewTrack.value.tags
  }
  return []
})

const previewArtistText = computed(() => {
  if (!previewTrack.value) return '—'
  return String(previewTrack.value.artist || '').trim() || '未知歌手'
})

const previewAlbumText = computed(() => {
  if (!previewTrack.value) return '—'
  return String(previewTrack.value.album || '').trim() || '未填专辑'
})

const catalogScopeOptions = CATALOG_SCOPES

function isTrackBindable(track) {
  if (!track?.musicKey || !track?.audioUrl) return false
  const key = String(track.musicKey)
  if (key.startsWith('local-')) return false
  const status = track.status ? String(track.status).toLowerCase() : ''
  if (status === 'draft' || status === 'reviewing' || status === 'rejected') return false
  return true
}

function applyDraftSelection(track) {
  draftSelected.value = isTrackBindable(track) ? { ...track } : null
}

const canConfirmSelection = computed(() => isTrackBindable(draftSelected.value))

const catalogEmptyText = computed(() => {
  if (aiLoading.value) return ''
  if (aiEmptyHint.value) return aiEmptyHint.value
  if (catalogSearchMode.value && aiSearchEnabled.value) {
    return 'AI没找到符合描述的歌曲，试试换个说法或自己上传'
  }
  if (catalogAiMode.value === 'recommend') {
    return 'AI没找到符合你帖子的歌曲，试试自己上传吧'
  }
  return '没有歌曲'
})

const plainLrcActiveIndex = computed(() => {
  const lines = plainLrcLines.value
  if (!lines.length || !duration.value) return 0
  const ratio = Math.min(1, Math.max(0, currentTime.value / duration.value))
  return Math.min(lines.length - 1, Math.floor(ratio * lines.length))
})

const hasComposeAudio = computed(() => Boolean(audioFile.value || songForm.value.audioName))
const hasComposeCover = computed(() => Boolean(coverFile.value || composeCoverPreviewUrl.value))
const hasComposeLrc = computed(() => Boolean(songForm.value.lrcText || lrcFile.value))

const trimDurationSec = computed(() => Math.max(0, trimEndSec.value - trimStartSec.value))

const trimSelectionStyle = computed(() => {
  const total = trimAudioBuffer.value?.duration || 0
  if (!total) return { left: '0%', width: '100%' }
  const left = (trimStartSec.value / total) * 100
  const width = ((trimEndSec.value - trimStartSec.value) / total) * 100
  return {
    left: `${Math.max(0, Math.min(100, left))}%`,
    width: `${Math.max(0, Math.min(100, width))}%`,
  }
})

const trimPlayheadStyle = computed(() => {
  const total = trimAudioBuffer.value?.duration || 0
  if (!total) return { left: '0%' }
  const ratio = Math.min(1, Math.max(0, trimPlayheadSec.value / total))
  return { left: `${ratio * 100}%` }
})

const trimSongTitle = computed(() => (
  songForm.value.title?.trim()
  || fileStem(songForm.value.audioName)
  || '未命名歌曲'
))

const karaokeActiveIndex = computed(() => {
  const lines = timedLrcLines.value
  if (!lines.length) return 0
  let idx = 0
  for (let i = 0; i < lines.length; i += 1) {
    if (lines[i].timeSec <= currentTime.value + 0.05) idx = i
    else break
  }
  return idx
})

const karaokeOffset = computed(() => {
  const idx = karaokeActiveIndex.value
  return Math.max(0, (idx - 2) * LRC_LINE_HEIGHT)
})

const plainLrcOffset = computed(() => Math.max(0, (plainLrcActiveIndex.value - 2) * LRC_LINE_HEIGHT))

const hasPlayerLyrics = computed(() => timedLrcLines.value.length > 0 || plainLrcLines.value.length > 0)

const composeLocked = computed(() => {
  const status = songForm.value.status
  return status === 'reviewing' || status === 'published'
})

const isPreviewFavorited = computed(() => Boolean(previewTrack.value?.favorited))

const filteredTracks = computed(() => tracks.value)

const showEmbeddedDiscoverFeed = computed(
  () => props.embedded && showDiscoverPanel.value && !catalogSearchMode.value,
)

function isMoodActive(tag) {
  if (catalogSearchMode.value) return false
  if (mineTab.value === 'compose' && songForm.value.tags?.length) {
    return songForm.value.tags.includes(tag)
  }
  return activeMood.value === tag
}

function onMoodClick(tag) {
  if (catalogSearchMode.value) return
  if (mineTab.value === 'compose') {
    toggleComposeTag(tag)
    return
  }
  activeMood.value = tag
  catalogAiMode.value = 'none'
  aiEmptyHint.value = ''
  catalogPageNum.value = 1
  loadCatalog(1)
}

const progressPercent = computed(() => {
  if (!duration.value) return 0
  return Math.min(100, Math.max(0, (currentTime.value / duration.value) * 100))
})

const wavePlayedCount = computed(() => {
  if (!duration.value || !waveHeights.value.length) return 0
  return Math.round((progressPercent.value / 100) * waveHeights.value.length)
})

watch(
  () => previewTrack.value?.audioUrl,
  async (url) => {
    if (!url) {
      waveHeights.value = Array(WAVE_BAR_COUNT).fill(10)
      return
    }
    waveAnalyzing.value = true
    try {
      waveHeights.value = await analyzeAudioPeaks(url, WAVE_BAR_COUNT)
    } finally {
      waveAnalyzing.value = false
    }
  },
  { immediate: true },
)

watch(
  isActive,
  async (active) => {
    if (!active) {
      stopAudio()
      revokeLocalPreview()
      showLrc.value = false
      timedLrcLines.value = []
      plainLrcLines.value = []
      if (isPickerMode.value) {
        mineTab.value = 'favorite'
      }
      showComposeLrc.value = false
      editingComposeLrc.value = false
      return
    }
    await bootstrapHall()
  },
  { immediate: true },
)

watch(
  () => props.hallTab,
  async (tab) => {
    if (!props.embedded || !isActive.value) return
    if (tab === 'mine') {
      await Promise.all([loadFavorites(), loadMineLists()])
    } else if (catalogSearchMode.value) {
      await loadCatalog()
    }
  },
)

// 候选集全站一致且极少变动，一个会话内只拉一次；失败时保留兜底列表不打断主流程
let moodTagsLoaded = false
async function loadMoodTags() {
  if (moodTagsLoaded) return
  try {
    const res = await listMusicMoodTags()
    const tags = res?.data
    if (Array.isArray(tags) && tags.length) {
      moodTags.value = tags
      moodTagsLoaded = true
    }
  } catch {
    // 保留 MOOD_TAGS_FALLBACK，下次进入面板再试
  }
}

async function bootstrapHall() {
  loadMoodTags()
  if (isPickerMode.value) {
    draftSelected.value = props.selected && isTrackBindable(props.selected)
      ? { ...props.selected }
      : null
    previewTrack.value = props.selected ? { ...props.selected } : draftSelected.value
  } else {
    draftSelected.value = null
  }
  loadRecent()
  const tasks = []
  if (!props.embedded || props.hallTab !== 'discover') {
    tasks.push(loadCatalog())
  } else if (catalogSearchMode.value) {
    tasks.push(loadCatalog())
  }
  if (!props.embedded || props.hallTab === 'mine') {
    tasks.push(loadFavorites(), loadMineLists())
  }
  await Promise.all(tasks)
  if (previewTrack.value?.audioUrl) {
    await nextTick()
    loadAudio(previewTrack.value.audioUrl)
  }
  if (previewTrack.value?.lrcUrl) {
    await loadLrc(previewTrack.value.lrcUrl)
  } else {
    timedLrcLines.value = []
    plainLrcLines.value = []
  }
}

onBeforeUnmount(() => {
  stopAudio()
  revokeLocalPreview()
  revokeComposeCoverPreview()
  revokeTrimPreview()
})

watch(mineTab, (tab) => {
  if (tab === 'favorite') loadFavorites()
  if (tab === 'upload') loadMineLists()
  if (tab === 'publish') loadMineLists()
  if (tab === 'compose' && hasComposeAudio.value) {
    syncLocalPreviewFromForm()
  }
})

watch(
  () => [songForm.value.title, songForm.value.artist, songForm.value.album, songForm.value.durationText, songForm.value.tags, songForm.value.lrcText, composeCoverPreviewUrl.value],
  () => {
    syncLocalPreviewFromForm()
    if (mineTab.value === 'compose' && songForm.value.lrcText) {
      applyLyricText(songForm.value.lrcText)
    }
  },
  { deep: true },
)

async function syncComposePreviewMedia() {
  if (mineTab.value !== 'compose') return
  if (!hasComposeAudio.value) return
  ensureComposePreviewTrack()
  await nextTick()
  const url = previewTrack.value?.audioUrl
  if (url) {
    loadAudio(url)
  }
  if (songForm.value.lrcText) {
    applyLyricText(songForm.value.lrcText)
  }
  syncLocalPreviewFromForm()
}

function close() {
  stopAudio()
  revokeLocalPreview()
  emit('update:modelValue', false)
}

function confirm() {
  if (!canConfirmSelection.value) {
    ElMessage.warning('仅已发布歌曲可绑定到帖子，请等待审核通过后再选择')
    return
  }
  const payload = { ...draftSelected.value }
  if (mineTab.value === 'compose' && songForm.value.tags?.length) {
    payload.tags = [...songForm.value.tags]
  }
  emit('confirm', payload)
  close()
}

async function loadCatalog(page = catalogPageNum.value) {
  const kw = appliedKeyword.value.trim()
  catalogSearchMode.value = Boolean(kw)
  if (catalogSearchMode.value) {
    activeMood.value = ''
  } else if (!activeMood.value) {
    activeMood.value = '热门'
  }
  if (catalogSearchMode.value && aiSearchEnabled.value) {
    await loadAiSearch(kw)
    return
  }
  loading.value = true
  loadError.value = ''
  aiEmptyHint.value = ''
  if (catalogAiMode.value === 'none') {
    catalogPageNum.value = page
  }
  try {
    const res = await listMusicCatalog({
      keyword: kw || undefined,
      scope: catalogScope.value,
      mood: catalogSearchMode.value ? undefined : activeMood.value,
      pageNum: page,
      pageSize: CATALOG_PAGE_SIZE,
    })
    const pageData = res?.data || {}
    tracks.value = Array.isArray(pageData.records) ? pageData.records : []
    catalogPageNum.value = pageData.pageNum || page
    catalogPageTotal.value = Math.max(1, Number(pageData.pages) || 1)
    catalogTotal.value = Number(pageData.total) || 0
    syncPreviewFavorite()
  } catch (error) {
    tracks.value = []
    loadError.value = extractApiErrorMessage(error) || '曲库加载失败'
  } finally {
    loading.value = false
  }
}

async function loadAiSearch(query) {
  if (!(await ensureLoggedIn('AI 搜索需要登录'))) return
  loading.value = true
  aiLoading.value = true
  loadError.value = ''
  aiEmptyHint.value = ''
  catalogAiMode.value = 'search'
  catalogPageNum.value = 1
  catalogPageTotal.value = 1
  catalogTotal.value = 0
  try {
    const res = await aiSearchArticleMusic({ query, scope: catalogScope.value })
    const data = res?.data || {}
    tracks.value = Array.isArray(data.tracks) ? data.tracks : []
    aiEmptyHint.value = tracks.value.length ? '' : (data.emptyHint || '')
    syncPreviewFavorite()
  } catch (error) {
    tracks.value = []
    loadError.value = extractApiErrorMessage(error) || 'AI 搜索失败'
  } finally {
    loading.value = false
    aiLoading.value = false
  }
}

async function toggleAiSearchMode() {
  if (!aiSearchEnabled.value && !(await ensureLoggedIn('AI 搜索需要登录'))) return
  aiSearchEnabled.value = !aiSearchEnabled.value
  if (!aiSearchEnabled.value) {
    catalogAiMode.value = 'none'
    aiEmptyHint.value = ''
  }
}

function onCatalogSearch() {
  appliedKeyword.value = keyword.value.trim()
  catalogPageNum.value = 1
  catalogAiMode.value = aiSearchEnabled.value && appliedKeyword.value ? 'search' : 'none'
  loadCatalog(1)
}

function onCatalogScopeChange() {
  if (catalogAiMode.value === 'recommend') return
  catalogPageNum.value = 1
  loadCatalog(1)
}

function onCatalogPageChange(page) {
  catalogPageNum.value = page
  loadCatalog(page)
}

function syncLocalPreviewFromForm() {
  if (mineTab.value !== 'compose') return
  if (!hasComposeAudio.value) return
  if (!previewTrack.value?.musicKey) {
    ensureComposePreviewTrack()
    return
  }
  const key = previewTrack.value.musicKey
  const next = {
    ...previewTrack.value,
    title: songForm.value.title || previewTrack.value.title,
    artist: songForm.value.artist || '',
    album: songForm.value.album || '',
    durationText: songForm.value.durationText || '',
    coverUrl: composeCoverPreviewUrl.value || previewTrack.value.coverUrl || '',
    tags: [...(songForm.value.tags || [])],
  }
  previewTrack.value = next
  if (draftSelected.value?.musicKey === key) {
    applyDraftSelection(next)
  }
}

function ensureComposePreviewTrack() {
  if (mineTab.value !== 'compose') return
  const remoteUrl = previewTrack.value?.audioUrl
  if (!audioFile.value && !localPreviewUrl.value && !remoteUrl) return
  let audioUrl = localPreviewUrl.value || remoteUrl
  if (!audioUrl && audioFile.value) {
    localPreviewUrl.value = URL.createObjectURL(audioFile.value)
    audioUrl = localPreviewUrl.value
  }
  const existingKey = previewTrack.value?.musicKey
  const key = existingKey || `local-compose-${Date.now()}`
  const next = {
    musicKey: key,
    title: songForm.value.title || fileStem(songForm.value.audioName) || '未命名歌曲',
    artist: songForm.value.artist || '',
    album: songForm.value.album || '',
    durationText: songForm.value.durationText || '',
    coverUrl: composeCoverPreviewUrl.value || previewTrack.value?.coverUrl || '',
    audioUrl,
    lrcUrl: previewTrack.value?.lrcUrl || '',
    lyricText: songForm.value.lrcText || previewTrack.value?.lyricText || '',
    tags: [...(songForm.value.tags || [])],
  }
  previewTrack.value = next
  applyDraftSelection(next)
}

async function loadFavorites() {
  if (!userStore.isLoggedIn) {
    favoriteTracks.value = []
    return
  }
  try {
    const res = await listMusicFavorites()
    favoriteTracks.value = Array.isArray(res?.data) ? res.data : []
    syncPreviewFavorite()
  } catch {
    favoriteTracks.value = []
  }
}

async function loadMineLists() {
  if (!userStore.isLoggedIn) {
    uploadTracks.value = []
    publishTracks.value = []
    return
  }
  mineLoading.value = true
  try {
    const [uploadRes, publishRes] = await Promise.all([
      listMyMusic('upload'),
      listMyMusic('publish'),
    ])
    uploadTracks.value = Array.isArray(uploadRes?.data) ? uploadRes.data : []
    publishTracks.value = Array.isArray(publishRes?.data) ? publishRes.data : []
  } catch {
    uploadTracks.value = []
    publishTracks.value = []
  } finally {
    mineLoading.value = false
  }
}

function syncPreviewFavorite() {
  const key = previewTrack.value?.musicKey
  if (!key) return
  const hit = [...tracks.value, ...favoriteTracks.value, ...publishTracks.value]
    .find((item) => item?.musicKey === key)
  if (hit && previewTrack.value) {
    previewTrack.value = { ...previewTrack.value, favorited: Boolean(hit.favorited) }
  }
}

function selectTrack(track) {
  if (!track?.musicKey) return
  revokeLocalPreview()
  applyDraftSelection(track)
  previewTrack.value = { ...track }
  pushRecent(track)
  loadAudio(track.audioUrl)
  if (track.lrcUrl) loadLrc(track.lrcUrl)
  else if (track.lyricText) applyLyricText(track.lyricText)
  else {
    timedLrcLines.value = []
    plainLrcLines.value = []
  }
}

function coverStyle(track) {
  if (track?.coverUrl) return {}
  return {
    backgroundImage: 'linear-gradient(135deg, #ffb4c8 0%, #f07ba8 55%, #c4b0e8 100%)',
  }
}

function loadAudio(url) {
  const el = audioRef.value
  if (!el || !url) return
  if (el.src !== url) {
    el.src = url
    el.load()
  }
  playing.value = false
  currentTime.value = 0
}

function togglePlay() {
  const el = audioRef.value
  if (!el || !previewTrack.value?.audioUrl) return
  if (playing.value) {
    el.pause()
    playing.value = false
    return
  }
  el.play().then(() => {
    playing.value = true
  }).catch(() => {
    ElMessage.warning('音频无法播放，请检查 OSS 资源')
  })
}

function stopAudio() {
  const el = audioRef.value
  if (el) {
    el.pause()
    el.removeAttribute('src')
    el.load()
  }
  playing.value = false
  currentTime.value = 0
  duration.value = 0
}

function onTimeUpdate() {
  currentTime.value = Number(audioRef.value?.currentTime) || 0
}

function onMeta() {
  duration.value = Number(audioRef.value?.duration) || 0
}

function onEnded() {
  playing.value = false
}

function seekByClick(event) {
  const el = audioRef.value
  if (!el || !duration.value || !previewTrack.value?.audioUrl) return
  const rect = event.currentTarget.getBoundingClientRect()
  const ratio = Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width))
  const next = ratio * duration.value
  el.currentTime = next
  currentTime.value = next
}

function toggleLrc() {
  showLrc.value = !showLrc.value
}

async function onFavoriteToggle() {
  if (!previewTrack.value?.musicKey || !previewTrack.value?.audioUrl) {
    ElMessage.warning('请先选择一首歌')
    return
  }
  if (!(await ensureLoggedIn())) return
  const track = previewTrack.value
  try {
    const res = await toggleMusicFavorite({
      musicKey: track.musicKey,
      title: track.title || track.musicKey,
      artist: track.artist || '',
      album: track.album || '',
      durationText: track.durationText || '',
      coverUrl: track.coverUrl || '',
      audioUrl: track.audioUrl,
      lrcUrl: track.lrcUrl || '',
    })
    const liked = Boolean(res?.data)
    applyFavoriteFlag(track.musicKey, liked)
    if (liked) {
      ElMessage.success('已收藏')
    } else {
      ElMessage.success('已取消收藏')
    }
    await loadFavorites()
  } catch (error) {
    ElMessage.error(extractApiErrorMessage(error) || '收藏失败')
  }
}

function applyFavoriteFlag(musicKey, liked) {
  const mark = (list) => list.map((item) => (
    item?.musicKey === musicKey ? { ...item, favorited: liked } : item
  ))
  tracks.value = mark(tracks.value)
  favoriteTracks.value = liked
    ? mark(favoriteTracks.value)
    : favoriteTracks.value.filter((item) => item.musicKey !== musicKey)
  publishTracks.value = mark(publishTracks.value)
  if (previewTrack.value?.musicKey === musicKey) {
    previewTrack.value = { ...previewTrack.value, favorited: liked }
  }
  if (draftSelected.value?.musicKey === musicKey) {
    draftSelected.value = { ...draftSelected.value, favorited: liked }
  }
}

function pickUploadFile(kind) {
  if (composeLocked.value) {
    ElMessage.warning('审核中或已发布的歌曲不能再编辑')
    return
  }
  const map = {
    audio: audioInputRef,
    cover: coverInputRef,
    lrc: lrcInputRef,
  }
  map[kind]?.value?.click()
}

function fileStem(name) {
  return String(name || '').replace(/\.[^.]+$/, '').trim()
}

function isAllowedAudioFile(file) {
  const name = String(file?.name || '').toLowerCase()
  return AUDIO_EXT_PATTERN.test(name)
}

function revokeComposeCoverPreview() {
  if (composeCoverPreviewUrl.value) {
    URL.revokeObjectURL(composeCoverPreviewUrl.value)
    composeCoverPreviewUrl.value = ''
  }
}

function revokeTrimPreview() {
  stopTrimPreview()
  if (trimBlobUrl.value) {
    URL.revokeObjectURL(trimBlobUrl.value)
    trimBlobUrl.value = ''
  }
}

async function refreshComposeAudioWave(file) {
  if (!file) {
    composeWaveHeights.value = Array(WAVE_BAR_COUNT).fill(10)
    return
  }
  composeWaveHeights.value = await analyzeAudioPeaks(file, WAVE_BAR_COUNT)
}

function refreshComposeCoverPreview(file) {
  revokeComposeCoverPreview()
  if (file) {
    composeCoverPreviewUrl.value = URL.createObjectURL(file)
  }
}

function parseLrcLines(text) {
  return String(text || '')
    .split(/\r?\n/)
    .map((line) => {
      const match = line.match(/^\[(\d{1,2}:\d{2}(?:\.\d+)?)\]\s*(.*)$/)
      if (match) {
        return { time: match[1].slice(0, 5), text: match[2].trim() }
      }
      const plain = line.trim()
      return plain ? { time: '--:--', text: plain } : null
    })
    .filter(Boolean)
    .slice(0, 80)
}

function isLrcMetaLine(text) {
  const t = String(text || '').trim()
  if (!t) return true
  if (/^(作词|作曲|编曲|制作人|演唱|词[:：]|曲[:：]|编[:：]|和声|混音|出品|企划|发行|录音|母带)/i.test(t)) return true
  if (/^\[?(ti|ar|al|by|offset|length):/i.test(t)) return true
  return false
}

function filterLyricDisplayLines(lines) {
  const raw = (lines || []).filter((line) => line?.text && !isLrcMetaLine(line.text))
  const zeroTimeCount = raw.filter((line) => line.time === '00:00').length
  if (zeroTimeCount <= 1) return raw
  return raw.filter((line) => line.time !== '00:00')
}

function parseTimedLrc(text) {
  return String(text || '')
    .split(/\r?\n/)
    .map((line) => {
      const match = line.match(/^\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]\s*(.*)$/)
      if (!match) return null
      const minutes = Number(match[1]) || 0
      const seconds = Number(match[2]) || 0
      const fracRaw = match[3] || ''
      let frac = 0
      if (fracRaw) {
        frac = Number(`0.${fracRaw}`) || 0
      }
      const body = (match[4] || '').trim()
      if (!body || isLrcMetaLine(body)) return null
      return { timeSec: minutes * 60 + seconds + frac, text: body }
    })
    .filter(Boolean)
}

function applyLyricText(text) {
  const timed = parseTimedLrc(text)
  if (timed.length) {
    timedLrcLines.value = timed
    plainLrcLines.value = []
    return
  }
  timedLrcLines.value = []
  plainLrcLines.value = String(text || '')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .slice(0, 40)
}

function revokeLocalPreview() {
  if (localPreviewUrl.value) {
    URL.revokeObjectURL(localPreviewUrl.value)
    localPreviewUrl.value = ''
  }
  if (localCoverPreviewUrl.value) {
    URL.revokeObjectURL(localCoverPreviewUrl.value)
    localCoverPreviewUrl.value = ''
  }
}

function base64ToFile(base64, mimeType, fileName) {
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i)
  }
  return new File([bytes], fileName, { type: mimeType || 'image/jpeg' })
}

async function enrichFromBackend(file) {
  if (!(await ensureLoggedIn())) return null
  parsing.value = true
  try {
    const res = await parseArticleMusic(file)
    return res?.data || null
  } catch (error) {
    ElMessage.warning(extractApiErrorMessage(error) || '内嵌信息解析失败，请手动填写')
    return null
  } finally {
    parsing.value = false
  }
}

function applyParseMetaToForm(meta) {
  if (!meta) return
  if (meta.title) songForm.value.title = meta.title
  if (meta.artist) songForm.value.artist = meta.artist
  if (meta.album) songForm.value.album = meta.album
  if (meta.durationText) songForm.value.durationText = meta.durationText
  if (meta.lyricText) {
    songForm.value.lrcText = meta.lyricText
  }
}

async function previewParsedLocal(audio, meta, coverObjectUrl) {
  revokeLocalPreview()
  const url = URL.createObjectURL(audio)
  localPreviewUrl.value = url
  if (coverObjectUrl) {
    localCoverPreviewUrl.value = coverObjectUrl
  }
  previewTrack.value = {
    musicKey: `local-parse-${Date.now()}`,
    title: meta?.title || fileStem(audio.name) || '解析试听',
    artist: meta?.artist || '',
    album: meta?.album || '',
    durationText: meta?.durationText || songForm.value.durationText || '',
    coverUrl: coverObjectUrl || '',
    audioUrl: url,
    lrcUrl: '',
    lyricText: meta?.lyricText || songForm.value.lrcText || '',
    tags: [...(songForm.value.tags || [])],
  }
  applyDraftSelection(previewTrack.value)
  applyLyricText(meta?.lyricText || songForm.value.lrcText || '')
  showLrc.value = Boolean(meta?.lyricText || songForm.value.lrcText)
  await nextTick()
  loadAudio(url)
  const el = audioRef.value
  if (!el) return
  try {
    await el.play()
    playing.value = true
  } catch {
    playing.value = false
  }
}

async function fillDurationFromAudio(file) {
  const url = URL.createObjectURL(file)
  await new Promise((resolve) => {
    const probe = new Audio()
    probe.preload = 'metadata'
    probe.onloadedmetadata = () => {
      songForm.value.durationText = formatTime(probe.duration)
      URL.revokeObjectURL(url)
      resolve()
    }
    probe.onerror = () => {
      URL.revokeObjectURL(url)
      resolve()
    }
    probe.src = url
  })
}

async function applyAudioFile(file, { parseMeta = false } = {}) {
  if (!isAllowedAudioFile(file)) {
    ElMessage.warning(`仅支持 ${AUDIO_EXT_HINT} 格式`)
    return false
  }
  if (file.size > AUDIO_MAX_BYTES) {
    ElMessage.warning('歌曲不能超过 50MB')
    return false
  }
  audioFile.value = file
  songForm.value.audioName = file.name
  if (!songForm.value.title) {
    songForm.value.title = fileStem(file.name)
  }
  await fillDurationFromAudio(file)
  await refreshComposeAudioWave(file)
  if (!parseMeta) return true
  const meta = await enrichFromBackend(file)
  if (meta) {
    applyParseMetaToForm(meta)
    songForm.value.parsed = true
  }
  return meta
}

async function applyCoverFile(file) {
  coverFile.value = file
  songForm.value.coverName = file.name
  refreshComposeCoverPreview(file)
  await syncComposePreviewMedia()
}

async function applyLrcFile(file) {
  lrcFile.value = file
  songForm.value.lrcName = file.name
  songForm.value.lrcText = await file.text()
  applyLyricText(songForm.value.lrcText)
  await syncComposePreviewMedia()
}

async function onUploadFileChange(kind, event) {
  const file = event?.target?.files?.[0]
  event.target.value = ''
  if (!file) return
  if (kind === 'audio') {
    const ok = await applyAudioFile(file, { parseMeta: false })
    if (!ok) return
    await syncComposePreviewMedia()
    ElMessage.success('歌曲已上传，可在右上角试听')
    return
  }
  if (kind === 'cover') await applyCoverFile(file)
  if (kind === 'lrc') await applyLrcFile(file)
}

function onOneClickParse() {
  if (composeLocked.value) {
    ElMessage.warning('审核中或已发布的歌曲不能再编辑')
    return
  }
  parseInputRef.value?.click()
}

async function onParseFilesChange(event) {
  const files = Array.from(event?.target?.files || [])
  event.target.value = ''
  if (!files.length) return

  let audioCount = 0
  let coverCount = 0
  let lrcCount = 0
  let audio = null
  let cover = null
  let lrc = null
  for (const file of files) {
    const name = String(file.name || '').toLowerCase()
    if (/\.(mp3|wav|flac|m4a)$/i.test(name)) {
      audioCount += 1
      if (!audio) audio = file
    } else if (/\.(jpg|jpeg|png|gif)$/i.test(name)) {
      coverCount += 1
      if (!cover) cover = file
    } else if (/\.(lrc|txt)$/i.test(name)) {
      lrcCount += 1
      if (!lrc) lrc = file
    }
  }

  if (audioCount > 1) {
    ElMessage.warning('一次只能上传一首歌')
    return
  }
  if (!audio && !cover && !lrc) {
    ElMessage.warning('未识别到可用的音频 / 封面 / 歌词文件')
    return
  }
  if (coverCount > 1) ElMessage.info('封面只需一张，已使用第一张')
  if (lrcCount > 1) ElMessage.info('歌词只需一份，已使用第一份')

  let meta = null
  let coverUrl = ''
  if (audio) {
    const result = await applyAudioFile(audio, { parseMeta: true })
    if (result === false) return
    meta = result || null
  }
  if (cover) {
    await applyCoverFile(cover)
    coverUrl = URL.createObjectURL(cover)
  } else if (meta?.coverBase64) {
    const mime = meta.coverMimeType || 'image/jpeg'
    const ext = mime.includes('png') ? 'png' : 'jpg'
    const embedded = base64ToFile(meta.coverBase64, mime, `${fileStem(audio.name)}_cover.${ext}`)
    await applyCoverFile(embedded)
    coverUrl = URL.createObjectURL(embedded)
  }
  if (lrc) {
    await applyLrcFile(lrc)
  }
  songForm.value.parsed = true
  if (audio) {
    await previewParsedLocal(audio, {
      title: songForm.value.title,
      artist: songForm.value.artist,
      album: songForm.value.album,
      durationText: songForm.value.durationText,
      lyricText: songForm.value.lrcText,
    }, coverUrl)
  }
  ElMessage.success(meta ? '已解析内嵌信息，可在右侧试听并核对后发布' : '已填入文件，请核对歌曲信息后发布')
}

function toggleComposeTag(tag) {
  const list = [...(songForm.value.tags || [])]
  const idx = list.indexOf(tag)
  if (idx >= 0) {
    list.splice(idx, 1)
  } else if (list.length < 5) {
    list.push(tag)
  } else {
    ElMessage.warning('最多选择 5 个标签')
    return
  }
  songForm.value.tags = list
}

function openComposeLrc() {
  showComposeLrc.value = true
  editingComposeLrc.value = false
}

function closeComposeLrc() {
  showComposeLrc.value = false
  editingComposeLrc.value = false
  if (mineTab.value === 'compose' && songForm.value.lrcText) {
    applyLyricText(songForm.value.lrcText)
    syncLocalPreviewFromForm()
  }
}

function statusLabel(status) {
  if (status === 'uploading') return '上传中'
  if (status === 'reviewing') return '审核中'
  if (status === 'rejected') return '未通过'
  if (status === 'published') return '已发布'
  return '未发布'
}

function isServiceReviewError(track) {
  if (!track) return false
  if (track.reviewKind === 'service_error') return true
  const reason = String(track.reviewReason || '')
  return reason.includes('内部错误') || reason.includes('暂时不可用')
}

function uploadActionLabel(track) {
  if (track.status === 'reviewing') {
    return isServiceReviewError(track) ? '重新审核' : '查看'
  }
  if (track.status === 'rejected') {
    return isServiceReviewError(track) ? '重新审核' : '重新编辑'
  }
  return '继续编辑'
}

async function onUploadAction(track) {
  if (isServiceReviewError(track)) {
    if (!(await ensureLoggedIn())) return
    try {
      await retryArticleMusicAudit(track.id)
      ElMessage.success('已重新提交审核')
      await loadMineLists()
    } catch (error) {
      ElMessage.error(extractApiErrorMessage(error) || '重新审核失败')
    }
    return
  }
  continueEdit(track)
}

function continueEdit(track) {
  mineTab.value = 'compose'
  audioFile.value = null
  coverFile.value = null
  lrcFile.value = null
  revokeComposeCoverPreview()
  revokeLocalPreview()
  composeWaveHeights.value = Array(WAVE_BAR_COUNT).fill(10)
  songForm.value = {
    ...emptySongForm(),
    id: track.id || null,
    title: track.title || '',
    artist: track.artist || '',
    album: track.album || '',
    durationText: track.durationText || '',
    lrcText: track.lyricText || '',
    tags: Array.isArray(track.tags) ? [...track.tags]
      : (Array.isArray(track.moodTags) ? [...track.moodTags] : []),
    status: track.status || '',
    audioName: track.audioUrl ? '已上传音频' : '',
    coverName: track.coverUrl ? '已上传封面' : '',
    lrcName: track.lrcUrl || track.lyricText ? '已有歌词' : '',
    parsed: false,
  }
  if (track.coverUrl) {
    composeCoverPreviewUrl.value = track.coverUrl
  }
  if (track.audioUrl) {
    const next = {
      ...track,
      tags: Array.isArray(track.tags) ? [...track.tags] : [],
    }
    previewTrack.value = next
    applyDraftSelection(next)
    loadAudio(track.audioUrl)
    if (track.lrcUrl) {
      loadLrc(track.lrcUrl)
    } else if (track.lyricText) {
      applyLyricText(track.lyricText)
    } else {
      timedLrcLines.value = []
      plainLrcLines.value = []
    }
  }
}

function resetCompose() {
  songForm.value = emptySongForm()
  audioFile.value = null
  coverFile.value = null
  lrcFile.value = null
  composeWaveHeights.value = Array(WAVE_BAR_COUNT).fill(10)
  revokeComposeCoverPreview()
  closeTrimDialog()
}

function buildUploadFormData(action) {
  const form = new FormData()
  form.append('action', action)
  if (songForm.value.id) form.append('id', String(songForm.value.id))
  form.append('title', songForm.value.title.trim())
  form.append('artist', songForm.value.artist.trim())
  form.append('album', songForm.value.album.trim())
  form.append('durationText', songForm.value.durationText.trim())
  form.append('lyricText', songForm.value.lrcText || '')
  if (songForm.value.tags?.length) {
    form.append('moodTags', JSON.stringify(songForm.value.tags))
  }
  if (audioFile.value) form.append('audio', audioFile.value)
  if (coverFile.value) form.append('cover', coverFile.value)
  if (lrcFile.value) form.append('lrc', lrcFile.value)
  return form
}

async function saveComposeDraft() {
  await submitMusic('draft')
}

async function submitCompose() {
  await submitMusic('publish')
}

async function submitMusic(action) {
  if (composeLocked.value) {
    ElMessage.warning('审核中或已发布的歌曲不能再编辑')
    return
  }
  if (!(await ensureLoggedIn())) return
  if (composeSubmitting.value) return
  if (!songForm.value.title.trim() || !songForm.value.artist.trim()) {
    ElMessage.warning('请填写歌名和歌手')
    return
  }
  if (!audioFile.value && !songForm.value.id) {
    ElMessage.warning('请先选择歌曲本体')
    return
  }
  composeSubmitting.value = true
  try {
    const res = await uploadArticleMusic(buildUploadFormData(action))
    if (res?.code !== 0) {
      ElMessage.error(res?.message || '保存失败')
      return
    }
    ElMessage.success(action === 'publish' ? '提交成功，审核将在后台进行' : '草稿已保存')
    resetCompose()
    mineTab.value = action === 'publish' ? 'upload' : 'upload'
    await loadMineLists()
  } catch (error) {
    ElMessage.error(extractApiErrorMessage(error) || '保存失败')
  } finally {
    composeSubmitting.value = false
  }
}

async function onAiRecommend() {
  const title = String(props.articleTitle || '').trim()
  const content = String(props.articleContent || '').replace(/<[^>]+>/g, '').trim()
  if (!title && content.length < 20) {
    ElMessage.warning('请先在帖子中填写标题或正文，再使用 AI 推荐')
    return
  }
  if (!(await ensureLoggedIn())) return
  if (aiLoading.value) return
  aiLoading.value = true
  loading.value = true
  loadError.value = ''
  aiEmptyHint.value = ''
  catalogAiMode.value = 'recommend'
  catalogSearchMode.value = false
  keyword.value = ''
  appliedKeyword.value = ''
  activeMood.value = ''
  catalogPageNum.value = 1
  catalogPageTotal.value = 1
  try {
    const res = await recommendArticleMusic({
      title,
      content,
      editorMode: 'rich',
    })
    const data = res?.data || {}
    tracks.value = Array.isArray(data.tracks) ? data.tracks : []
    aiEmptyHint.value = tracks.value.length ? '' : (data.emptyHint || 'AI没找到符合你帖子的歌曲，试试自己上传吧')
    if (Array.isArray(data.moods) && data.moods.length) {
      activeMood.value = ''
    }
    syncPreviewFavorite()
  } catch (error) {
    tracks.value = []
    loadError.value = extractApiErrorMessage(error) || 'AI 推荐失败'
  } finally {
    aiLoading.value = false
    loading.value = false
  }
}

function formatTime(sec) {
  const n = Math.max(0, Math.floor(Number(sec) || 0))
  const m = Math.floor(n / 60)
  const s = n % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function playPrev() {
  shiftTrack(-1)
}

function playNext() {
  shiftTrack(1)
}

function shiftTrack(delta) {
  const list = filteredTracks.value
  if (!list.length || !previewTrack.value) return
  const idx = list.findIndex((t) => t.musicKey === previewTrack.value.musicKey)
  const next = list[(idx + delta + list.length) % list.length]
  selectTrack(next)
}

async function loadLrc(url) {
  try {
    const res = await fetch(url)
    if (!res.ok) {
      timedLrcLines.value = []
      plainLrcLines.value = []
      return
    }
    const text = await res.text()
    applyLyricText(text)
  } catch {
    timedLrcLines.value = []
    plainLrcLines.value = []
  }
}

function isRecentPlaying(track) {
  return Boolean(
    playing.value
    && previewTrack.value?.musicKey
    && track?.musicKey
    && previewTrack.value.musicKey === track.musicKey,
  )
}

function trackPlayPayload(track) {
  if (!track?.musicKey || !track?.audioUrl) return null
  return {
    musicKey: track.musicKey,
    title: track.title,
    artist: track.artist,
    album: track.album,
    durationText: track.durationText,
    coverUrl: track.coverUrl,
    audioUrl: track.audioUrl,
    lrcUrl: track.lrcUrl,
  }
}

async function loadRecent(page = recentPageNum.value) {
  if (!userStore.isLoggedIn) {
    recentTracks.value = []
    recentPageNum.value = 1
    recentPageTotal.value = 1
    return
  }
  recentLoading.value = true
  try {
    const res = await listMusicRecentPlays(page, RECENT_PAGE_SIZE)
    const pageData = res?.data || {}
    recentTracks.value = Array.isArray(pageData.records) ? pageData.records : []
    recentPageNum.value = pageData.pageNum || page
    recentPageTotal.value = Math.max(1, Number(pageData.pages) || 1)
  } catch {
    recentTracks.value = []
    recentPageNum.value = 1
    recentPageTotal.value = 1
  } finally {
    recentLoading.value = false
  }
}

async function pushRecent(track) {
  if (!userStore.isLoggedIn) return
  const payload = trackPlayPayload(track)
  if (!payload) return
  try {
    await recordMusicRecentPlay(payload)
    if (recentPageNum.value === 1) {
      await loadRecent(1)
    }
  } catch {
    // ignore record failure
  }
}

function onRecentPageChange(page) {
  recentPageNum.value = page
  loadRecent(page)
}

async function openTrimDialog() {
  if (composeLocked.value) {
    ElMessage.warning('审核中或已发布的歌曲不能再编辑')
    return
  }
  if (!audioFile.value) {
    ElMessage.warning('请先上传歌曲本体')
    return
  }
  trimLoading.value = true
  showTrimDialog.value = true
  try {
    revokeTrimPreview()
    const buffer = await decodeAudioSource(audioFile.value)
    trimAudioBuffer.value = buffer
    trimStartSec.value = 0
    trimEndSec.value = buffer.duration
    trimPlayheadSec.value = 0
    trimWaveHeights.value = peaksFromBuffer(buffer, WAVE_BAR_COUNT)
    trimBlobUrl.value = URL.createObjectURL(audioFile.value)
    await nextTick()
    const el = trimAudioRef.value
    if (el) {
      el.src = trimBlobUrl.value
      el.load()
    }
  } catch {
    showTrimDialog.value = false
    ElMessage.warning('无法解析音频，暂不支持裁剪')
  } finally {
    trimLoading.value = false
  }
}

function closeTrimDialog() {
  showTrimDialog.value = false
  trimAudioBuffer.value = null
  trimStartSec.value = 0
  trimEndSec.value = 0
  trimPlayheadSec.value = 0
  revokeTrimPreview()
}

function stopTrimPreview() {
  const el = trimAudioRef.value
  if (el) {
    el.pause()
  }
  trimPlaying.value = false
}

function onTrimTimeUpdate() {
  const el = trimAudioRef.value
  if (!el) return
  trimPlayheadSec.value = el.currentTime
  if (!trimPlaying.value) return
  if (el.currentTime >= trimEndSec.value - 0.02) {
    el.pause()
    el.currentTime = trimStartSec.value
    trimPlayheadSec.value = trimStartSec.value
    trimPlaying.value = false
  }
}

function toggleTrimPlay() {
  const el = trimAudioRef.value
  if (!el || !trimBlobUrl.value) return
  if (trimPlaying.value) {
    el.pause()
    trimPlaying.value = false
    return
  }
  if (el.currentTime < trimStartSec.value || el.currentTime >= trimEndSec.value) {
    el.currentTime = trimStartSec.value
  }
  el.play().then(() => {
    trimPlaying.value = true
  }).catch(() => {
    trimPlaying.value = false
    ElMessage.warning('试听失败，请重试')
  })
}

function trimSecFromClientX(clientX, trackEl) {
  const total = trimAudioBuffer.value?.duration || 0
  if (!total || !trackEl) return 0
  const rect = trackEl.getBoundingClientRect()
  const ratio = Math.min(1, Math.max(0, (clientX - rect.left) / rect.width))
  return ratio * total
}

function onTrimTrackClick(event) {
  const sec = trimSecFromClientX(event.clientX, event.currentTarget)
  const el = trimAudioRef.value
  if (!el) return
  const next = Math.min(trimEndSec.value - 0.05, Math.max(trimStartSec.value, sec))
  el.currentTime = next
  trimPlayheadSec.value = next
}

function onTrimHandleDown(kind, event) {
  event.preventDefault()
  event.stopPropagation()
  const track = event.currentTarget?.closest('.music-hall__trim-track')
  if (!track) return
  const minGap = 1
  const move = (moveEvent) => {
    const sec = trimSecFromClientX(moveEvent.clientX, track)
    if (kind === 'start') {
      trimStartSec.value = Math.min(sec, trimEndSec.value - minGap)
    } else {
      trimEndSec.value = Math.max(sec, trimStartSec.value + minGap)
    }
  }
  const up = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', up)
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', up)
}

async function applyTrim() {
  if (!audioFile.value || !trimAudioBuffer.value) return
  if (trimDurationSec.value < 1) {
    ElMessage.warning('裁剪片段至少 1 秒')
    return
  }
  const total = trimAudioBuffer.value.duration || 0
  const isFullRange = trimStartSec.value <= 0.05 && trimEndSec.value >= total - 0.05
  if (isFullRange) {
    closeTrimDialog()
    ElMessage.info('未调整裁剪区间，已保留原音频')
    return
  }
  if (!(await ensureLoggedIn())) return
  trimApplying.value = true
  try {
    const res = await trimArticleMusic(audioFile.value, trimStartSec.value, trimEndSec.value)
    const meta = res?.data
    if (!meta?.audioBase64) {
      ElMessage.error(res?.message || '裁剪失败')
      return
    }
    const trimmed = base64ToFile(
      meta.audioBase64,
      meta.mimeType || 'audio/mpeg',
      meta.fileName || `${fileStem(audioFile.value.name)}_trim.mp3`,
    )
    audioFile.value = trimmed
    songForm.value.audioName = trimmed.name
    songForm.value.durationText = meta.durationText || formatTime(meta.durationSeconds || trimDurationSec.value)
    await refreshComposeAudioWave(trimmed)
    closeTrimDialog()
    ElMessage.success('已应用裁剪，时长已更新')
  } catch (error) {
    ElMessage.error(extractApiErrorMessage(error) || '裁剪失败，请重试')
  } finally {
    trimApplying.value = false
  }
}
