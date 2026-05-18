import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'
import { captureFeedScroll, restoreFeedScroll } from '@/utils/feedScrollRestore'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior(to, from, savedPosition) {
    if (to.path === '/' && from.path.match(/^\/article\/[^/]+$/)) {
      return false
    }
    if (savedPosition) return savedPosition
    return { top: 0 }
  },
  routes: [
    {
      path: '/sign-in',
      name: 'signIn',
      component: () => import('../views/SignIn.vue'),
      meta: { public: true, layout: 'auth' },
    },
    {
      path: '/sign-up',
      name: 'signUp',
      component: () => import('../views/SignUp.vue'),
      meta: { public: true, layout: 'auth' },
    },
    {
      path: '/forgot-password',
      name: 'forgotPassword',
      component: () => import('../views/ForgotPassword.vue'),
      meta: { public: true, layout: 'auth' },
    },
    {
      path: '/',
      component: () => import('../layouts/MainShellLayout.vue'),
      meta: { shell: true },
      children: [
        {
          path: '',
          name: 'home',
          component: () => import('../views/HomeFeed.vue'),
          meta: { public: true },
        },
        {
          path: 'board/:id',
          name: 'articleList',
          component: () => import('../views/ArticleList.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'article/create',
          name: 'articleCreate',
          component: () => import('../views/ArticleCreate.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'article/edit/:id',
          name: 'articleEdit',
          component: () => import('../views/ArticleEdit.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'article/:id/cover',
          name: 'articleCoverSetup',
          component: () => import('../views/ArticleCoverSetup.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'article/:id/audit',
          name: 'articleAuditPending',
          component: () => import('../views/ArticleAuditPending.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'article/:id',
          name: 'articleDetail',
          component: () => import('../views/ArticleDetail.vue'),
          meta: { public: true },
        },
        {
          path: 'profile/:id?',
          name: 'profile',
          component: () => import('../views/Profile.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('../views/Settings.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'messages',
          name: 'messages',
          component: () => import('../views/MessageRouteTrigger.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'creative',
          name: 'creative',
          component: () => import('../views/CreativeCenter.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'emoji-shop/create',
          redirect: { path: '/emoji-shop', query: { upload: '1' } },
        },
        {
          path: 'emoji-shop/:id(\\d+)',
          redirect: (to) => ({ path: '/emoji-shop', query: { detail: to.params.id } }),
        },
        {
          path: 'emoji-shop',
          name: 'emojiShop',
          component: () => import('../views/EmojiShop.vue'),
          meta: { public: true },
        },
        {
          path: 'points',
          name: 'pointsWallet',
          component: () => import('../views/PointsWallet.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'lottery',
          name: 'lottery',
          component: () => import('../views/LotteryView.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'vip',
          name: 'vipCenter',
          component: () => import('../views/VipCenter.vue'),
          meta: { public: true },
        },
        {
          path: 'checkin',
          name: 'checkin',
          component: () => import('../views/Checkin.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'search/user',
          name: 'searchUser',
          component: () => import('../views/SearchUser.vue'),
          meta: { public: true },
        },
        {
          path: 'search',
          name: 'searchArticle',
          component: () => import('../views/SearchArticle.vue'),
          meta: { public: true },
        },
        {
          path: 'favorites/folder/:folderId(\\d+)',
          name: 'favoriteFolder',
          component: () => import('../views/FavoriteFolder.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'favorites',
          name: 'favorites',
          component: () => import('../views/Favorites.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'privacy',
          name: 'privacy',
          component: () => import('../views/Privacy.vue'),
          meta: { public: true, shellBare: true },
        },
        {
          path: 'terms',
          name: 'terms',
          component: () => import('../views/Terms.vue'),
          meta: { public: true, shellBare: true },
        },
        { path: ':pathMatch(.*)*', redirect: '/' },
      ],
    },
  ],
})

router.beforeEach((to, from) => {
  const userStore = useUserStore()
  const isLoggedIn = userStore.isLoggedIn

  if (from.path === '/' && /^\/article\/[^/]+$/.test(to.path)) {
    captureFeedScroll()
  }

  if (to.meta.requiresAuth && !isLoggedIn) {
    return '/sign-in'
  }
  if (isLoggedIn && (to.path === '/sign-in' || to.path === '/sign-up')) {
    return '/'
  }
  return true
})

export default router
