import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/Login.vue') },
  { path: '/', name: 'home', component: () => import('../views/Home.vue') },
  { path: '/movie/:id', name: 'movie-detail', component: () => import('../views/MovieDetail.vue') },
  { path: '/cinemas', name: 'cinemas', component: () => import('../views/Cinemas.vue') },
  { path: '/cinema/:id', name: 'cinema-detail', component: () => import('../views/CinemaDetail.vue') },
  { path: '/session/:id', name: 'session-detail', component: () => import('../views/SessionDetail.vue') },
  { path: '/seckill/:id', name: 'seat-select', component: () => import('../views/SeatSelect.vue'), meta: { requiresAuth: true } },
  { path: '/pay/:orderId', name: 'pay', component: () => import('../views/Pay.vue'), meta: { requiresAuth: true } },
  { path: '/orders', name: 'orders', component: () => import('../views/Orders.vue'), meta: { requiresAuth: true } },
  { path: '/chat', name: 'chat', component: () => import('../views/Chat.vue'), meta: { requiresAuth: true } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 登录守卫：requiresAuth 的路由未登录跳登录页
router.beforeEach((to) => {
  const logged = !!localStorage.getItem('token')
  if (to.meta.requiresAuth && !logged) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && logged) {
    return { path: '/' }
  }
  return true
})

export default router
