<template>
  <main class="page">
    <div v-if="loading" class="state-center" style="min-height:50vh">
      <div class="spinner"></div>
    </div>

    <div v-else-if="!stylist" class="state-center">
      <p class="state-text">스타일리스트를 찾을 수 없습니다.</p>
    </div>

    <template v-else>
      <!-- 프로필 헤더 -->
      <section class="profile-hero">
        <div class="container">
          <button class="back-link" @click="$router.back()">← 뒤로가기</button>
          <div class="profile-layout">
            <div class="profile-img-wrap">
              <img :src="stylist.profileImg || `https://i.pravatar.cc/160?u=${stylist.id}`" :alt="stylist.name" class="profile-img" />
            </div>
            <div class="profile-info">
              <div class="profile-badges">
                <span class="badge badge-accent">경력 {{ stylist.experience }}년</span>
                <span v-if="stylist.rating >= 4.5" class="badge badge-gold">Top Rated</span>
              </div>
              <h1 class="profile-name">{{ stylist.name }}</h1>
              <p class="profile-salon">{{ stylist.salonName }}</p>
              <p class="profile-location">{{ stylist.location }}</p>
              <div class="profile-rating">
                <span class="stars">★★★★★</span>
                <span class="rating-num">{{ stylist.rating?.toFixed(1) }}</span>
                <span class="rating-cnt">리뷰 {{ reviews.length }}개</span>
              </div>
              <p class="profile-bio">{{ stylist.bio }}</p>
              <div class="profile-actions">
                <RouterLink :to="`/booking/${stylist.id}`" class="btn btn-primary btn-lg">예약하기</RouterLink>
                <button class="btn btn-ghost" @click="toggleFavorite">
                  {{ isFavorited ? '찜 취소' : '찜하기' }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </section>

      <div class="container detail-body">
        <!-- 오늘 빈자리 -->
        <section class="available-section card" v-if="availableSlots.length > 0 || slotsLoading">
          <div class="avail-header">
            <h2 class="card-title">오늘 빈자리</h2>
            <span class="avail-date">{{ todayLabel }}</span>
          </div>
          <div v-if="slotsLoading" class="slots-loading">
            <div class="spinner" style="width:20px;height:20px;border-width:2px"></div>
          </div>
          <div v-else-if="availableSlots.length === 0" class="slots-empty">오늘 예약 가능한 시간이 없습니다.</div>
          <div v-else class="slots-grid">
            <span
              v-for="slot in availableSlots"
              :key="slot"
              class="slot-chip"
              @click="goBookWithSlot(slot)"
            >{{ slot }}</span>
          </div>
        </section>

        <div class="detail-grid">
          <!-- 좌측 콘텐츠 -->
          <div class="detail-main">
            <!-- 서비스 -->
            <section class="card">
              <h2 class="card-title">서비스 메뉴</h2>
              <div class="service-tabs">
                <button
                  v-for="cat in serviceCategories"
                  :key="cat"
                  class="svc-tab"
                  :class="{ active: activeCategory === cat }"
                  @click="activeCategory = cat"
                >{{ cat }}</button>
              </div>
              <div class="svc-list">
                <RouterLink
                  v-for="svc in filteredServices"
                  :key="svc.id"
                  :to="`/booking/${stylist.id}?serviceId=${svc.id}`"
                  class="svc-item"
                >
                  <div class="svc-info">
                    <p class="svc-name">{{ svc.name }}</p>
                    <p class="svc-desc">{{ svc.description }}</p>
                    <p class="svc-duration">{{ svc.durationMinutes }}분</p>
                  </div>
                  <div class="svc-right">
                    <p class="svc-price">{{ svc.price?.toLocaleString() }}원</p>
                    <span class="svc-book">예약 →</span>
                  </div>
                </RouterLink>
              </div>
            </section>

            <!-- 리뷰 -->
            <section class="card reviews-card">
              <div class="card-header-row">
                <h2 class="card-title">리뷰 {{ reviews.length }}개</h2>
                <div class="avg-rating">
                  <span class="avg-num">{{ avgRating }}</span>
                  <span class="stars">★★★★★</span>
                </div>
              </div>
              <div v-if="reviews.length === 0" class="state-center" style="padding:32px 0">
                <p class="state-text">아직 리뷰가 없습니다.</p>
              </div>
              <div v-else class="review-list">
                <div v-for="r in reviews" :key="r.id" class="review-item">
                  <div class="review-top">
                    <div class="review-user">
                      <div class="user-avatar">{{ r.userName?.[0] ?? 'U' }}</div>
                      <div>
                        <p class="user-name">{{ r.userName }}</p>
                        <p class="review-date">{{ formatDate(r.createdAt) }}</p>
                      </div>
                    </div>
                    <div class="review-stars">
                      <span v-for="i in 5" :key="i" class="star-item" :class="{ filled: i <= r.rating }">★</span>
                    </div>
                  </div>
                  <p class="review-content">{{ r.content }}</p>
                </div>
              </div>
            </section>
          </div>

          <!-- 우측 사이드바 -->
          <aside class="detail-sidebar">
            <!-- 영업시간 -->
            <div class="card">
              <h2 class="card-title">영업시간</h2>
              <div class="hours-list">
                <div
                  v-for="h in workingHours"
                  :key="h.dayOfWeek"
                  class="hour-row"
                  :class="{ today: isToday(h.dayOfWeek) }"
                >
                  <span class="hour-day">{{ dayName(h.dayOfWeek) }}</span>
                  <span v-if="h.isDayOff" class="hour-off">휴무</span>
                  <span v-else class="hour-time">{{ h.openTime?.slice(0,5) }} – {{ h.closeTime?.slice(0,5) }}</span>
                </div>
              </div>
            </div>

            <!-- 예약 CTA -->
            <div class="card cta-card">
              <p class="cta-label">지금 바로 예약하세요</p>
              <RouterLink :to="`/booking/${stylist.id}`" class="btn btn-primary btn-full">예약하기</RouterLink>
            </div>
          </aside>
        </div>
      </div>
    </template>
  </main>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { stylistApi } from '@/api/stylist'
import { reviewApi } from '@/api/review'
import { reservationApi } from '@/api/reservation'
import api from '@/api/index'
import { favoriteApi } from '@/api/favorite'

const route  = useRoute()
const router = useRouter()

const stylist       = ref(null)
const reviews       = ref([])
const workingHours  = ref([])
const loading       = ref(true)
const activeCategory = ref('전체')
const isFavorited   = ref(false)
const availableSlots = ref([])
const slotsLoading  = ref(false)

const today = new Date()
const todayLabel = `${today.getMonth() + 1}월 ${today.getDate()}일 (${['일','월','화','수','목','금','토'][today.getDay()]})`

const serviceCategories = computed(() => {
  const cats = [...new Set((stylist.value?.services || []).map(s => s.category))]
  return ['전체', ...cats]
})

const filteredServices = computed(() => {
  if (!stylist.value?.services) return []
  if (activeCategory.value === '전체') return stylist.value.services
  return stylist.value.services.filter(s => s.category === activeCategory.value)
})

const avgRating = computed(() => {
  if (!reviews.value.length) return '-'
  return (reviews.value.reduce((s, r) => s + r.rating, 0) / reviews.value.length).toFixed(1)
})

function dayName(d) { return ['일','월','화','수','목','금','토'][d] }
function isToday(d) { return d === today.getDay() }
function formatDate(str) {
  if (!str) return ''
  const d = new Date(str)
  return `${d.getFullYear()}.${String(d.getMonth()+1).padStart(2,'0')}.${String(d.getDate()).padStart(2,'0')}`
}

async function loadAvailableSlots() {
  const id = route.params.id
  const todayStr = today.toISOString().slice(0, 10)
  slotsLoading.value = true
  try {
    const res = await reservationApi.getBookedTimes(id, todayStr)
    const bookedTimes = res.data || []
    const todayHours = workingHours.value.find(h => h.dayOfWeek === today.getDay())
    if (!todayHours || todayHours.isDayOff) { availableSlots.value = []; return }
    const open  = parseInt(todayHours.openTime?.slice(0, 2) || '10')
    const close = parseInt(todayHours.closeTime?.slice(0, 2) || '19')
    const slots = []
    for (let h = open; h < close; h++) {
      const slot = `${String(h).padStart(2,'0')}:00`
      if (!bookedTimes.includes(slot)) slots.push(slot)
    }
    availableSlots.value = slots.slice(0, 8)
  } catch { availableSlots.value = [] }
  finally { slotsLoading.value = false }
}

function goBookWithSlot(slot) {
  router.push({ path: `/booking/${stylist.value.id}`, query: { time: slot } })
}

async function toggleFavorite() {
  try {
    const res = await favoriteApi.toggle(stylist.value.id)
    isFavorited.value = res.data.favorited
  } catch (e) {
    alert(e.response?.data?.message || '로그인이 필요합니다.')
  }
}

onMounted(async () => {
  const id = route.params.id
  try {
    const [stylistRes, reviewRes] = await Promise.all([
      stylistApi.getStylist(id),
      reviewApi.getByStylist(id),
    ])
    stylist.value = stylistRes.data
    reviews.value = reviewRes.data || []
    workingHours.value = stylist.value.workingHours || []
    await loadAvailableSlots()
    try {
      const statusRes = await favoriteApi.checkStatus(id)
      isFavorited.value = statusRes.data.favorited
    } catch { /* 비로그인 시 무시 */ }
  } catch { stylist.value = null }
  finally { loading.value = false }
})
</script>

<style scoped>
/* Hero */
.profile-hero {
  background: var(--bg-card);
  border-bottom: 1px solid var(--border);
  padding: 24px 0 32px;
}
.back-link {
  display: inline-block; margin-bottom: 20px;
  font-size: 13px; color: var(--text-muted);
  background: none; border: none; cursor: pointer; padding: 0;
  transition: var(--transition);
}
.back-link:hover { color: var(--text); }

.profile-layout { display: flex; gap: 32px; align-items: flex-start; }
.profile-img-wrap { flex-shrink: 0; }
.profile-img { width: 140px; height: 140px; border-radius: var(--radius-lg); object-fit: cover; border: 1px solid var(--border); }

.profile-info { flex: 1; }
.profile-badges { display: flex; gap: 6px; margin-bottom: 10px; flex-wrap: wrap; }
.profile-name { font-size: 28px; font-weight: 800; letter-spacing: -0.03em; margin-bottom: 4px; }
.profile-salon { font-size: 15px; font-weight: 600; color: var(--accent); margin-bottom: 2px; }
.profile-location { font-size: 13px; color: var(--text-muted); margin-bottom: 12px; }
.profile-rating { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.rating-num { font-size: 16px; font-weight: 800; }
.rating-cnt { font-size: 13px; color: var(--text-muted); }
.profile-bio { font-size: 14px; color: var(--text-sub); line-height: 1.7; margin-bottom: 20px; max-width: 480px; }
.profile-actions { display: flex; gap: 10px; flex-wrap: wrap; }

/* Body */
.detail-body { padding-top: 28px; }
.detail-grid { display: grid; grid-template-columns: 1fr 300px; gap: 20px; }
.detail-main { display: flex; flex-direction: column; gap: 20px; }
.detail-sidebar { display: flex; flex-direction: column; gap: 16px; position: sticky; top: 76px; }

/* Available slots */
.available-section { margin-bottom: 20px; }
.avail-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.avail-date { font-size: 13px; color: var(--text-muted); }
.slots-loading { padding: 12px 0; display: flex; justify-content: center; }
.slots-empty { font-size: 13px; color: var(--text-muted); padding: 8px 0; }
.slots-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.slot-chip {
  padding: 7px 14px; border-radius: var(--radius-md);
  border: 1.5px solid var(--success); background: var(--success-light);
  font-size: 13px; font-weight: 700; color: var(--success);
  cursor: pointer; transition: var(--transition);
}
.slot-chip:hover { background: var(--success); color: #fff; }

/* Card title */
.card-title { font-size: 17px; font-weight: 700; margin-bottom: 16px; letter-spacing: -0.02em; }
.card-header-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.avg-rating { display: flex; align-items: center; gap: 6px; }
.avg-num { font-size: 22px; font-weight: 800; }

/* Service tabs */
.service-tabs { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 16px; }
.svc-tab {
  padding: 6px 14px; border-radius: var(--radius-full);
  border: 1px solid var(--border); background: transparent;
  font-size: 13px; font-weight: 500; color: var(--text-muted); cursor: pointer; transition: var(--transition);
}
.svc-tab:hover { border-color: var(--border-strong); color: var(--text-sub); }
.svc-tab.active { border-color: var(--primary); background: var(--primary); color: #fff; }

.svc-list { display: flex; flex-direction: column; gap: 2px; }
.svc-item {
  display: flex; justify-content: space-between; align-items: flex-start;
  padding: 14px 16px; border-radius: var(--radius-md);
  border: 1px solid var(--border); background: var(--bg);
  transition: var(--transition); gap: 12px;
}
.svc-item:hover { border-color: var(--primary); background: var(--bg-card); box-shadow: var(--shadow-sm); }
.svc-info { flex: 1; }
.svc-name { font-size: 15px; font-weight: 600; color: var(--text); }
.svc-desc { font-size: 12px; color: var(--text-muted); margin-top: 3px; }
.svc-duration { font-size: 12px; color: var(--text-muted); margin-top: 2px; }
.svc-right { text-align: right; flex-shrink: 0; }
.svc-price { font-size: 16px; font-weight: 800; color: var(--text); }
.svc-book { font-size: 12px; color: var(--accent); font-weight: 600; margin-top: 4px; display: block; }

/* Reviews */
.review-list { display: flex; flex-direction: column; gap: 20px; }
.review-item { padding-bottom: 20px; border-bottom: 1px solid var(--border); }
.review-item:last-child { border-bottom: none; padding-bottom: 0; }
.review-top { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 10px; }
.review-user { display: flex; gap: 10px; align-items: center; }
.user-avatar {
  width: 36px; height: 36px; border-radius: 50%; background: var(--bg-surface);
  display: flex; align-items: center; justify-content: center;
  font-size: 14px; font-weight: 700; color: var(--text-sub); border: 1px solid var(--border);
  flex-shrink: 0;
}
.user-name { font-size: 14px; font-weight: 600; }
.review-date { font-size: 12px; color: var(--text-muted); margin-top: 2px; }
.review-stars { display: flex; gap: 2px; }
.star-item { font-size: 13px; color: var(--border-strong); }
.star-item.filled { color: var(--gold); }
.review-content { font-size: 14px; color: var(--text-sub); line-height: 1.7; }

/* Hours */
.hours-list { display: flex; flex-direction: column; gap: 6px; }
.hour-row { display: flex; justify-content: space-between; align-items: center; padding: 8px 12px; border-radius: var(--radius-sm); font-size: 13px; }
.hour-row.today { background: var(--bg-surface); font-weight: 600; }
.hour-day { color: var(--text-sub); font-weight: 500; }
.hour-off { color: var(--text-muted); }
.hour-time { color: var(--text); font-weight: 600; }

/* CTA */
.cta-card { background: var(--primary); border-color: var(--primary); }
.cta-label { font-size: 14px; color: rgba(255,255,255,0.7); margin-bottom: 12px; }
.cta-card .btn-primary { background: #fff; color: var(--primary); }
.cta-card .btn-primary:hover { background: #f3f4f6; }

@media (max-width: 768px) {
  .profile-layout { flex-direction: column; gap: 20px; }
  .profile-img { width: 100px; height: 100px; }
  .profile-name { font-size: 22px; }
  .detail-grid { grid-template-columns: 1fr; }
  .detail-sidebar { position: static; }
}
</style>
