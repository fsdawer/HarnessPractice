<template>
  <main class="page">
    <!-- 리뷰 작성 모달 -->
    <Teleport to="body">
      <div v-if="reviewModal.open" class="modal-backdrop" @click.self="closeReviewModal">
      <div class="modal-box">
        <div class="modal-header">
          <h3 class="modal-title">리뷰 작성</h3>
          <button class="modal-close" @click="closeReviewModal">✕</button>
        </div>
        <div class="modal-body">
          <p class="review-target">{{ reviewModal.serviceName }} · {{ reviewModal.stylistName }}</p>
          <div class="star-row">
            <button
              v-for="i in 5" :key="i"
              class="star-btn" :class="{ active: i <= reviewModal.rating }"
              @click="reviewModal.rating = i"
            >★</button>
          </div>
          <textarea
            v-model="reviewModal.content"
            class="review-textarea"
            placeholder="서비스는 어떠셨나요? 솔직한 후기를 남겨주세요 (10자 이상)"
            rows="4"
          ></textarea>
        </div>
        <div class="modal-footer">
          <button class="btn btn-ghost btn-sm" @click="closeReviewModal">취소</button>
          <button class="btn btn-primary btn-sm" :disabled="reviewSubmitting" @click="submitReview">
            <span v-if="reviewSubmitting" class="spinner" style="width:12px;height:12px;border-width:2px"></span>
            <span v-else>등록</span>
          </button>
        </div>
      </div>
    </div>
    </Teleport>

    <div class="container">
      <div class="page-header">
        <button class="back-btn" @click="$router.back()">‹</button>
        <h1 class="page-title">예약 내역</h1>
      </div>

      <!-- 상태 필터 -->
      <div class="filter-bar">
        <button
          v-for="f in filters" :key="f.value"
          class="filter-btn" :class="{ active: activeFilter === f.value }"
          @click="activeFilter = f.value"
        >{{ f.label }}</button>
      </div>

      <!-- 로딩 -->
      <div v-if="loading" class="state-center"><div class="spinner"></div></div>

      <!-- 빈 상태 -->
      <div v-else-if="filteredReservations.length === 0" class="state-center">
        <p class="state-text">예약 내역이 없습니다.</p>
        <RouterLink to="/" class="btn btn-primary btn-sm">미용사 찾기</RouterLink>
      </div>

      <!-- 목록 -->
      <div v-else class="res-list">
        <div
          v-for="r in filteredReservations" :key="r.id"
          class="res-card card"
          :class="{ expanded: expanded === r.id }"
        >
          <!-- 카드 헤더 (항상 표시) -->
          <div class="res-main" @click="toggle(r.id)">
            <div class="res-top-row">
              <span class="badge" :class="statusBadge(r.status)">{{ statusLabel(r.status) }}</span>
              <span class="res-date">{{ formatDate(r.reservedAt) }}</span>
            </div>
            <p class="res-service">{{ r.serviceName }}</p>
            <div class="res-sub-row">
              <span class="res-stylist">{{ r.stylistName }}</span>
              <span v-if="r.salonName" class="res-salon"> · {{ r.salonName }}</span>
            </div>
            <div class="res-price-row">
              <span class="res-price">{{ r.totalPrice?.toLocaleString() }}원</span>
              <span class="expand-icon">{{ expanded === r.id ? '▲' : '▼' }}</span>
            </div>
          </div>

          <!-- 펼치면 보이는 상세 + 결제 정보 -->
          <div v-if="expanded === r.id" class="res-detail">
            <div class="detail-section">
              <p class="detail-label">예약 정보</p>
              <div class="detail-row">
                <span class="detail-key">예약번호</span>
                <span class="detail-val">#{{ r.id }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-key">서비스</span>
                <span class="detail-val">{{ r.serviceName }} ({{ r.serviceDuration }}분)</span>
              </div>
              <div v-if="r.requestMemo" class="detail-row">
                <span class="detail-key">요청사항</span>
                <span class="detail-val">{{ r.requestMemo }}</span>
              </div>
            </div>

            <!-- 결제 정보 -->
            <div v-if="paymentMap[r.id]" class="detail-section">
              <p class="detail-label">결제 정보</p>
              <div class="detail-row">
                <span class="detail-key">결제 상태</span>
                <span class="badge" :class="payStatusBadge(paymentMap[r.id].status)">
                  {{ payStatusLabel(paymentMap[r.id].status) }}
                </span>
              </div>
              <div class="detail-row">
                <span class="detail-key">결제 수단</span>
                <span class="detail-val">{{ methodLabel(paymentMap[r.id].method) }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-key">결제 금액</span>
                <span class="detail-val pay-amount">{{ paymentMap[r.id].amount?.toLocaleString() }}원</span>
              </div>
              <div v-if="paymentMap[r.id].paidAt" class="detail-row">
                <span class="detail-key">결제 일시</span>
                <span class="detail-val">{{ formatDate(paymentMap[r.id].paidAt) }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-key">주문번호</span>
                <span class="detail-val mono">{{ paymentMap[r.id].orderId }}</span>
              </div>
            </div>
            <div v-else class="detail-section">
              <p class="detail-label">결제 정보</p>
              <p class="no-payment">결제 내역 없음</p>
            </div>

            <!-- 액션 버튼 -->
            <div class="res-actions">
              <RouterLink v-if="r.chatRoomId" :to="`/chat/${r.chatRoomId}`" class="btn btn-ghost btn-sm">채팅</RouterLink>
              <button
                v-if="r.status === 'CONFIRMED'"
                class="btn btn-danger-ghost btn-sm"
                @click.stop="cancelReservation(r.id)"
              >예약 취소</button>
              <RouterLink
                v-if="!r.chatRoomId && r.status !== 'CANCELLED'"
                :to="`/payment/${r.id}`"
                class="btn btn-primary btn-sm"
              >결제하기</RouterLink>
              <button
                v-if="r.status === 'DONE' && !reviewedIds.has(r.id)"
                class="btn btn-primary btn-sm"
                @click.stop="openReviewModal(r)"
              >리뷰 작성</button>
              <span v-if="r.status === 'DONE' && reviewedIds.has(r.id)" class="review-done-label">리뷰 작성 완료</span>
              <RouterLink
                v-if="r.status === 'DONE' || r.status === 'CANCELLED'"
                :to="`/booking/${r.stylistId}`"
                class="btn btn-ghost btn-sm"
              >재예약</RouterLink>
            </div>
          </div>
        </div>

        <div v-if="hasMore" class="load-more-wrap">
          <button class="btn btn-ghost" :disabled="loadingMore" @click="loadMore">
            <span v-if="loadingMore" class="spinner" style="width:14px;height:14px;border-width:2px"></span>
            <span v-else>더 보기</span>
          </button>
        </div>
      </div>
    </div>
  </main>
</template>

<script setup>
import { ref, computed, onMounted, reactive } from 'vue'
import { reservationApi } from '@/api/reservation'
import { paymentApi } from '@/api/payment'
import { reviewApi } from '@/api/review'

const reservations = ref([])
const paymentMap = ref({})   // reservationId → PaymentResponse
const reviewedIds = ref(new Set())
const loading = ref(false)
const loadingMore = ref(false)
const nextCursorId = ref(null)  // 다음 요청 시 lastId로 사용
const hasMore = ref(false)
const expanded = ref(null)
const activeFilter = ref('ALL')

const reviewModal = reactive({
  open: false,
  reservationId: null,
  stylistProfileId: null,
  serviceName: '',
  stylistName: '',
  rating: 5,
  content: '',
})
const reviewSubmitting = ref(false)

function openReviewModal(r) {
  reviewModal.open = true
  reviewModal.reservationId = r.id
  reviewModal.stylistProfileId = r.stylistId  // ReservationResponse.stylistId = stylistProfile.id
  reviewModal.serviceName = r.serviceName
  reviewModal.stylistName = r.stylistName
  reviewModal.rating = 5
  reviewModal.content = ''
}

function closeReviewModal() {
  reviewModal.open = false
}

async function submitReview() {
  if (reviewModal.content.trim().length < 10) {
    alert('리뷰 내용을 10자 이상 입력해주세요.')
    return
  }
  reviewSubmitting.value = true
  try {
    await reviewApi.create({
      stylistProfileId: reviewModal.stylistProfileId,
      reservationId: reviewModal.reservationId,
      rating: reviewModal.rating,
      content: reviewModal.content.trim(),
    })
    reviewedIds.value = new Set([...reviewedIds.value, reviewModal.reservationId])
    closeReviewModal()
  } catch (e) {
    alert(e.response?.data?.message || '리뷰 등록 실패')
  } finally {
    reviewSubmitting.value = false
  }
}

async function loadReviewedIds() {
  try {
    const res = await reviewApi.getMyReviews()
    reviewedIds.value = new Set((res.data || []).map(rv => rv.reservationId).filter(Boolean))
  } catch { /* 실패해도 무시 */ }
}

const filters = [
  { label: '전체', value: 'ALL' },
  { label: '예약확정', value: 'CONFIRMED' },
  { label: '완료', value: 'DONE' },
  { label: '취소', value: 'CANCELLED' },
]

const filteredReservations = computed(() =>
  activeFilter.value === 'ALL'
    ? reservations.value
    : reservations.value.filter(r => r.status === activeFilter.value)
)

function toggle(id) {
  expanded.value = expanded.value === id ? null : id
}

function statusLabel(s) { return { CONFIRMED: '예약확정', DONE: '완료', CANCELLED: '취소됨', PENDING: '대기중' }[s] || s }
function statusBadge(s) { return { CONFIRMED: 'badge-green', DONE: 'badge-gray', CANCELLED: 'badge-red', PENDING: 'badge-orange' }[s] || 'badge-gray' }
function payStatusLabel(s) { return { PAID: '결제완료', PENDING: '대기', REFUNDED: '환불' }[s] || s }
function payStatusBadge(s) { return { PAID: 'badge-green', PENDING: 'badge-orange', REFUNDED: 'badge-blue' }[s] || 'badge-gray' }
function methodLabel(m) { return { TOSS: '토스페이', KAKAO_PAY: '카카오페이', NAVER_PAY: '네이버페이' }[m] || m }
function formatDate(str) {
  if (!str) return ''
  const d = new Date(str)
  return `${d.getFullYear()}.${String(d.getMonth()+1).padStart(2,'0')}.${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`
}

async function loadReservations(cursorId = null, append = false) {
  if (!append) loading.value = true
  else loadingMore.value = true
  try {
    const res = await reservationApi.getMyReservations(cursorId)
    const content = res.data.content || []
    reservations.value = append ? [...reservations.value, ...content] : content
    hasMore.value = res.data.hasMore
    nextCursorId.value = res.data.nextCursorId
  } catch {
    if (!append) reservations.value = []
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

async function loadPayments() {
  try {
    const res = await paymentApi.getMyPayments()
    const map = {}
    for (const p of (res.data || [])) {
      map[p.reservationId] = p
    }
    paymentMap.value = map
  } catch { /* 결제 조회 실패해도 예약 목록은 보여줌 */ }
}

async function loadMore() {
  await loadReservations(nextCursorId.value, true)
}

async function cancelReservation(id) {
  if (!confirm('예약을 취소하시겠습니까?')) return
  try { await reservationApi.cancel(id); await loadReservations() }
  catch (e) { alert(e.response?.data?.message || '취소 실패') }
}

onMounted(() => {
  loadReservations()
  loadPayments()
  loadReviewedIds()
})
</script>

<style scoped>
.container { max-width: 480px; padding: 16px; }

.page-header { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }
.back-btn { font-size: 24px; background: none; border: none; cursor: pointer; color: var(--text); padding: 0 4px; line-height: 1; }
.page-title { font-size: 20px; font-weight: 800; }

.filter-bar { display: flex; gap: 6px; margin-bottom: 14px; overflow-x: auto; padding-bottom: 2px; }
.filter-btn {
  flex-shrink: 0; padding: 6px 14px; border-radius: 20px; border: 1.5px solid var(--border);
  background: var(--bg); color: var(--text-muted); font-size: 13px; font-weight: 500; cursor: pointer;
  transition: var(--transition);
}
.filter-btn.active { background: var(--primary); color: #fff; border-color: var(--primary); }

.res-list { display: flex; flex-direction: column; gap: 10px; }
.res-card { padding: 0; overflow: hidden; cursor: pointer; }
.res-main { padding: 16px; }
.res-top-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.res-date { font-size: 12px; color: var(--text-muted); }
.res-service { font-size: 16px; font-weight: 700; margin-bottom: 4px; }
.res-sub-row { font-size: 13px; color: var(--text-muted); margin-bottom: 8px; }
.res-price-row { display: flex; justify-content: space-between; align-items: center; }
.res-price { font-size: 18px; font-weight: 800; }
.expand-icon { font-size: 11px; color: var(--text-muted); }

.res-detail {
  border-top: 1px solid var(--border);
  padding: 14px 16px;
  background: var(--bg-surface);
  cursor: default;
}
.detail-section { margin-bottom: 14px; }
.detail-section:last-of-type { margin-bottom: 0; }
.detail-label { font-size: 11px; font-weight: 700; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 8px; }
.detail-row { display: flex; align-items: baseline; gap: 8px; margin-bottom: 5px; }
.detail-key { font-size: 12px; color: var(--text-muted); width: 72px; flex-shrink: 0; }
.detail-val { font-size: 13px; color: var(--text); }
.pay-amount { font-weight: 700; color: var(--primary); }
.mono { font-family: 'SF Mono', 'Fira Code', monospace; font-size: 11px; word-break: break-all; }
.no-payment { font-size: 13px; color: var(--text-muted); }

.res-actions { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 12px; }

.load-more-wrap { display: flex; justify-content: center; padding: 12px 0; }
.state-center { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 60px 0; }
.state-text { color: var(--text-muted); font-size: 15px; }

.review-done-label { font-size: 12px; color: var(--text-muted); padding: 5px 10px; }

/* 리뷰 모달 */
.modal-backdrop {
  position: fixed; inset: 0; background: rgba(0,0,0,0.45);
  display: flex; align-items: center; justify-content: center; z-index: 500; padding: 16px;
}
.modal-box {
  width: 100%; max-width: 400px;
  background: #fff; border-radius: var(--radius-lg);
  box-shadow: 0 12px 40px rgba(0,0,0,0.18); overflow: hidden;
}
.modal-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 20px; border-bottom: 1px solid var(--border);
}
.modal-title { font-size: 16px; font-weight: 700; }
.modal-close { background: none; border: none; font-size: 16px; cursor: pointer; color: var(--text-muted); padding: 0; }
.modal-body { padding: 20px; display: flex; flex-direction: column; gap: 14px; }
.review-target { font-size: 13px; color: var(--text-muted); }
.star-row { display: flex; gap: 4px; }
.star-btn { font-size: 28px; background: none; border: none; cursor: pointer; color: var(--border); transition: color 0.1s; padding: 0; }
.star-btn.active { color: var(--gold, #f59e0b); }
.review-textarea {
  width: 100%; padding: 10px 12px; border: 1.5px solid var(--border);
  border-radius: var(--radius-md); font-size: 14px; font-family: var(--font);
  color: var(--text); resize: none; outline: none; transition: var(--transition);
  box-sizing: border-box;
}
.review-textarea:focus { border-color: var(--primary); }
.modal-footer { display: flex; justify-content: flex-end; gap: 8px; padding: 14px 20px; border-top: 1px solid var(--border); }
</style>
