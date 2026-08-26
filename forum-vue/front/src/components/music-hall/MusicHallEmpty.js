import emptyMusicUrl from '@/assets/images/musiuc_not.png'

export default {
  name: 'MusicHallEmpty',
  props: {
    text: { type: String, default: '暂无内容' },
    compact: { type: Boolean, default: false },
  },
  setup() {
    return { emptyMusicUrl }
  },
}
