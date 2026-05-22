<template>
  <main class="page">
    <div class="container">
      <div class="page-header">
        <button class="back-btn" @click="$router.back()">‹</button>
        <h1 class="page-title">채팅</h1>
      </div>

      <div v-if="loading" class="state-center"><div class="spinner"></div></div>

      <div v-else-if="rooms.length === 0" class="state-center">
        <p class="state-text">채팅방이 없습니다.</p>
        <RouterLink to="/" class="btn btn-primary btn-sm">미용사 찾기</RouterLink>
      </div>

      <div v-else class="room-list">
        <RouterLink
          v-for="r in rooms"
          :key="r.roomId"
          :to="`/chat/${r.roomId}`"
          class="room-item card"
        >
          <div class="room-avatar">
            <img v-if="r.partnerProfileImg" :src="r.partnerProfileImg" alt="" class="avatar-img" />
            <div v-else class="avatar-initial">{{ (r.partnerName || '?')[0] }}</div>
          </div>
          <div class="room-body">
            <div class="room-top">
              <span class="room-name">{{ r.partnerName }}</span>
              <span v-if="r.lastMessageAt" class="room-time">{{ formatTime(r.lastMessageAt) }}</span>
            </div>
            <p class="room-last-msg">{{ r.lastMessage || '메시지가 없습니다.' }}</p>
          </div>
          <div v-if="r.unreadCount > 0" class="unread-badge">{{ r.unreadCount }}</div>
        </RouterLink>
      </div>
    </div>
  </main>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { chatApi } from '@/api/chat'

const rooms = ref([])
const loading = ref(true)

function formatTime(s) {
  if (!s) return ''
  const d = new Date(s)
  const now = new Date()
  const diff = Math.floor((now - d) / 1000)
  if (diff < 60) return '방금'
  if (diff < 3600) return `${Math.floor(diff / 60)}분 전`
  if (diff < 86400) return `${d.getHours()}:${String(d.getMinutes()).padStart(2,'0')}`
  return `${d.getMonth()+1}/${d.getDate()}`
}

onMounted(async () => {
  try {
    const res = await chatApi.getMyRooms()
    rooms.value = res.data || []
  } catch { rooms.value = [] }
  finally { loading.value = false }
})
</script>

<style scoped>
.container { max-width: 480px; padding: 16px; }
.page-header { display: flex; align-items: center; gap: 8px; margin-bottom: 20px; }
.back-btn { font-size: 24px; background: none; border: none; cursor: pointer; color: var(--text); padding: 0 4px; line-height: 1; }
.page-title { font-size: 20px; font-weight: 800; }

.state-center { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 60px 0; gap: 12px; }
.state-text { font-size: 14px; color: var(--text-muted); }

.room-list { display: flex; flex-direction: column; gap: 8px; }
.room-item {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 16px; text-decoration: none; color: inherit;
  transition: var(--transition);
}
.room-item:hover { background: var(--bg-surface); }

.room-avatar { flex-shrink: 0; }
.avatar-img { width: 46px; height: 46px; border-radius: 50%; object-fit: cover; }
.avatar-initial {
  width: 46px; height: 46px; border-radius: 50%;
  background: var(--primary); color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px; font-weight: 700;
}

.room-body { flex: 1; min-width: 0; }
.room-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.room-name { font-size: 15px; font-weight: 700; color: var(--text); }
.room-time { font-size: 12px; color: var(--text-muted); flex-shrink: 0; }
.room-last-msg { font-size: 13px; color: var(--text-muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.unread-badge {
  flex-shrink: 0;
  min-width: 20px; height: 20px; border-radius: 10px;
  background: var(--primary); color: #fff;
  font-size: 11px; font-weight: 700; line-height: 20px; text-align: center; padding: 0 5px;
}
</style>
