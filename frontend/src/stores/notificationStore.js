import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useNotificationStore = defineStore('notification', () => {
  const notifications = ref([])
  const connected = ref(false)
  let eventSource = null
  const seen = new Set()  // 세션 내 수신한 dedupKey 집합 (O(1) 조회)

  const unreadCount = computed(() => notifications.value.filter(n => !n.read).length)

  function addNotification(msg) {
    // 서버가 보낸 dedupKey 우선, 없으면 클라이언트에서 구성
    const dedupKey = msg.dedupKey ?? `${msg.type}_${msg.reservationId ?? msg.reservedAt ?? ''}`
    if (dedupKey && seen.has(dedupKey)) return
    if (dedupKey) seen.add(dedupKey)

    notifications.value.unshift({
      id: `${Date.now()}-${Math.random()}`,
      dedupKey,
      type: msg.type,
      reservationId: msg.reservationId,
      message: msg.message,
      detail: msg.type === 'RESERVATION_CREATED'
        ? `${msg.clientName} · ${formatDateTime(msg.reservedAt)}`
        : msg.type === 'RESERVATION_CANCELLED'
        ? `${msg.clientName} · ${formatDateTime(msg.reservedAt)}`
        : msg.type === 'WAITING_AVAILABLE'
        ? `지금 바로 예약하세요 · ${formatDateTime(msg.reservedAt)}`
        : msg.type === 'RESERVATION_REMINDER_1D'
        ? `내일 예약 · ${formatDateTime(msg.reservedAt)}`
        : msg.type === 'RESERVATION_REMINDER_1H'
        ? `1시간 후 예약 · ${formatDateTime(msg.reservedAt)}`
        : msg.reservedAt,
      read: false,
      createdAt: new Date().toISOString(),
    })
    if (notifications.value.length > 50) notifications.value.pop()
  }

  function formatDateTime(str) {
    if (!str) return ''
    try {
      const d = new Date(str)
      return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
    } catch { return str }
  }

  function markAllRead() {
    notifications.value.forEach(n => { n.read = true })
  }

  function markRead(id) {
    const n = notifications.value.find(n => n.id === id)
    if (n) n.read = true
  }

  function clearAll() {
    notifications.value = []
    seen.clear()
  }

  let retryTimer = null
  let currentUserId = null
  let currentToken = null

  function connect(userId, token) {
    if (eventSource) return
    currentUserId = userId
    currentToken  = token
    _open()
  }

  function _open() {
    const url = `http://localhost:8080/api/notifications/stream?token=${encodeURIComponent(currentToken)}`
    eventSource = new EventSource(url)

    eventSource.addEventListener('notification', (e) => {
      try {
        addNotification(JSON.parse(e.data))
      } catch (err) {
        console.error('알림 파싱 오류', err)
      }
    })

    eventSource.onopen = () => {
      connected.value = true
      if (retryTimer) { clearTimeout(retryTimer); retryTimer = null }
    }

    // EventSource가 CLOSED 상태(readyState===2)면 자동 retry를 포기한 것 → 직접 재연결
    // CONNECTING(0) / OPEN(1) 상태면 브라우저가 알아서 재시도하므로 내버려 둠
    eventSource.onerror = () => {
      connected.value = false
      if (eventSource?.readyState === EventSource.CLOSED) {
        eventSource = null
        retryTimer = setTimeout(_open, 5000)
      }
    }
  }

  function disconnect() {
    if (retryTimer) { clearTimeout(retryTimer); retryTimer = null }
    eventSource?.close()
    eventSource = null
    connected.value = false
  }

  return { notifications, unreadCount, connected, connect, disconnect, markAllRead, markRead, clearAll }
})
