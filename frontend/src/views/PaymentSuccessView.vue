<template>
  <main class="page">
    <div class="container">
      <div class="success-wrap">
        <div class="success-icon">
          <svg viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
            <circle cx="24" cy="24" r="22" stroke="currentColor" stroke-width="2"/>
            <path d="M14 24l7 7 13-14" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <h1 class="success-title">결제 완료</h1>
        <p class="success-sub">예약이 확정되었습니다</p>

        <div class="detail-card">
          <div v-if="loading" class="state-center" style="padding:24px 0">
            <div class="spinner"></div>
          </div>
          <template v-else>
            <div class="detail-row">
              <span class="detail-label">주문번호</span>
              <span class="detail-val mono">{{ orderId }}</span>
            </div>
            <div v-if="payment" class="detail-row">
              <span class="detail-label">결제 금액</span>
              <span class="detail-val">{{ payment.amount?.toLocaleString() }}원</span>
            </div>
            <div v-if="payment" class="detail-row">
              <span class="detail-label">결제 수단</span>
              <span class="detail-val">{{ methodLabel(payment.method) }}</span>
            </div>
            <div v-if="reservation" class="detail-row">
              <span class="detail-label">서비스</span>
              <span class="detail-val">{{ reservation.serviceName }}</span>
            </div>
            <div v-if="reservation" class="detail-row">
              <span class="detail-label">예약 일시</span>
              <span class="detail-val">{{ formatDate(reservation.reservedAt) }}</span>
            </div>
          </template>
        </div>

        <div class="success-actions">
          <RouterLink to="/mypage" class="btn btn-primary">예약 내역 확인</RouterLink>
          <RouterLink to="/" class="btn btn-ghost">홈으로</RouterLink>
        </div>
      </div>
    </div>
  </main>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { paymentApi } from '@/api/payment'
import { reservationApi } from '@/api/reservation'

const route  = useRoute()
const router = useRouter()
const loading    = ref(true)
const payment    = ref(null)
const reservation = ref(null)
const orderId    = ref(route.query.orderId    || '')
const paymentKey = ref(route.query.paymentKey || '')
const amount     = ref(Number(route.query.amount) || 0)

function methodLabel(m) {
  return { TOSS: '토스페이', KAKAO_PAY: '카카오페이', NAVER_PAY: '네이버페이' }[m] || m
}
function formatDate(str) {
  if (!str) return ''
  const d = new Date(str)
  return `${d.getFullYear()}.${String(d.getMonth()+1).padStart(2,'0')}.${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`
}

onMounted(async () => {
  if (!orderId.value) { loading.value = false; return }
  try {
    // Toss 리다이렉트: paymentKey와 amount가 있으면 백엔드 승인 처리
    if (paymentKey.value && amount.value) {
      try {
        // 쿠폰 할인은 prepare() 단계에서 이미 적용됨.
        // confirm은 Toss 결제 완료를 백엔드에서 검증하고 예약을 CONFIRMED로 전환하는 역할.
        await paymentApi.confirm({
          paymentKey: paymentKey.value,
          orderId: orderId.value,
          amount: amount.value,
        })
      } catch (e) {
        const msg = e.response?.data?.message || ''
        // "이미 결제 완료" = 새로고침 재시도 → 그냥 결제 내역 조회로 이어감
        if (!msg.includes('이미 결제')) {
          router.replace({
            name: 'PaymentFail',
            query: { orderId: orderId.value, message: msg || '결제 승인에 실패했습니다.' },
          })
          return
        }
      }
    }
    const res = await paymentApi.getByOrderId(orderId.value)
    payment.value = res.data
    if (payment.value?.reservationId) {
      const rRes = await reservationApi.getById(payment.value.reservationId)
      reservation.value = rRes.data
    }
  } catch {}
  finally { loading.value = false }
})
</script>

<style scoped>
.success-wrap {
  max-width: 480px;
  margin: 0 auto;
  padding: 48px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.success-icon {
  width: 72px; height: 72px;
  color: var(--success);
  animation: pop 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}
@keyframes pop {
  from { transform: scale(0); opacity: 0; }
  to   { transform: scale(1); opacity: 1; }
}

.success-title { font-size: 28px; font-weight: 800; letter-spacing: -0.03em; text-align: center; }
.success-sub { font-size: 15px; color: var(--text-muted); text-align: center; margin-top: -8px; }

.detail-card {
  width: 100%;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 20px 24px;
  box-shadow: var(--shadow);
}
.detail-row {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 0; border-bottom: 1px solid var(--border);
}
.detail-row:last-child { border-bottom: none; }
.detail-label { font-size: 13px; color: var(--text-muted); font-weight: 500; }
.detail-val { font-size: 14px; font-weight: 600; color: var(--text); }
.detail-val.mono { font-family: 'SF Mono', 'Fira Code', monospace; font-size: 12px; color: var(--text-sub); }

.success-actions { display: flex; gap: 10px; flex-wrap: wrap; justify-content: center; }
</style>
