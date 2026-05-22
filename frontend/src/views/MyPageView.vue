<template>
  <main class="page">
    <div class="container">

      <!-- 프로필 헤더 -->
      <div class="profile-card card">
        <div class="avatar">{{ user?.name?.[0] ?? 'U' }}</div>
        <div class="profile-info">
          <p class="profile-name">{{ user?.name }}</p>
          <p class="profile-email">{{ user?.email }}</p>
          <span class="role-badge" :class="user?.role === 'STYLIST' ? 'badge-accent' : 'badge-gray'">
            {{ user?.role === 'STYLIST' ? '스타일리스트' : '일반 회원' }}
          </span>
        </div>
      </div>

      <!-- 메뉴 섹션들 -->
      <div class="menu-section card">
        <p class="section-label">예약 · 서비스</p>
        <RouterLink to="/mypage/reservations" class="menu-item">
          <span class="menu-icon">📋</span>
          <span class="menu-title">예약 내역</span>
          <span class="menu-arrow">›</span>
        </RouterLink>
        <RouterLink v-if="user?.role === 'STYLIST'" to="/stylist/reservations" class="menu-item">
          <span class="menu-icon">💼</span>
          <span class="menu-title">예약 관리</span>
          <span class="menu-arrow">›</span>
        </RouterLink>
      </div>

      <div class="menu-section card">
        <p class="section-label">나의 활동</p>
        <RouterLink to="/mypage/favorites" class="menu-item">
          <span class="menu-icon">❤️</span>
          <span class="menu-title">관심 디자이너</span>
          <span class="menu-arrow">›</span>
        </RouterLink>
        <RouterLink to="/mypage/reviews" class="menu-item">
          <span class="menu-icon">⭐</span>
          <span class="menu-title">리뷰 관리</span>
          <span class="menu-arrow">›</span>
        </RouterLink>
        <RouterLink to="/notifications" class="menu-item">
          <span class="menu-icon">🔔</span>
          <span class="menu-title">알림</span>
          <span class="menu-arrow">›</span>
        </RouterLink>
        <RouterLink to="/chat" class="menu-item">
          <span class="menu-icon">💬</span>
          <span class="menu-title">채팅</span>
          <span class="menu-arrow">›</span>
        </RouterLink>
      </div>

      <div class="menu-section card">
        <p class="section-label">혜택</p>
        <RouterLink to="/mypage/coupons" class="menu-item">
          <span class="menu-icon">🎟️</span>
          <span class="menu-title">쿠폰</span>
          <span class="menu-arrow">›</span>
        </RouterLink>
        <button class="menu-item" @click="comingSoon">
          <span class="menu-icon">🎉</span>
          <span class="menu-title">이벤트</span>
          <span class="menu-badge-soon">준비 중</span>
        </button>
      </div>

      <div class="menu-section card">
        <p class="section-label">계정</p>
        <RouterLink to="/mypage/settings" class="menu-item">
          <span class="menu-icon">👤</span>
          <span class="menu-title">프로필 관리</span>
          <span class="menu-arrow">›</span>
        </RouterLink>
        <RouterLink v-if="user?.role === 'STYLIST'" to="/stylist/manage" class="menu-item">
          <span class="menu-icon">✂️</span>
          <span class="menu-title">스타일리스트 프로필</span>
          <span class="menu-arrow">›</span>
        </RouterLink>
      </div>

    </div>

    <!-- 준비 중 토스트 -->
    <div v-if="toastVisible" class="toast">준비 중인 서비스입니다.</div>
  </main>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useAuthStore } from '@/stores/authStore'

const auth = useAuthStore()
const user = computed(() => auth.user)

const toastVisible = ref(false)
let toastTimer = null

function comingSoon() {
  if (toastTimer) clearTimeout(toastTimer)
  toastVisible.value = true
  toastTimer = setTimeout(() => { toastVisible.value = false }, 2000)
}
</script>

<style scoped>
.container { max-width: 480px; padding: 16px; }

.profile-card {
  display: flex; align-items: center; gap: 16px;
  padding: 20px; margin-bottom: 12px;
}
.avatar {
  width: 56px; height: 56px; border-radius: 50%;
  background: var(--primary); color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 22px; font-weight: 800; flex-shrink: 0;
}
.profile-info { min-width: 0; }
.profile-name { font-size: 18px; font-weight: 700; margin-bottom: 2px; }
.profile-email { font-size: 13px; color: var(--text-muted); margin-bottom: 6px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.menu-section {
  padding: 0; margin-bottom: 12px; overflow: hidden;
}
.section-label {
  font-size: 11px; font-weight: 700; color: var(--text-muted);
  letter-spacing: 0.06em; text-transform: uppercase;
  padding: 14px 16px 6px;
}
.menu-item {
  display: flex; align-items: center; gap: 12px;
  width: 100%; padding: 14px 16px;
  background: none; border: none; text-align: left; cursor: pointer;
  color: var(--text); text-decoration: none;
  border-top: 1px solid var(--border);
  transition: background 0.15s;
}
.menu-item:first-of-type { border-top: none; }
.menu-item:hover { background: var(--bg-surface); }
.menu-icon { font-size: 18px; width: 24px; text-align: center; flex-shrink: 0; }
.menu-title { flex: 1; font-size: 15px; font-weight: 500; }
.menu-arrow { font-size: 20px; color: var(--text-muted); line-height: 1; }
.menu-badge-soon {
  font-size: 10px; font-weight: 700; background: var(--bg-surface);
  color: var(--text-muted); padding: 2px 7px; border-radius: 20px;
  border: 1px solid var(--border);
}

.toast {
  position: fixed; bottom: 80px; left: 50%; transform: translateX(-50%);
  background: rgba(0,0,0,0.75); color: #fff;
  padding: 10px 20px; border-radius: 20px; font-size: 13px;
  pointer-events: none; z-index: 999;
}
</style>
