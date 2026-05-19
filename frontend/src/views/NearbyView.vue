<template>
  <main class="page nearby-page">

    <!-- 지도 영역 -->
    <div class="map-section container">
      <div class="map-wrap">
        <div ref="mapContainer" class="map-container">
          <!-- 위치 권한 요청 전 플레이스홀더 -->
          <div v-if="!locating && !located && !locError" class="map-placeholder">
            <p class="placeholder-icon">📍</p>
            <p class="placeholder-text">내 위치를 기반으로 주변 미용사를 찾습니다</p>
            <button class="btn btn-primary" @click="locate">위치 허용하기</button>
          </div>
          <div v-if="locating" class="map-placeholder">
            <div class="spinner"></div>
            <p class="placeholder-text" style="margin-top:12px">위치를 가져오는 중...</p>
          </div>
          <div v-if="locError" class="map-placeholder">
            <p class="placeholder-icon">⚠️</p>
            <p class="placeholder-text">{{ locError }}</p>
            <button class="btn btn-primary" @click="locate">다시 시도</button>
          </div>
        </div>

        <!-- 현재 위치 재탐색 버튼 -->
        <button v-if="located && mapInstance" class="recenter-btn" title="내 위치로 이동" @click="recenter">
          <svg viewBox="0 0 24 24" fill="none" width="20" height="20">
            <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.8"/>
            <circle cx="12" cy="12" r="4" stroke="currentColor" stroke-width="1.8"/>
            <line x1="12" y1="2" x2="12" y2="5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
            <line x1="12" y1="19" x2="12" y2="22" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
            <line x1="2" y1="12" x2="5" y2="12" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
            <line x1="19" y1="12" x2="22" y2="12" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
          </svg>
        </button>
      </div>

      <!-- 반경 선택 -->
      <div v-if="located" class="radius-chips">
        <button
          v-for="r in radiusOptions" :key="r.value"
          class="r-chip" :class="{ active: radius === r.value }"
          @click="changeRadius(r.value)"
        >{{ r.label }}</button>
      </div>
    </div>

    <!-- 목록 영역 -->
    <div class="list-section container">

      <!-- 헤더 -->
      <div v-if="located" class="list-header">
        <h2 class="list-title">내 주변 미용사 <span class="list-count">{{ rankedStylists.length }}명</span></h2>
        <select v-model="sortBy" class="sort-select">
          <option value="rating">평점순</option>
          <option value="review">리뷰순</option>
          <option value="distance">거리순</option>
        </select>
      </div>

      <div v-if="listLoading" class="state-center"><div class="spinner"></div></div>

      <div v-else-if="located && rankedStylists.length === 0" class="state-center">
        <p class="state-text">반경 {{ radiusLabel }}km 내 등록된 미용사가 없습니다.</p>
      </div>

      <div v-else-if="located" class="stylist-list">
        <RouterLink
          v-for="(s, idx) in rankedStylists"
          :key="s.id"
          :to="`/stylist/${s.id}`"
          class="stylist-row card"
        >
          <span class="rank-num" :class="{ top3: idx < 3 }">{{ idx + 1 }}</span>
          <div class="s-avatar">
            <img v-if="s.profileImg" :src="s.profileImg" alt="" />
            <span v-else>{{ s.name?.[0] }}</span>
          </div>
          <div class="s-info">
            <p class="s-name">{{ s.name }}</p>
            <p class="s-salon">{{ s.salonName || '프리랜서' }}</p>
            <div class="s-meta">
              <span class="s-rating">★ {{ Number(s.rating ?? 0).toFixed(1) }}</span>
              <span class="s-dot">·</span>
              <span class="s-review">리뷰 {{ s.reviewCount ?? 0 }}</span>
              <span v-if="s.distance != null" class="s-dot">·</span>
              <span v-if="s.distance != null" class="s-dist">{{ formatDist(s.distance) }}</span>
            </div>
          </div>
          <span class="s-arrow">›</span>
        </RouterLink>
      </div>

    </div>
  </main>
</template>

<script setup>
import { ref, computed, onMounted, onActivated, nextTick } from 'vue'
import { stylistApi } from '@/api/stylist'

const mapContainer = ref(null)
const mapInstance  = ref(null)
const mapMarkers   = ref([])
const myLocation   = ref(null)
const stylists     = ref([])
const locating     = ref(false)
const located      = ref(false)
const locError     = ref('')
const listLoading  = ref(false)
const radius       = ref(3000)
const sortBy       = ref('rating')

const radiusOptions = [
  { label: '1km', value: 1000 },
  { label: '3km', value: 3000 },
  { label: '5km', value: 5000 },
]
const radiusLabel = computed(() => radiusOptions.find(r => r.value === radius.value)?.label?.replace('km','') ?? '3')

const rankedStylists = computed(() => {
  const list = [...stylists.value]
  if (sortBy.value === 'rating')   return list.sort((a, b) => (Number(b.rating) || 0) - (Number(a.rating) || 0))
  if (sortBy.value === 'review')   return list.sort((a, b) => (b.reviewCount || 0) - (a.reviewCount || 0))
  if (sortBy.value === 'distance') return list.sort((a, b) => (a.distance || 0) - (b.distance || 0))
  return list
})

function formatDist(m) {
  if (m == null) return ''
  return m < 1000 ? `${Math.round(m)}m` : `${(m / 1000).toFixed(1)}km`
}

function waitForKakao(timeout = 5000) {
  return new Promise((resolve, reject) => {
    if (window.kakao?.maps) return resolve()
    let elapsed = 0
    const t = setInterval(() => {
      elapsed += 100
      if (window.kakao?.maps) { clearInterval(t); resolve() }
      else if (elapsed >= timeout) { clearInterval(t); reject(new Error('Kakao SDK timeout')) }
    }, 100)
  })
}

function clearMapMarkers() {
  mapMarkers.value.forEach(m => m.setMap(null))
  mapMarkers.value = []
}

function drawMarkers(map, lat, lng) {
  clearMapMarkers()

  // 내 위치 (별 마커)
  const myMarker = new window.kakao.maps.Marker({
    map,
    position: new window.kakao.maps.LatLng(lat, lng),
    image: new window.kakao.maps.MarkerImage(
      'https://t1.daumcdn.net/localimg/localimages/07/mapapidoc/markerStar.png',
      new window.kakao.maps.Size(24, 35)
    ),
  })
  mapMarkers.value.push(myMarker)

  // 스타일리스트 마커
  stylists.value.forEach(s => {
    if (!s.latitude || !s.longitude) return
    const pos = new window.kakao.maps.LatLng(s.latitude, s.longitude)
    const marker = new window.kakao.maps.Marker({ map, position: pos })
    const info = new window.kakao.maps.InfoWindow({
      content: `<div style="padding:6px 10px;font-size:13px;font-weight:600;cursor:pointer;white-space:nowrap"
        onclick="location.href='/stylist/${s.id}'">${s.name}<br>
        <span style="font-size:11px;color:#6b7280">★${Number(s.rating ?? 0).toFixed(1)} · ${s.salonName || '프리랜서'}</span></div>`,
    })
    window.kakao.maps.event.addListener(marker, 'click', () => info.open(map, marker))
    mapMarkers.value.push(marker)
  })
}

function initMap(lat, lng) {
  if (!mapContainer.value) return
  const center = new window.kakao.maps.LatLng(lat, lng)

  if (mapInstance.value) {
    // 지도 재사용: 마커만 새로 그림
    mapInstance.value.setCenter(center)
    drawMarkers(mapInstance.value, lat, lng)
    return
  }

  const map = new window.kakao.maps.Map(mapContainer.value, { center, level: 5 })
  mapInstance.value = map
  drawMarkers(map, lat, lng)
}

async function fetchAndDraw(lat, lng, r) {
  listLoading.value = true
  try {
    const res = await stylistApi.getNearbyStylists(lat, lng, r)
    stylists.value = res.data || []
  } catch {
    stylists.value = []
  } finally {
    listLoading.value = false
  }

  await nextTick()
  await waitForKakao()
  window.kakao.maps.load(() => initMap(lat, lng))
}

async function locate() {
  if (!navigator.geolocation) {
    locError.value = '이 브라우저는 위치 서비스를 지원하지 않습니다.'
    return
  }
  locError.value = ''
  locating.value = true
  navigator.geolocation.getCurrentPosition(
    async ({ coords: { latitude: lat, longitude: lng } }) => {
      locating.value = false
      myLocation.value = { lat, lng }
      located.value = true
      await fetchAndDraw(lat, lng, radius.value)
    },
    (err) => {
      locating.value = false
      locError.value = '위치 정보를 가져올 수 없습니다. 브라우저 위치 권한을 허용해주세요.'
    }
  )
}

async function changeRadius(r) {
  radius.value = r
  if (!myLocation.value) return
  await fetchAndDraw(myLocation.value.lat, myLocation.value.lng, r)
}

function recenter() {
  if (!mapInstance.value || !myLocation.value) return
  mapInstance.value.setCenter(new window.kakao.maps.LatLng(myLocation.value.lat, myLocation.value.lng))
  mapInstance.value.setLevel(5)
}

onMounted(locate)

onActivated(async () => {
  if (located.value && mapInstance.value) {
    await nextTick()
    mapInstance.value.relayout()
    recenter()
  }
})
</script>

<style scoped>
.nearby-page { padding: 0; }

/* 지도 */
.map-section { padding: 20px 0 8px; }
.map-wrap { position: relative; }
.map-container {
  width: 100%; height: 320px;
  border-radius: var(--radius-lg); overflow: hidden;
  border: 1px solid var(--border);
  display: flex; align-items: center; justify-content: center;
  background: var(--bg-surface);
}
.map-placeholder {
  display: flex; flex-direction: column; align-items: center; gap: 12px;
  padding: 24px;
}
.placeholder-icon { font-size: 40px; }
.placeholder-text { font-size: 14px; color: var(--text-muted); text-align: center; }

/* 내 위치 버튼 */
.recenter-btn {
  position: absolute; right: 12px; bottom: 12px;
  width: 40px; height: 40px; border-radius: 50%;
  background: #fff; border: 1px solid var(--border);
  box-shadow: 0 2px 6px rgba(0,0,0,0.12);
  display: flex; align-items: center; justify-content: center;
  cursor: pointer; color: var(--primary); z-index: 10;
  transition: var(--transition);
}
.recenter-btn:hover { box-shadow: 0 4px 12px rgba(0,0,0,0.18); }

/* 반경 칩 */
.radius-chips { display: flex; gap: 6px; margin-top: 10px; flex-wrap: wrap; }
.r-chip {
  padding: 5px 14px; border-radius: 20px;
  background: var(--bg-card); border: 1.5px solid var(--border);
  font-size: 12px; font-weight: 600; color: var(--text-muted);
  cursor: pointer; transition: var(--transition);
}
.r-chip.active { background: var(--primary); color: #fff; border-color: var(--primary); }

/* 목록 */
.list-section { padding: 20px 16px 40px; max-width: 680px; margin: 0 auto; }

.list-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 14px;
}
.list-title { font-size: 18px; font-weight: 800; }
.list-count { font-size: 14px; font-weight: 600; color: var(--primary); margin-left: 4px; }
.sort-select {
  padding: 6px 10px; border: 1.5px solid var(--border); border-radius: var(--radius-sm);
  font-size: 13px; background: var(--bg); color: var(--text); cursor: pointer;
}

.stylist-list { display: flex; flex-direction: column; gap: 8px; }
.stylist-row {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 16px; text-decoration: none; color: var(--text);
  transition: background 0.15s;
}
.stylist-row:hover { background: var(--bg-surface); }

.rank-num {
  width: 26px; flex-shrink: 0; text-align: center;
  font-size: 15px; font-weight: 800; color: var(--text-muted);
}
.rank-num.top3 { color: var(--primary); }

.s-avatar {
  width: 44px; height: 44px; border-radius: 50%; flex-shrink: 0;
  background: var(--primary); color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 16px; font-weight: 700; overflow: hidden;
}
.s-avatar img { width: 100%; height: 100%; object-fit: cover; }

.s-info { flex: 1; min-width: 0; }
.s-name { font-size: 15px; font-weight: 700; margin-bottom: 2px; }
.s-salon { font-size: 12px; color: var(--text-muted); margin-bottom: 4px; }
.s-meta { display: flex; align-items: center; gap: 5px; font-size: 12px; }
.s-rating { color: var(--gold, #f59e0b); font-weight: 600; }
.s-dot { color: var(--border-strong); }
.s-review { color: var(--text-muted); }
.s-dist { color: var(--text-muted); }

.s-arrow { font-size: 20px; color: var(--text-muted); flex-shrink: 0; }

.state-center { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 48px 0; }
.state-text { color: var(--text-muted); font-size: 14px; text-align: center; }
</style>
