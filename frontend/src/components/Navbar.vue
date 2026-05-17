<template>
  <nav class="navbar">
    <div class="container nav-inner">
      <RouterLink to="/" class="nav-logo">
        <span class="logo-mark">✦</span>
        <span class="logo-text">CutIng</span>
      </RouterLink>

      <ul class="nav-links">
        <li><RouterLink to="/" class="nav-link">홈</RouterLink></li>
        <li><RouterLink to="/nearby" class="nav-link">내 주변</RouterLink></li>
        <li><RouterLink to="/ranking" class="nav-link">랭킹</RouterLink></li>
        <li><RouterLink to="/community" class="nav-link">커뮤니티</RouterLink></li>
        <li><RouterLink to="/hair-analysis" class="nav-link nav-link--ai">AI 추천</RouterLink></li>
        <li v-if="auth.isStylist">
          <RouterLink to="/stylist/reservations" class="nav-link">예약 관리</RouterLink>
        </li>
        <li v-if="auth.isLoggedIn">
          <RouterLink to="/mypage" class="nav-link">마이페이지</RouterLink>
        </li>
      </ul>

      <div class="nav-actions">
        <template v-if="!auth.isLoggedIn">
          <RouterLink to="/login" class="btn btn-ghost btn-sm">로그인</RouterLink>
          <RouterLink to="/register" class="btn btn-primary btn-sm">회원가입</RouterLink>
        </template>
        <template v-else>
          <!-- 알림 벨 -->
          <div class="bell-wrap" ref="bellEl">
            <button class="bell-btn" @click.stop="toggleNotif" aria-label="알림">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
                <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
              </svg>
              <span v-if="notif.unreadCount > 0" class="bell-badge">{{ notif.unreadCount > 9 ? '9+' : notif.unreadCount }}</span>
            </button>

            <!-- 드롭다운 -->
            <Transition name="notif-drop">
              <div v-if="notifOpen" class="notif-dropdown">
                <div class="notif-header">
                  <span class="notif-title">알림</span>
                  <button v-if="notif.notifications.length > 0" class="notif-clear" @click="notif.clearAll()">모두 지우기</button>
                </div>
                <div class="notif-body">
                  <div v-if="notif.notifications.length === 0" class="notif-empty">
                    새 알림이 없습니다
                  </div>
                  <div
                    v-for="n in notif.notifications"
                    :key="n.id"
                    class="notif-item"
                    :class="{ unread: !n.read }"
                    @click="goNotif(n)"
                  >
                    <div class="notif-dot" :class="{ visible: !n.read }"></div>
                    <div class="notif-content">
                      <p class="notif-msg">{{ n.message }}</p>
                      <p class="notif-detail">{{ n.detail }}</p>
                    </div>
                  </div>
                </div>
              </div>
            </Transition>
          </div>

          <div class="nav-user-wrap">
            <span class="nav-user-dot"></span>
            <span class="nav-user">{{ auth.user?.name }}</span>
          </div>
          <button class="btn btn-ghost btn-sm" @click="handleLogout">로그아웃</button>
        </template>
        <button class="hamburger" :class="{ open: mobileOpen }" @click="mobileOpen = !mobileOpen" aria-label="메뉴">
          <span></span><span></span><span></span>
        </button>
      </div>
    </div>

    <Transition name="drawer">
      <div v-if="mobileOpen" class="mobile-drawer">
        <RouterLink to="/" class="drawer-item" @click="mobileOpen = false">홈</RouterLink>
        <RouterLink to="/nearby" class="drawer-item" @click="mobileOpen = false">내 주변</RouterLink>
        <RouterLink to="/ranking" class="drawer-item" @click="mobileOpen = false">랭킹</RouterLink>
        <RouterLink to="/community" class="drawer-item" @click="mobileOpen = false">커뮤니티</RouterLink>
        <RouterLink to="/hair-analysis" class="drawer-item drawer-item--ai" @click="mobileOpen = false">✨ AI 헤어 추천</RouterLink>
        <RouterLink v-if="auth.isStylist" to="/stylist/reservations" class="drawer-item" @click="mobileOpen = false">예약 관리</RouterLink>
        <RouterLink v-if="auth.isLoggedIn" to="/mypage" class="drawer-item" @click="mobileOpen = false">마이페이지</RouterLink>
        <template v-if="!auth.isLoggedIn">
          <RouterLink to="/login" class="drawer-item" @click="mobileOpen = false">로그인</RouterLink>
          <RouterLink to="/register" class="drawer-item drawer-item--primary" @click="mobileOpen = false">회원가입</RouterLink>
        </template>
        <template v-else>
          <button class="drawer-item drawer-item--danger" @click="handleLogout">로그아웃</button>
        </template>
      </div>
    </Transition>
  </nav>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useNotificationStore } from '@/stores/notificationStore'

const auth = useAuthStore()
const notif = useNotificationStore()
const router = useRouter()
const mobileOpen = ref(false)
const notifOpen = ref(false)
const bellEl = ref(null)

async function handleLogout() {
  await auth.logout()
  mobileOpen.value = false
  notifOpen.value = false
  router.push('/')
}

function toggleNotif() {
  notifOpen.value = !notifOpen.value
  if (notifOpen.value) notif.markAllRead()
}

function goNotif(n) {
  notifOpen.value = false
  if (n.reservationId) router.push('/mypage')
}

function onClickOutside(e) {
  if (bellEl.value && !bellEl.value.contains(e.target)) notifOpen.value = false
}

onMounted(() => document.addEventListener('click', onClickOutside))
onUnmounted(() => document.removeEventListener('click', onClickOutside))
</script>

<style scoped>
.navbar {
  position: sticky; top: 0; z-index: 200;
  height: var(--navbar-h);
  background: rgba(255,255,255,0.96);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--border);
}
.nav-inner { height: var(--navbar-h); display: flex; align-items: center; gap: 24px; }
.nav-logo { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.logo-mark { font-size: 13px; color: var(--primary); }
.logo-text { font-size: 18px; font-weight: 700; color: var(--text); letter-spacing: -0.5px; }

.nav-links { display: flex; align-items: center; gap: 2px; list-style: none; flex: 1; }
.nav-link {
  padding: 6px 12px; border-radius: var(--radius-sm);
  font-size: 14px; font-weight: 500; color: var(--text-muted); transition: var(--transition);
}
.nav-link:hover { color: var(--text); background: var(--bg-surface); }
.nav-link.router-link-exact-active { color: var(--primary); font-weight: 600; }
.nav-link--ai { color: #7c5cef; font-weight: 600; }
.nav-link--ai:hover { background: #f0ebff; color: #5a3eaa; }

.nav-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.nav-user-wrap { display: flex; align-items: center; gap: 6px; }
.nav-user-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--success); }
.nav-user { font-size: 13px; font-weight: 600; color: var(--text-sub); }

/* Bell */
.bell-wrap { position: relative; }
.bell-btn {
  position: relative; display: flex; align-items: center; justify-content: center;
  width: 36px; height: 36px; border-radius: var(--radius-sm);
  background: transparent; border: 1px solid var(--border);
  color: var(--text-muted); cursor: pointer; transition: var(--transition);
}
.bell-btn:hover { color: var(--text); background: var(--bg-surface); }
.bell-badge {
  position: absolute; top: -4px; right: -4px;
  min-width: 16px; height: 16px; border-radius: 8px;
  background: var(--danger); color: #fff;
  font-size: 10px; font-weight: 700; line-height: 16px; text-align: center; padding: 0 3px;
  border: 1.5px solid #fff;
}

/* Dropdown */
.notif-dropdown {
  position: absolute; top: calc(100% + 8px); right: 0;
  width: 320px; max-height: 400px;
  background: #fff; border: 1px solid var(--border);
  border-radius: var(--radius-lg); box-shadow: 0 8px 24px rgba(0,0,0,0.12);
  z-index: 300; display: flex; flex-direction: column; overflow: hidden;
}
.notif-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 16px 12px; border-bottom: 1px solid var(--border);
}
.notif-title { font-size: 14px; font-weight: 700; color: var(--text); }
.notif-clear { font-size: 12px; color: var(--text-muted); background: none; border: none; cursor: pointer; padding: 0; }
.notif-clear:hover { color: var(--danger); }

.notif-body { overflow-y: auto; flex: 1; }
.notif-empty { padding: 32px 16px; text-align: center; font-size: 13px; color: var(--text-muted); }

.notif-item {
  display: flex; align-items: flex-start; gap: 10px;
  padding: 12px 16px; cursor: pointer; transition: var(--transition);
  border-bottom: 1px solid var(--border);
}
.notif-item:last-child { border-bottom: none; }
.notif-item:hover { background: var(--bg-surface); }
.notif-item.unread { background: #f0f4ff; }
.notif-item.unread:hover { background: #e8eeff; }

.notif-dot {
  width: 6px; height: 6px; border-radius: 50%;
  background: transparent; flex-shrink: 0; margin-top: 5px;
}
.notif-dot.visible { background: var(--accent); }

.notif-content { flex: 1; min-width: 0; }
.notif-msg { font-size: 13px; font-weight: 600; color: var(--text); }
.notif-detail { font-size: 12px; color: var(--text-muted); margin-top: 2px; }

/* Dropdown animation */
.notif-drop-enter-active, .notif-drop-leave-active { transition: all 0.15s ease; }
.notif-drop-enter-from, .notif-drop-leave-to { opacity: 0; transform: translateY(-6px); }

.hamburger {
  display: none; width: 36px; height: 36px; background: transparent;
  border: 1px solid var(--border); border-radius: var(--radius-sm);
  flex-direction: column; align-items: center; justify-content: center; gap: 4px; padding: 0;
}
.hamburger span { display: block; width: 16px; height: 1.5px; background: var(--text-sub); transition: var(--transition); transform-origin: center; }
.hamburger.open span:nth-child(1) { transform: translateY(5.5px) rotate(45deg); }
.hamburger.open span:nth-child(2) { opacity: 0; }
.hamburger.open span:nth-child(3) { transform: translateY(-5.5px) rotate(-45deg); }

.mobile-drawer {
  display: flex; flex-direction: column; background: #fff;
  border-bottom: 1px solid var(--border); padding: 8px 12px 16px; gap: 2px;
}
.drawer-item {
  display: block; padding: 11px 14px; border-radius: var(--radius-sm);
  font-size: 14px; font-weight: 500; color: var(--text-sub);
  background: transparent; border: none; text-align: left; transition: var(--transition); cursor: pointer; width: 100%;
}
.drawer-item:hover { background: var(--bg-surface); color: var(--text); }
.drawer-item--primary { color: var(--primary); font-weight: 600; }
.drawer-item--danger  { color: var(--danger); }
.drawer-item--ai { color: #7c5cef; font-weight: 600; }

.drawer-enter-active, .drawer-leave-active { transition: all 0.2s ease; overflow: hidden; }
.drawer-enter-from, .drawer-leave-to { max-height: 0; opacity: 0; }
.drawer-enter-to, .drawer-leave-from { max-height: 500px; opacity: 1; }

@media (max-width: 768px) {
  .nav-links { display: none; }
  .nav-actions .btn { display: none; }
  .nav-user-wrap { display: none; }
  .hamburger { display: flex; }
}
</style>
