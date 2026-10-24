import { createRouter, createWebHistory } from 'vue-router'

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
    { path: '/finale', component: () => import('./views/FinaleView.vue') },
    { path: '/me', component: () => import('./views/ProfileView.vue') },
  ],
})
