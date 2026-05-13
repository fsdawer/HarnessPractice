<template>
  <main class="page">
    <div class="container">
      <div class="page-header">
        <button class="back-btn" @click="$router.back()">‹</button>
        <h1 class="page-title">쿠폰함</h1>
      </div>

      <div v-if="loading" class="state-center"><div class="spinner"></div></div>

      <div v-else-if="coupons.length === 0" class="state-center">
        <p class="state-text">보유한 쿠폰이 없습니다.</p>
      </div>

      <div v-else class="coupon-list">
        <div v-for="c in coupons" :key="c.userCouponId" class="coupon-card card">
          <div class="coupon-top">
            <span class="coupon-name">{{ c.name }}</span>
            <span class="coupon-discount">{{ c.discountRate }}% 할인</span>
          </div>
          <div class="coupon-info">
            <span v-if="c.minPrice">{{ c.minPrice.toLocaleString() }}원 이상 구매 시</span>
            <span v-if="c.maxDiscount"> · 최대 {{ c.maxDiscount.toLocaleString() }}원</span>
          </div>
          <div class="coupon-bottom">
            <span class="coupon-expire">{{ c.expiredAt ? formatDate(c.expiredAt) + ' 까지' : '기간 무제한' }}</span>
            <span class="badge-valid">사용 가능</span>
          </div>
        </div>
      </div>
    </div>
  </main>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { couponApi } from '@/api/coupon'

const coupons = ref([])
const loading = ref(false)

function formatDate(str) {
  if (!str) return ''
  const d = new Date(str)
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`
}

async function load() {
  loading.value = true
  try {
    coupons.value = (await couponApi.getMyCoupons()).data || []
  } catch {
    coupons.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.container { max-width: 480px; padding: 16px; }
.page-header { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }
.back-btn { font-size: 24px; background: none; border: none; cursor: pointer; color: var(--text); padding: 0 4px; line-height: 1; }
.page-title { font-size: 20px; font-weight: 800; }

.coupon-list { display: flex; flex-direction: column; gap: 10px; }

.coupon-card { padding: 16px; }

.coupon-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.coupon-name { font-size: 15px; font-weight: 700; }
.coupon-discount { font-size: 18px; font-weight: 800; color: var(--primary); }

.coupon-info { font-size: 12px; color: var(--text-muted); margin-bottom: 10px; }

.coupon-bottom { display: flex; justify-content: space-between; align-items: center; }
.coupon-expire { font-size: 12px; color: var(--text-muted); }

.badge-valid { font-size: 11px; font-weight: 700; color: var(--primary); background: var(--primary-light, #ede9fe); padding: 3px 8px; border-radius: 20px; }

.state-center { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 60px 0; }
.state-text { color: var(--text-muted); font-size: 15px; }
</style>
