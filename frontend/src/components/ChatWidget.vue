<template>
  <div class="chat-widget">
    <Transition name="popup">
      <div v-if="isOpen" class="chat-popup">
        <div class="popup-header">
          <span class="popup-title">채팅 목록</span>
          <button class="popup-close" @click="isOpen = false">✕</button>
        </div>

        <div v-if="loading" class="popup-state">
          <div class="spinner" style="width:28px;height:28px;border-width:2px;"></div>
        </div>
        <div v-else-if="rooms.length === 0" class="popup-state">
          채팅방이 없습니다.
        </div>
        <ul v-else class="room-list">
          <li
            v-for="room in rooms"
            :key="room.roomId"
            class="room-item"
            :class="{ unread: room.unreadCount > 0 }"
            @click="goToRoom(room)"
          >
            <div class="room-avatar">{{ getPartnerInitial(room) }}</div>
            <div class="room-info">
              <div class="room-title-row">
                <span class="room-name">{{ getPartnerName(room) }}</span>
                <span class="room-time">{{ formatTime(room.lastMessageAt) }}</span>
              </div>
              <p class="room-salon" v-if="getSalonName(room)">{{ getSalonName(room) }}</p>
              <p class="room-preview" :class="{ 'room-preview--unread': room.unreadCount > 0 }">{{ truncate(room.lastMessageContent) }}</p>
            </div>
            <span v-if="room.unreadCount > 0" class="unread-badge">{{ room.unreadCount > 99 ? '99+' : room.unreadCount }}</span>
          </li>
        </ul>
      </div>
    </Transition>

    <button class="chat-fab" @click="toggleChat" :title="isOpen ? '닫기' : '채팅 목록'">
      <span v-if="!isOpen && totalUnread > 0" class="fab-badge">{{ totalUnread > 9 ? '9+' : totalUnread }}</span>
      <svg v-if="!isOpen" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
      </svg>
      <svg v-else xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
        <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
      </svg>
    </button>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { chatApi } from '@/api/chat'
import { useAuthStore } from '@/stores/authStore'

const router = useRouter()
const auth   = useAuthStore()

const isOpen  = ref(false)
const rooms   = ref([])
const loading = ref(false)

const totalUnread = computed(() => rooms.value.reduce((sum, r) => sum + (r.unreadCount || 0), 0))

async function toggleChat() {
  isOpen.value = !isOpen.value
  if (isOpen.value) await fetchRooms()
}

async function fetchRooms() {
  loading.value = true
  try {
    const res = await chatApi.getMyRooms()
    rooms.value = res.data
  } catch (e) {
    console.error('채팅방 목록 조회 실패', e)
  } finally {
    loading.value = false
  }
}

// 내가 고객이면 상대방은 미용사, 내가 미용사면 상대방은 고객
function getPartnerName(room) {
  return auth.user?.id === room.userId ? room.stylistUserName : room.userName
}

function getPartnerInitial(room) {
  const name = getPartnerName(room)
  return name ? name.charAt(0) : '?'
}

// 내가 고객일 때만 미용실 이름 표시 (미용사 입장에서는 본인 미용실이므로 생략)
function getSalonName(room) {
  return auth.user?.id === room.userId ? (room.salonName || '') : ''
}

// 10자 초과 시 말줄임
function truncate(content) {
  if (!content) return '대화를 시작해보세요'
  return content.length > 10 ? content.slice(0, 10) + '...' : content
}

function formatTime(dateStr) {
  if (!dateStr) return ''
  const d    = new Date(dateStr)
  const diff = Math.floor((Date.now() - d) / 60000)
  if (diff < 1)    return '방금'
  if (diff < 60)   return `${diff}분 전`
  if (diff < 1440) return `${Math.floor(diff / 60)}시간 전`
  return `${d.getMonth() + 1}/${d.getDate()}`
}

function goToRoom(room) {
  isOpen.value = false
  router.push(`/chat/${room.roomId}`)
}
</script>

<style scoped>
.chat-widget {
  position: fixed;
  bottom: 28px;
  right: 28px;
  z-index: 9000;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
}

.chat-fab {
  position: relative;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: var(--primary);
  color: #fff;
  border: none;
  cursor: pointer;
  box-shadow: 0 2px 10px rgba(0,0,0,0.18);
  transition: background 0.15s;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.chat-fab:hover {
  background: var(--primary-hover);
}

.chat-popup {
  width: 320px;
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  border: 1px solid var(--border);
  overflow: hidden;
}

.popup-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 15px 18px;
  border-bottom: 1px solid var(--border);
}
.popup-title { font-size: 15px; font-weight: 700; color: var(--text); }
.popup-close {
  background: none;
  border: none;
  font-size: 13px;
  color: var(--text-muted);
  cursor: pointer;
  padding: 4px 6px;
  border-radius: var(--radius-sm);
  transition: var(--transition);
}
.popup-close:hover { color: var(--text); background: var(--bg); }

.popup-state {
  padding: 36px 20px;
  text-align: center;
  color: var(--text-muted);
  font-size: 14px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.room-list {
  list-style: none;
  max-height: 420px;
  overflow-y: auto;
}
.room-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 13px 18px;
  cursor: pointer;
  transition: var(--transition);
  border-bottom: 1px solid var(--border);
}
.room-item:last-child { border-bottom: none; }
.room-item:hover { background: var(--bg); }

.room-avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: var(--primary-light);
  color: var(--primary);
  font-size: 17px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 2px;
}

.room-info { flex: 1; min-width: 0; }

.room-title-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 8px;
}
.room-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.room-time {
  font-size: 11px;
  color: var(--text-muted);
  flex-shrink: 0;
}
.room-salon {
  font-size: 12px;
  color: var(--primary);
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.room-preview {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.room-preview--unread { color: var(--text-sub); font-weight: 600; }
.room-item.unread { background: #f5f8ff; }
.room-item.unread:hover { background: #eef3ff; }

.unread-badge {
  flex-shrink: 0;
  min-width: 20px; height: 20px; border-radius: 10px;
  background: var(--danger); color: #fff;
  font-size: 11px; font-weight: 700; line-height: 20px;
  text-align: center; padding: 0 5px;
  align-self: center;
}

.fab-badge {
  position: absolute; top: -4px; right: -4px;
  min-width: 18px; height: 18px; border-radius: 9px;
  background: var(--danger); color: #fff;
  font-size: 10px; font-weight: 700; line-height: 18px;
  text-align: center; padding: 0 3px;
  border: 2px solid #fff;
  pointer-events: none;
}

/* Popup transition */
.popup-enter-active, .popup-leave-active { transition: all 0.2s ease; }
.popup-enter-from, .popup-leave-to { opacity: 0; transform: translateY(10px) scale(0.97); }
</style>
