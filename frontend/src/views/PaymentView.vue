<template>
  <main class="page">
    <div class="container">
      <button class="back-link" @click="router.push('/mypage')">← 뒤로가기</button>

      <div v-if="loading" class="state-center">
        <div class="spinner"></div>
        <p class="state-text">결제 정보를 불러오는 중...</p>
      </div>

      <div v-else class="pay-layout">
        <!-- 좌측: 예약 내역 -->
        <div class="pay-main">
          <h1 class="pay-title">결제</h1>

          <div class="card info-card">
            <h2 class="card-section-title">예약 내역</h2>
            <div class="res-summary">
              <img :src="`https://i.pravatar.cc/64?u=${reservation.stylistId}`" class="res-avatar" />
              <div class="res-detail">
                <p class="res-stylist">{{ reservation.stylistName }}</p>
                <p class="res-salon">{{ reservation.salonName }}</p>
                <div class="res-meta-row">
                  <span class="res-meta-item">{{ formatDate(reservation.reservedAt) }}</span>
                  <span class="meta-dot">·</span>
                  <span class="res-meta-item">{{ reservation.serviceName }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 쿠폰 선택 -->
          <div class="card coupon-card">
            <h2 class="card-section-title">쿠폰</h2>
            <div v-if="coupons.length === 0" class="coupon-empty">사용 가능한 쿠폰이 없습니다</div>
            <div v-else class="coupon-list">
              <label
                v-for="uc in coupons"
                :key="uc.userCouponId"
                class="coupon-item"
                :class="{ selected: selectedCouponId === uc.userCouponId }"
              >
                <input
                  type="radio"
                  :value="uc.userCouponId"
                  v-model="selectedCouponId"
                  class="coupon-radio"
                />
                <div class="coupon-info">
                  <span class="coupon-name">{{ uc.name }}</span>
                  <span class="coupon-rate">{{ uc.discountRate }}% 할인</span>
                  <span class="coupon-cond">최소 {{ uc.minPrice?.toLocaleString() }}원 이상 · 최대 {{ uc.maxDiscount?.toLocaleString() ?? '무제한' }}원</span>
                  <span class="coupon-exp">{{ formatExpiry(uc.expiredAt) }} 만료</span>
                </div>
              </label>
              <label class="coupon-item" :class="{ selected: selectedCouponId === null }">
                <input type="radio" :value="null" v-model="selectedCouponId" class="coupon-radio" />
                <div class="coupon-info">
                  <span class="coupon-name">쿠폰 사용 안 함</span>
                </div>
              </label>
            </div>
          </div>
        </div>

        <!-- 우측: 결제 요약 -->
        <aside class="pay-sidebar">
          <div class="card summary-card">
            <h2 class="card-section-title">결제 요약</h2>
            <div class="summary-rows">
              <div class="summary-row">
                <span>서비스</span>
                <span>{{ reservation.serviceName }}</span>
              </div>
              <div class="summary-row">
                <span>소요 시간</span>
                <span>{{ reservation.serviceDuration }}분</span>
              </div>
              <div class="summary-row">
                <span>정가</span>
                <span>{{ reservation.totalPrice?.toLocaleString() }}원</span>
              </div>
              <div v-if="discountAmount > 0" class="summary-row discount-row">
                <span>쿠폰 할인</span>
                <span class="discount-val">-{{ discountAmount.toLocaleString() }}원</span>
              </div>
              <div class="summary-divider"></div>
              <div class="summary-row total">
                <span>총 결제 금액</span>
                <span class="total-price">{{ finalPrice.toLocaleString() }}원</span>
              </div>
            </div>

            <p v-if="payError" class="err-msg">{{ payError }}</p>

            <button
              class="btn btn-primary btn-full pay-btn"
              :disabled="paying"
              @click="handlePay"
            >
              <span v-if="paying" class="spinner" style="width:16px;height:16px;border-width:2px"></span>
              <span v-else>{{ finalPrice.toLocaleString() }}원 결제하기</span>
            </button>
            <p class="pay-note">결제 후 예약이 확정됩니다</p>
          </div>
        </aside>
      </div>
    </div>
  </main>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { reservationApi } from '@/api/reservation'
import { paymentApi } from '@/api/payment'
import { couponApi } from '@/api/coupon'

const route  = useRoute()
const router = useRouter()

const reservation      = ref({})
const coupons          = ref([])
const selectedCouponId = ref(null)
const loading          = ref(true)
const paying           = ref(false)
const payError         = ref('')

const discountAmount = computed(() => {
  if (!selectedCouponId.value) return 0
  const uc = coupons.value.find(c => c.userCouponId === selectedCouponId.value)
  if (!uc || !reservation.value.totalPrice) return 0
  const orig = reservation.value.totalPrice
  let disc = Math.floor(orig * uc.discountRate / 100)
  if (uc.maxDiscount != null) disc = Math.min(disc, uc.maxDiscount)
  return disc
})

const finalPrice = computed(() =>
  Math.max(0, (reservation.value.totalPrice || 0) - discountAmount.value)
)

function formatDate(str) {
  if (!str) return ''
  const d = new Date(str)
  return `${d.getFullYear()}.${String(d.getMonth()+1).padStart(2,'0')}.${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`
}

function formatExpiry(str) {
  if (!str) return ''
  const d = new Date(str)
  return `${d.getFullYear()}.${String(d.getMonth()+1).padStart(2,'0')}.${String(d.getDate()).padStart(2,'0')}`
}

async function handlePay() {
  paying.value = true
  payError.value = ''
  try {
    console.log('[Pay] ① selectedCouponId:', selectedCouponId.value)
    const prepRes = await paymentApi.prepare({
      reservationId: reservation.value.id,
      method: 'TOSS',
      userCouponId: selectedCouponId.value ?? null,
    })
    console.log('[Pay] ② prepRes.data:', prepRes.data)
    const { orderId, amount } = prepRes.data
    console.log('[Pay] ③ Toss에 전달 — orderId:', orderId, 'amount:', amount)

    const tossPayments = window.TossPayments(import.meta.env.VITE_TOSS_CLIENT_KEY)
    const payment = tossPayments.payment({ customerKey: window.TossPayments.ANONYMOUS })
    await payment.requestPayment({
      method: 'CARD',
      amount: { currency: 'KRW', value: amount },
      orderId,
      orderName: reservation.value.serviceName,
      successUrl: `${window.location.origin}/payment/success`,
      failUrl:    `${window.location.origin}/payment/fail`,
    })
  } catch (e) {
    if (e?.code === 'PAY_PROCESS_CANCELED' || e?.code === 'USER_CANCEL') {
      paying.value = false
      return
    }
    payError.value = e.response?.data?.message || e.message || '결제 처리 중 오류가 발생했습니다.'
    paying.value = false
  }
}

onMounted(async () => {
  try {
    const [resRes, couponRes] = await Promise.all([
      reservationApi.getById(route.params.reservationId),
      couponApi.getMyCoupons().catch(() => ({ data: [] })),
    ])
    reservation.value = resRes.data
    coupons.value = couponRes.data || []
  } catch {
    router.push('/')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.back-link {
  display: inline-block; margin-bottom: 20px;
  font-size: 13px; color: var(--text-muted); background: none; border: none; cursor: pointer; padding: 0;
}
.back-link:hover { color: var(--text); }

.pay-title { font-size: 22px; font-weight: 800; letter-spacing: -0.02em; margin-bottom: 20px; }
.pay-layout { display: grid; grid-template-columns: 1fr 300px; gap: 20px; align-items: start; }
.pay-main { display: flex; flex-direction: column; gap: 16px; }

.card-section-title { font-size: 12px; font-weight: 700; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.06em; margin-bottom: 14px; }

.res-summary { display: flex; gap: 14px; align-items: center; }
.res-avatar { width: 52px; height: 52px; border-radius: var(--radius-md); object-fit: cover; flex-shrink: 0; border: 1px solid var(--border); }
.res-stylist { font-size: 15px; font-weight: 700; }
.res-salon { font-size: 13px; color: var(--text-muted); margin: 2px 0 6px; }
.res-meta-row { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--text-sub); }
.meta-dot { color: var(--border-strong); }

/* 쿠폰 */
.coupon-empty { font-size: 13px; color: var(--text-muted); }
.coupon-list { display: flex; flex-direction: column; gap: 8px; }
.coupon-item {
  display: flex; align-items: flex-start; gap: 10px;
  padding: 12px 14px; border: 1.5px solid var(--border);
  border-radius: var(--radius-sm); cursor: pointer; transition: border-color 0.15s;
}
.coupon-item.selected { border-color: var(--text); background: var(--bg-surface); }
.coupon-radio { margin-top: 2px; accent-color: var(--text); }
.coupon-info { display: flex; flex-direction: column; gap: 2px; }
.coupon-name { font-size: 14px; font-weight: 600; color: var(--text); }
.coupon-rate { font-size: 13px; font-weight: 700; color: var(--text); }
.coupon-cond { font-size: 12px; color: var(--text-muted); }
.coupon-exp { font-size: 11px; color: var(--text-muted); }

/* 결제 요약 */
.summary-rows { display: flex; flex-direction: column; gap: 10px; margin-bottom: 20px; }
.summary-row { display: flex; justify-content: space-between; font-size: 14px; color: var(--text-sub); }
.discount-row { color: var(--text); }
.discount-val { color: #e53e3e; font-weight: 600; }
.summary-divider { height: 1px; background: var(--border); margin: 4px 0; }
.summary-row.total { font-size: 15px; font-weight: 700; color: var(--text); margin-top: 4px; }
.total-price { font-size: 20px; font-weight: 800; }

.err-msg { color: var(--danger); font-size: 13px; margin-bottom: 12px; padding: 10px 14px; background: var(--danger-light); border-radius: var(--radius-sm); }
.pay-btn { margin-bottom: 10px; padding: 14px; font-size: 16px; }
.pay-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.pay-note { text-align: center; font-size: 12px; color: var(--text-muted); }

.pay-sidebar { position: sticky; top: 76px; }

@media (max-width: 768px) {
  .pay-layout { grid-template-columns: 1fr; }
  .pay-sidebar { position: static; }
  .pay-title { font-size: 18px; }
}
</style>
