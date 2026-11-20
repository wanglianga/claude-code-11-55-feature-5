import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  { path: '/login', component: () => import('./views/LoginView.vue') },
  { path: '/', component: () => import('./views/DashboardView.vue') },
  { path: '/sources', component: () => import('./views/SourcesView.vue') },
  { path: '/factors', component: () => import('./views/FactorsView.vue') },
  { path: '/runs', component: () => import('./views/RunsView.vue') },
  { path: '/runs/:id', component: () => import('./views/RunDetailView.vue') },
  { path: '/conversion', component: () => import('./views/ConversionView.vue') },
  { path: '/impacts', component: () => import('./views/ImpactsView.vue') },
  { path: '/impacts/:id', component: () => import('./views/ImpactDetailView.vue') },
  { path: '/reports', component: () => import('./views/ReportsView.vue') },
  { path: '/reports/:id', component: () => import('./views/ReportDetailView.vue') }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach(to => {
  const token = localStorage.getItem('token')
  if (!token && to.path !== '/login') return '/login'
  if (token && to.path === '/login') return '/'
  return true
})

export default router
