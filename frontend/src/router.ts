import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '@/views/DashboardView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'dashboard', component: DashboardView },
    { path: '/stock/:symbol', name: 'stock', component: () => import('@/views/StockDetailView.vue'), props: true },
    { path: '/news', name: 'news', component: () => import('@/views/NewsView.vue') },
    { path: '/portfolio', name: 'portfolio', component: () => import('@/views/PortfolioView.vue') },
    { path: '/ai', name: 'ai', component: () => import('@/views/AiAssistantView.vue') },
    { path: '/settings', name: 'settings', component: () => import('@/views/SettingsView.vue') },
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})
