import { TrendCharts, VideoPlay } from '@element-plus/icons-vue'
import MusicHallEmpty from './MusicHallEmpty.vue'
import AppPagination from '@/components/common/AppPagination.vue'

export default {
  name: 'MusicHallHot',
  components: { VideoPlay, TrendCharts, MusicHallEmpty, AppPagination },
  props: {
    tracks: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    pageNum: { type: Number, default: 1 },
    pageTotal: { type: Number, default: 1 },
    activeMusicKey: { type: String, default: '' },
  },
  emits: ['play', 'page-change'],
  setup(props, { emit }) {
    const coverStyle = (track) => {
      if (!track?.coverUrl) return {}
      return { backgroundImage: `url(${track.coverUrl})` }
    }

    const onPlay = (track) => emit('play', track)
    const onPageChange = (page) => emit('page-change', page)

    return { coverStyle, onPlay, onPageChange }
  },
}
