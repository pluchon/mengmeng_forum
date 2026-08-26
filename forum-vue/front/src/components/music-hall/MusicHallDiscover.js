import { onMounted, ref } from 'vue'
import {
  getMusicDiscoverFeatured,
  listMusicDiscoverRecommend,
  listMusicDiscoverHot,
} from '@/api/article'
import MusicHallFeatured from './MusicHallFeatured.vue'
import MusicHallRecommend from './MusicHallRecommend.vue'
import MusicHallHot from './MusicHallHot.vue'

const PAGE_SIZE = 6

export default {
  name: 'MusicHallDiscover',
  components: {
    MusicHallFeatured,
    MusicHallRecommend,
    MusicHallHot,
  },
  props: {
    activeMusicKey: { type: String, default: '' },
  },
  emits: ['play'],
  setup(props, { emit }) {
    const featured = ref(null)
    const featuredLoading = ref(false)

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
