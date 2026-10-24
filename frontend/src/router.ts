import { createRouter, createWebHistory } from 'vue-router'
import { useMe } from './stores/me'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: () => import('./views/HomeView.vue') },
    { path: '/beers', component: () => import('./views/BeersView.vue') },
    { path: '/beers/:id/rate', component: () => import('./views/RateView.vue'), props: true },
    { path: '/session', component: () => import('./views/SessionStartView.vue') },
    { path: '/session/:id', component: () => import('./views/SessionView.vue'), props: true },
    { path: '/join/:code?', component: () => import('./views/JoinView.vue'), props: true },
    { path: '/stats', component: () => import('./views/StatsView.vue') },
    { path: '/privacy', component: () => import('./views/PrivacyView.vue') },
    { path: '/consent', component: () => import('./views/ConsentView.vue') },
    { path: '/me', component: () => import('./views/ProfileView.vue') },
  ],
})

// Until the current consent version is accepted, every route leads to /consent. The server
// enforces the same rule on the API; this only saves the person a round of 403s.
router.beforeEach(async (to) => {
  // /privacy is public: no login, no consent, never a redirect.
  if (to.path === '/privacy') return
  const store = useMe()
  if (!store.me) await store.refresh().catch(() => {})
  if (store.me?.consentRequired && to.path !== '/consent') return '/consent'
})
