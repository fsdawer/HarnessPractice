<template>
  <main class="page">
    <div class="container">
      <div class="page-header">
        <div>
          <h1 class="section-title">대시보드</h1>
          <p class="section-subtitle">예약, 매출, 리뷰 현황을 한눈에 확인하세요</p>
        </div>
        <div class="dash-nav">
          <RouterLink to="/stylist/manage" class="btn btn-ghost btn-sm">프로필 관리</RouterLink>
          <RouterLink to="/stylist/reservations" class="btn btn-ghost btn-sm">예약 관리 →</RouterLink>
        </div>
      </div>

      <div v-if="loading" class="state-center"><div class="spinner"></div></div>
      <p v-else-if="error" class="msg-error">{{ error }}</p>

      <template v-else-if="data">
        <!-- 예약 현황 -->
        <h2 class="dash-group-title">예약 현황</h2>
        <div class="stat-row">
          <div class="stat-card">
            <p class="stat-label">오늘 예약</p>
            <p class="stat-val">{{ data.reservation.todayCount }}</p>
          </div>
          <div class="stat-card">
            <p class="stat-label">이번 달 예약</p>
            <p class="stat-val">{{ data.reservation.monthCount }}</p>
          </div>
          <div class="stat-card">
            <p class="stat-label">대기 중 (PENDING)</p>
            <p class="stat-val warn">{{ data.reservation.pendingCount }}</p>
          </div>
        </div>

        <!-- 매출 현황 -->
        <h2 class="dash-group-title">매출 현황 (이번 달)</h2>
        <div class="stat-row">
          <div class="stat-card">
            <p class="stat-label">총 매출 (PAID)</p>
            <p class="stat-val success">{{ formatPrice(data.sales.monthRevenue) }}</p>
          </div>
          <div class="stat-card">
            <p class="stat-label">환불액 (REFUNDED)</p>
            <p class="stat-val muted">{{ formatPrice(data.sales.monthRefund) }}</p>
          </div>
        </div>

        <!-- 리뷰 현황 -->
        <h2 class="dash-group-title">리뷰 현황</h2>
        <div class="stat-row">
          <div class="stat-card">
            <p class="stat-label">총 리뷰</p>
            <p class="stat-val">{{ data.review.totalCount }}</p>
          </div>
          <div class="stat-card">
            <p class="stat-label">평균 평점</p>
            <p class="stat-val">{{ formatRating(data.review.avgRating) }}</p>
          </div>
        </div>

        <!-- 최근 예약 -->
        <h2 class="dash-group-title">최근 예약 5건</h2>
        <div v-if="data.recentReservations.length === 0" class="state-center">
          <p class="state-text">아직 예약이 없습니다.</p>
        </div>
        <table v-else class="dash-table">
          <thead>
            <tr>
              <th>예약자</th>
              <th>서비스</th>
              <th>일시</th>
              <th>상태</th>
              <th>금액</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in data.recentReservations" :key="r.id">
              <td>{{ r.customerName }}</td>
              <td>{{ r.serviceName }}</td>
              <td>{{ formatDate(r.reservedAt) }}</td>
              <td><span class="badge" :class="statusClass(r.status)">{{ statusLabel(r.status) }}</span></td>
              <td>{{ formatPrice(r.totalPrice) }}</td>
            </tr>
          </tbody>
        </table>
      </template>
    </div>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { stylistApi } from '@/api/stylist'

const data = ref(null)
const loading = ref(true)
const error = ref('')

const STATUS_LABEL = {
  PENDING: '대기',
  CONFIRMED: '확정',
  DONE: '완료',
  CANCELLED: '취소',
}

const STATUS_CLASS = {
  PENDING: 'badge-warn',
  CONFIRMED: 'badge-success',
  DONE: 'badge-muted',
  CANCELLED: 'badge-danger',
}

function statusLabel(s) {
  return STATUS_LABEL[s] || s
}
function statusClass(s) {
  return STATUS_CLASS[s] || ''
}

function formatPrice(v) {
  if (v == null) return '0원'
  return Number(v).toLocaleString() + '원'
}

function formatRating(v) {
  if (v == null) return '-'
  return Number(v).toFixed(1)
}

function formatDate(s) {
  if (!s) return ''
  const d = new Date(s)
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mi = String(d.getMinutes()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd} ${hh}:${mi}`
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await stylistApi.getDashboard()
    data.value = res.data
  } catch (e) {
    error.value = e?.response?.data?.message || '대시보드를 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.dash-nav {
  display: flex;
  gap: 8px;
}
.dash-group-title {
  font-size: 16px;
  font-weight: 600;
  margin: 24px 0 12px;
  color: var(--text-primary, #111);
}
.stat-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 8px;
}
.stat-card {
  background: #fff;
  border: 1px solid var(--border, #e5e7eb);
  border-radius: 12px;
  padding: 16px 20px;
}
.stat-label {
  font-size: 13px;
  color: var(--text-secondary, #6b7280);
  margin: 0 0 6px;
}
.stat-val {
  font-size: 24px;
  font-weight: 700;
  margin: 0;
}
.stat-val.success {
  color: var(--success, #16a34a);
}
.stat-val.warn {
  color: var(--accent, #f59e0b);
}
.stat-val.muted {
  color: var(--text-secondary, #6b7280);
}
.dash-table {
  width: 100%;
  border-collapse: collapse;
  background: #fff;
  border: 1px solid var(--border, #e5e7eb);
  border-radius: 12px;
  overflow: hidden;
}
.dash-table th,
.dash-table td {
  padding: 12px 16px;
  text-align: left;
  font-size: 14px;
  border-bottom: 1px solid var(--border, #e5e7eb);
}
.dash-table thead th {
  background: #f9fafb;
  font-weight: 600;
  color: var(--text-secondary, #6b7280);
}
.dash-table tbody tr:last-child td {
  border-bottom: none;
}
.badge {
  display: inline-block;
  padding: 2px 8px;
  font-size: 12px;
  border-radius: 999px;
  font-weight: 500;
}
.badge-warn {
  background: #fef3c7;
  color: #92400e;
}
.badge-success {
  background: #dcfce7;
  color: #166534;
}
.badge-muted {
  background: #f3f4f6;
  color: #374151;
}
.badge-danger {
  background: #fee2e2;
  color: #991b1b;
}
.state-center {
  display: flex;
  justify-content: center;
  padding: 32px;
}
.msg-error {
  color: #991b1b;
  margin-top: 12px;
}
</style>
