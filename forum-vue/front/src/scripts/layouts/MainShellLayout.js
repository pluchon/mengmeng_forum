import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AnnouncementBoard from '@/components/common/AnnouncementBoard.vue'
import HomeSidebar from '@/components/layout/HomeSidebar.vue'
import HomeTopBar from '@/components/layout/HomeTopBar.vue'
import SiteIcpBar from '@/components/layout/SiteIcpBar.vue'
import { provideHomeShellContext } from '@/composables/useHomeShell'

const route = useRoute()
const isShellBare = computed(() => route.matched.some((r) => r.meta?.shellBare))
const isShellParticle = computed(() => route.matched.some((r) => r.meta?.shellParticle))

const { announcementRef } = provideHomeShellContext()
