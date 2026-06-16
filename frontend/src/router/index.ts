import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: () => import('../pages/PropertyList.vue') },
    { path: '/properties/:id', component: () => import('../pages/PropertyDetail.vue') },
    { path: '/analytics', component: () => import('../pages/DistrictAnalytics.vue') },
    { path: '/top-properties', component: () => import('../pages/TopProperties.vue') },
    { path: '/big-drop', component: () => import('../pages/BigDropOverview.vue') },
    { path: '/config', component: () => import('../pages/ScoringConfig.vue') },
    { path: '/config/districts', component: () => import('../pages/DistrictPriceConfig.vue') },
  ],
})

export default router
