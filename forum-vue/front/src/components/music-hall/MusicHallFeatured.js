import { computed } from 'vue'
import { MagicStick, VideoPlay } from '@element-plus/icons-vue'
import MusicHallEmpty from './MusicHallEmpty.vue'

export default {
  name: 'MusicHallFeatured',
  components: { VideoPlay, MagicStick, MusicHallEmpty },
  props: {
    track: { type: Object, default: null },
    loading: { type: Boolean, default: false },
    activeMusicKey: { type: String, default: '' },
  },
  emits: ['play'],
  setup(props, { emit }) {
    const isPlaying = computed(() => props.track?.musicKey === props.activeMusicKey)

    const coverStyle = computed(() => {
      const track = props.track
      if (!track?.coverUrl) return {}
      return { backgroundImage: `url(${track.coverUrl})` }
    })

    const onPlay = () => {
      if (props.track) emit('play', props.track)
    }

    return { isPlaying, coverStyle, onPlay }
  },
}
