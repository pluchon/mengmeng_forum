import { onMounted, ref } from 'vue'
import {
  getMusicDiscoverFeatured,
  listMusicDiscoverRecommend,
  listMusicDiscoverHot,
} from '@/api/article'
import MusicHallFeatured from './MusicHallFeatured.vue'
import MusicHallRecommend from './MusicHallRecommend.vue'
import MusicMoodFilterDialog from './MusicMoodFilterDialog.vue'
import MusicHallHot from './MusicHallHot.vue'

const PAGE_SIZE = 6

export default {
  name: 'MusicHallDiscover',
  components: {
    MusicHallFeatured,
    MusicHallRecommend,
    MusicMoodFilterDialog,
    MusicHallHot,
  },
  props: {
    activeMusicKey: { type: String, default: '' },
  },
  emits: ['play'],
  setup(props, { emit }) {
    const featured = ref(null)
    const featuredLoading = ref(false)

    // 只影响推荐：热榜的语义是全站热度排名，被个人筛选切一刀就不叫榜了。
    // 存 localStorage 而不是后端——为一个筛选器新建用户偏好表不划算。
    const moodFilter = ref(readSavedMoods())
    const moodDialogVisible = ref(false)

    const recommendTracks = ref([])
    const recommendLoading = ref(false)
    const recommendPageNum = ref(1)
    const recommendPageTotal = ref(1)

    const hotTracks = ref([])
    const hotLoading = ref(false)
    const hotPageNum = ref(1)
    const hotPageTotal = ref(1)

    const loadFeatured = async () => {
      featuredLoading.value = true
      try {
        const res = await getMusicDiscoverFeatured()
        featured.value = res?.data || null
      } catch {
        featured.value = null
      } finally {
        featuredLoading.value = false
      }
    }

    const loadRecommend = async (pageNum = recommendPageNum.value) => {
      recommendLoading.value = true
      try {
        const res = await listMusicDiscoverRecommend({
          pageNum,
          pageSize: PAGE_SIZE,
          excludeMusicKey: featured.value?.musicKey,
          moods: moodFilter.value.length ? moodFilter.value : undefined,
        })
        const page = res?.data
        recommendTracks.value = page?.records || []
        recommendPageNum.value = page?.pageNum || pageNum
        recommendPageTotal.value = page?.pages || 1
      } catch {
        recommendTracks.value = []
        recommendPageTotal.value = 1
      } finally {
        recommendLoading.value = false
      }
    }

    const onMoodFilterApply = (moods) => {
      moodFilter.value = Array.isArray(moods) ? moods : []
      saveMoods(moodFilter.value)
      // 换了筛选条件必须回第一页，否则会停在新条件下不存在的页码上
      recommendPageNum.value = 1
      loadRecommend(1)
    }

    const loadHot = async (pageNum = hotPageNum.value) => {
      hotLoading.value = true
      try {
        const res = await listMusicDiscoverHot({ pageNum, pageSize: PAGE_SIZE })
        const page = res?.data
        hotTracks.value = page?.records || []
        hotPageNum.value = page?.pageNum || pageNum
        hotPageTotal.value = page?.pages || 1
      } catch {
        hotTracks.value = []
        hotPageTotal.value = 1
      } finally {
        hotLoading.value = false
      }
    }

    const refreshAll = async () => {
      await loadFeatured()
      await Promise.all([loadRecommend(1), loadHot(1)])
    }

    const onPlay = (track) => emit('play', track)

    const onRecommendPageChange = (page) => {
      loadRecommend(page)
    }

    const onHotPageChange = (page) => {
      loadHot(page)
    }

    onMounted(() => {
      refreshAll()
    })

    return {
      featured,
      featuredLoading,
      moodFilter,
      moodDialogVisible,
      onMoodFilterApply,
      recommendTracks,
      recommendLoading,
      recommendPageNum,
      recommendPageTotal,
      hotTracks,
      hotLoading,
      hotPageNum,
      hotPageTotal,
      onPlay,
      onRecommendPageChange,
      onHotPageChange,
      refreshAll,
    }
  },
}

// 隐私模式或站点数据被清时 localStorage 会直接抛，不能让它拖垮整个发现页
const MOOD_FILTER_KEY = 'music-hall:recommend-moods'
const MOOD_FILTER_MAX = 5

function readSavedMoods() {
  try {
    const raw = window.localStorage.getItem(MOOD_FILTER_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.filter((item) => typeof item === 'string' && item.trim()).slice(0, MOOD_FILTER_MAX)
  } catch {
    return []
  }
}

function saveMoods(moods) {
  try {
    window.localStorage.setItem(MOOD_FILTER_KEY, JSON.stringify(moods.slice(0, MOOD_FILTER_MAX)))
  } catch {
    // 存不下就算了，本次会话内筛选依然有效
  }
}
