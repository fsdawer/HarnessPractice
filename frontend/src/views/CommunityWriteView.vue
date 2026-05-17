<template>
  <div class="write-page">
    <div class="container">
      <div class="page-header">
        <button class="back-btn" @click="$router.back()">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
        </button>
        <h1 class="page-title">게시글 작성</h1>
      </div>

      <form class="write-form" @submit.prevent="submit">
        <!-- 제목 -->
        <div class="form-group">
          <label class="form-label">제목 <span class="required">*</span></label>
          <input v-model="form.title" class="form-input" placeholder="제목을 입력하세요" required />
        </div>

        <!-- 내용 -->
        <div class="form-group">
          <label class="form-label">내용 <span class="required">*</span></label>
          <textarea v-model="form.content" class="form-textarea" placeholder="내용을 입력하세요" rows="8" required></textarea>
        </div>

        <!-- 가격 -->
        <div class="form-group">
          <label class="form-label">가격 (선택)</label>
          <input v-model.number="form.price" type="number" class="form-input" placeholder="예: 30000" min="0" />
        </div>

        <!-- 위치 -->
        <div class="form-group">
          <label class="form-label">위치</label>
          <div class="district-display">
            <span v-if="form.district">{{ form.district }}</span>
            <span v-else class="district-empty">위치 인증이 필요합니다. <RouterLink to="/community" class="link">인증하러 가기</RouterLink></span>
          </div>
        </div>

        <!-- 내 예약 불러오기 -->
        <div class="form-group">
          <label class="form-label">예약 연결 (선택)</label>
          <button type="button" class="btn btn-outline btn-sm" @click="openReservationModal">
            내 예약 불러오기
          </button>
          <div v-if="selectedReservation" class="reservation-badge">
            <span>{{ selectedReservation.stylistName }} · {{ selectedReservation.serviceName }} · {{ formatDate(selectedReservation.reservedAt) }}</span>
            <button type="button" class="badge-remove" @click="selectedReservation = null; form.reservationId = null">x</button>
          </div>
        </div>

        <button type="submit" class="btn btn-primary btn-full" :disabled="submitting">
          {{ submitting ? '등록 중...' : '게시글 등록' }}
        </button>
      </form>
    </div>

    <!-- 예약 선택 모달 -->
    <div v-if="showReservationModal" class="modal-overlay" @click.self="showReservationModal = false">
      <div class="modal">
        <div class="modal-header">
          <h3 class="modal-title">예약 선택</h3>
          <button class="modal-close" @click="showReservationModal = false">x</button>
        </div>
        <div class="modal-body">
          <div v-if="loadingReservations" class="modal-loading">불러오는 중...</div>
          <div v-else-if="reservations.length === 0" class="modal-empty">예약 내역이 없습니다.</div>
          <div
            v-for="r in reservations"
            :key="r.id"
            class="reservation-item"
            @click="selectReservation(r)"
          >
            <div class="res-stylist">{{ r.stylistName }}</div>
            <div class="res-detail">{{ r.serviceName }} · {{ formatDate(r.reservedAt) }}</div>
            <div class="res-status" :class="r.status.toLowerCase()">{{ statusLabel(r.status) }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { communityApi } from '@/api/community'
import { reservationApi } from '@/api/reservation'
import { useAuthStore } from '@/stores/authStore'

const router = useRouter()
const auth = useAuthStore()

const form = reactive({
  title: '',
  content: '',
  price: null,
  district: auth.user?.verifiedDistrict || '',
  reservationId: null,
})

const submitting = ref(false)
const showReservationModal = ref(false)
const loadingReservations = ref(false)
const reservations = ref([])
const selectedReservation = ref(null)

async function openReservationModal() {
  showReservationModal.value = true
  if (reservations.value.length === 0) {
    loadingReservations.value = true
    try {
      const res = await reservationApi.getMyReservations()
      reservations.value = res.data.map(r => ({
        id: r.id,
        stylistName: r.stylistName,
        serviceName: r.serviceName,
        reservedAt: r.reservedAt,
        status: r.status,
      }))
    } catch (e) {
      console.error(e)
    } finally {
      loadingReservations.value = false
    }
  }
}

function selectReservation(r) {
  selectedReservation.value = r
  form.reservationId = r.id
  showReservationModal.value = false
}

async function submit() {
  if (!form.district) {
    alert('위치 인증이 필요합니다. 커뮤니티 페이지에서 먼저 인증해 주세요.')
    return
  }
  submitting.value = true
  try {
    const payload = {
      title: form.title,
      content: form.content,
      district: form.district,
      price: form.price || null,
      reservationId: form.reservationId,
    }
    const res = await communityApi.createPost(payload)
    router.push(`/community/${res.data.id}`)
  } catch (e) {
    alert('게시글 등록에 실패했습니다.')
  } finally {
    submitting.value = false
  }
}

function formatDate(dateStr) {
  const d = new Date(dateStr)
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`
}

function statusLabel(status) {
  const map = { PENDING: '대기', CONFIRMED: '확정', DONE: '완료', CANCELLED: '취소' }
  return map[status] || status
}
</script>

<style scoped>
.write-page { padding: 24px 0 60px; }
.page-header { display: flex; align-items: center; gap: 12px; margin-bottom: 24px; }
.back-btn { background: none; border: none; cursor: pointer; display: flex; align-items: center; color: var(--text-muted); padding: 4px; }
.back-btn:hover { color: var(--text); }
.page-title { font-size: 20px; font-weight: 700; color: var(--text); }

.write-form { display: flex; flex-direction: column; gap: 20px; max-width: 640px; }
.form-group { display: flex; flex-direction: column; gap: 6px; }
.form-label { font-size: 13px; font-weight: 600; color: var(--text-sub); }
.required { color: var(--danger); }
.form-input, .form-textarea {
  padding: 10px 14px; border: 1px solid var(--border);
  border-radius: var(--radius-sm); font-size: 14px; outline: none;
  transition: var(--transition);
}
.form-input:focus, .form-textarea:focus { border-color: var(--primary); }
.form-textarea { resize: vertical; font-family: inherit; }

.district-display {
  padding: 10px 14px; border: 1px solid var(--border);
  border-radius: var(--radius-sm); font-size: 14px;
  background: var(--bg-surface); color: var(--text-sub);
}
.district-empty { color: var(--text-muted); }
.link { color: var(--primary); text-decoration: underline; }

.reservation-badge {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 6px 12px; background: #f0f4ff;
  border: 1px solid #c7d7ff; border-radius: 20px;
  font-size: 13px; color: var(--text-sub); margin-top: 8px;
}
.badge-remove {
  background: none; border: none; cursor: pointer;
  font-size: 14px; color: var(--text-muted); padding: 0;
  line-height: 1;
}

.btn-full { width: 100%; padding: 13px; font-size: 15px; font-weight: 700; }

/* Modal */
.modal-overlay {
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.5);
  display: flex; align-items: center; justify-content: center;
  z-index: 500;
}
.modal {
  background: #fff; border-radius: var(--radius-lg);
  width: 90%; max-width: 480px; max-height: 70vh;
  display: flex; flex-direction: column; overflow: hidden;
}
.modal-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 20px; border-bottom: 1px solid var(--border);
}
.modal-title { font-size: 16px; font-weight: 700; color: var(--text); }
.modal-close { background: none; border: none; font-size: 18px; cursor: pointer; color: var(--text-muted); }
.modal-body { overflow-y: auto; padding: 8px 0; }
.modal-loading, .modal-empty { padding: 24px; text-align: center; color: var(--text-muted); font-size: 14px; }

.reservation-item {
  padding: 14px 20px; cursor: pointer; transition: var(--transition);
  border-bottom: 1px solid var(--border);
}
.reservation-item:last-child { border-bottom: none; }
.reservation-item:hover { background: var(--bg-surface); }
.res-stylist { font-size: 14px; font-weight: 600; color: var(--text); margin-bottom: 3px; }
.res-detail { font-size: 13px; color: var(--text-muted); }
.res-status { font-size: 12px; margin-top: 4px; }
.res-status.confirmed { color: var(--success); }
.res-status.done { color: var(--primary); }
.res-status.cancelled { color: var(--danger); }
.res-status.pending { color: var(--warning, #f59e0b); }
</style>
