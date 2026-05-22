<template>
  <main class="page">
    <div class="container">
      <div class="page-header">
        <button class="back-btn" @click="$router.back()">‹</button>
        <h1 class="page-title">알림</h1>
        <button v-if="notifications.length > 0" class="read-all-btn" @click="markAllRead">전체 읽음</button>
      </div>

      <div v-if="loading" class="state-center"><div class="spinner"></div></div>

      <div v-else-if="notifications.length === 0" class="state-center">
        <p class="state-text">알림이 없습니다.</p>
      </div>

      <div v-else class="notif-list">
        <div
          v-for="n in notifications"
          :key="n.id"
          class="notif-item"
          :class="{ unread: !n.isRead }"
          @click="markRead(n)"
        >
          <div class="notif-dot-wrap">
            <div v-if="!n.isRead" class="notif-dot"></div>
          </div>
          <div class="notif-content">
            <p class="notif-type">{{ typeLabel(n.type) }}</p>
            <p class="notif-msg">{{ n.message }}</p>
            <p class="notif-time">{{ formatTime(n.createdAt) }}</p>
          </div>
        </div>
      </div>
    </div>
  </main>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { notificationApi } from '@/api/notification'

const notifications = ref([])
const loading = ref(true)

const TYPE_LABEL = {
  RESERVATION_CREATED: '예약 확정',
  RESERVATION_REMINDER_1D: '예약 리마인더 (내일)',
  RESERVATION_REMINDER_1H: '예약 리마인더 (1시간 전)',
  WAITING_AVAILABLE: '빈자리 알림',
}

function typeLabel(t) {
  return TYPE_LABEL[t] || t
}

function formatTime(s) {
  if (!s) return ''
  const d = new Date(s)
  const now = new Date()
  const diff = Math.floor((now - d) / 1000)
  if (diff < 60) return '방금 전'
  if (diff < 3600) return `${Math.floor(diff / 60)}분 전`
  if (diff < 86400) return `${Math.floor(diff / 3600)}시간 전`
  return `${d.getFullYear()}.${String(d.getMonth()+1).padStart(2,'0')}.${String(d.getDate()).padStart(2,'0')}`
}

async function loadNotifications() {
  loading.value = true
  try {
    const res = await notificationApi.getList()
    notifications.value = res.data || []
  } catch { notifications.value = [] }
  finally { loading.value = false }
}

async function markRead(n) {
  if (n.isRead) return
  try {
    await notificationApi.markRead(n.id)
    n.isRead = true
  } catch { /* 실패 무시 */ }
}

async function markAllRead() {
  try {
    await notificationApi.markAllRead()
    notifications.value.forEach(n => n.isRead = true)
  } catch { /* 실패 무시 */ }
}

onMounted(loadNotifications)
</script>

<style scoped>
.container { max-width: 480px; padding: 16px; }
.page-header { display: flex; align-items: center; gap: 8px; margin-bottom: 20px; }
.back-btn { font-size: 24px; background: none; border: none; cursor: pointer; color: var(--text); padding: 0 4px; line-height: 1; }
.page-title { font-size: 20px; font-weight: 800; flex: 1; }
.read-all-btn { font-size: 13px; color: var(--primary); background: none; border: none; cursor: pointer; padding: 4px 8px; }
.read-all-btn:hover { text-decoration: underline; }

.state-center { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 60px 0; gap: 12px; }
.state-text { font-size: 14px; color: var(--text-muted); }

.notif-list { display: flex; flex-direction: column; }
.notif-item {
  display: flex; align-items: flex-start; gap: 12px;
  padding: 14px 0; border-bottom: 1px solid var(--border);
  cursor: pointer; transition: var(--transition);
}
.notif-item:last-child { border-bottom: none; }
.notif-item.unread { background: #f8f9ff; margin: 0 -16px; padding: 14px 16px; }

.notif-dot-wrap { flex-shrink: 0; width: 8px; padding-top: 6px; }
.notif-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--primary); }

.notif-content { flex: 1; min-width: 0; }
.notif-type { font-size: 11px; font-weight: 700; color: var(--primary); text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 2px; }
.notif-msg { font-size: 14px; color: var(--text); line-height: 1.5; margin-bottom: 4px; }
.notif-time { font-size: 12px; color: var(--text-muted); }
</style>
