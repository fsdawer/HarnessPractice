
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/HomeView.vue'),
  },
  {
    path: '/nearby',
    name: 'Nearby',
    component: () => import('@/views/NearbyView.vue'),
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { guestOnly: true },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { guestOnly: true },
  },
  {
    path: '/ranking',
    name: 'Ranking',
    component: () => import('@/views/RankingView.vue'),
  },
  // 주의: /stylist/me, /stylist/manage, /stylist/reservations 등 정적 경로를 /:id 보다 먼저 선언
  {
    path: '/stylist/dashboard',
    name: 'StylistDashboard',
    component: () => import('@/views/StylistDashboardView.vue'),
    meta: { requiresAuth: true, stylistOnly: true },
  },
  {
    path: '/stylist/manage',
    name: 'StylistManage',
    component: () => import('@/views/StylistManageView.vue'),
    meta: { requiresAuth: true, stylistOnly: true },
  },
  {
    path: '/stylist/reservations',
    name: 'StylistReservations',
    component: () => import('@/views/StylistReservationsView.vue'),
    meta: { requiresAuth: true, stylistOnly: true },
  },
  {
    path: '/stylist/:id',
    name: 'StylistDetail',
    component: () => import('@/views/StylistDetailView.vue'),
  },
  {
    path: '/booking/:stylistId',
    name: 'Booking',
    component: () => import('@/views/BookingView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/payment/success',
    name: 'PaymentSuccess',
    component: () => import('@/views/PaymentSuccessView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/payment/fail',
    name: 'PaymentFail',
    component: () => import('@/views/PaymentFailView.vue'),
  },
  {
    path: '/payment/:reservationId',
    name: 'Payment',
    component: () => import('@/views/PaymentView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/mypage',
    name: 'MyPage',
    component: () => import('@/views/MyPageView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/mypage/reservations',
    name: 'MyReservations',
    component: () => import('@/views/MyPageReservationsView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/mypage/favorites',
    name: 'MyFavorites',
    component: () => import('@/views/MyPageFavoritesView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/mypage/reviews',
    name: 'MyReviews',
    component: () => import('@/views/MyPageReviewsView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/mypage/coupons',
    name: 'MyCoupons',
    component: () => import('@/views/MyPageCouponsView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/mypage/settings',
    name: 'MySettings',
    component: () => import('@/views/MyPageSettingsView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/chat/:roomId',
    name: 'Chat',
    component: () => import('@/views/ChatView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/oauth2/callback',
    name: 'OAuth2Callback',
    component: () => import('@/views/OAuth2CallbackView.vue'),
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFoundView.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

// Navigation guard
router.beforeEach((to) => {
  const auth = useAuthStore()

  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guestOnly && auth.isLoggedIn) {
    return { name: 'Home' }
  }
  if (to.meta.stylistOnly && auth.user?.role !== 'STYLIST') {
    return { name: 'Home' }
  }
})

export default router
