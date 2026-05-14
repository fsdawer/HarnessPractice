<template>
  <main class="page">
    <!-- Hero -->
    <section class="hero">
      <div class="container">
        <div class="hero-inner">
          <div class="hero-text">
            <p class="hero-eyebrow">내 주변 미용사 예약 플랫폼</p>
            <h1 class="hero-title">믿을 수 있는<br>스타일리스트</h1>
            <p class="hero-sub">검증된 미용사를 쉽고 빠르게 예약하세요</p>
          </div>
          <div class="hero-search">
            <div class="search-row">
              <input
                v-model="searchQuery"
                class="search-input"
                placeholder="미용사 이름, 미용실 검색"
                @keyup.enter="search"
              />
              <input
                v-model="districtQuery"
                class="search-input"
                placeholder="지역 (예: 강남구)"
                @keyup.enter="search"
              />
              <button class="btn btn-primary search-btn" @click="search">검색</button>
            </div>
            <div class="search-actions">
              <div class="quick-tags">
                <button v-for="tag in popularTags" :key="tag" class="tag-chip" @click="activeFilter = tag; search()">{{ tag }}</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <div class="container content-area">
      <!-- 필터 바 -->
      <div class="filter-bar">
        <div class="filter-chips">
          <button v-for="f in filters" :key="f.value" class="f-chip" :class="{ active: activeFilter === f.value }" @click="activeFilter = f.value">
            {{ f.label }}
          </button>
        </div>
        <div class="sort-wrap">
          <select v-model="sortBy" class="sort-select">
            <option value="rating">평점순</option>
            <option value="review">리뷰순</option>
            <option value="exp">경력순</option>
          </select>
        </div>
      </div>

      <!-- 로딩 -->
      <div v-if="loading" class="state-center">
        <div class="spinner"></div>
        <p class="state-text">검색 중...</p>
      </div>

      <!-- 결과 없음 -->
      <div v-else-if="sortedStylists.length === 0" class="state-center">
        <p class="state-text">검색 결과가 없습니다.</p>
        <button class="btn btn-ghost btn-sm" @click="resetSearch">검색 초기화</button>
      </div>

      <!-- 스타일리스트 목록 -->
      <div v-else class="stylists-grid">
        <StylistCard v-for="s in sortedStylists" :key="s.id" :stylist="s" />
      </div>

      <!-- AI 헤어 추천 배너 -->
      <RouterLink to="/hair-analysis" class="ai-banner">
        <div class="ai-banner-text">
          <p class="ai-banner-eyebrow">✨ AI 기반 헤어 추천</p>
          <p class="ai-banner-title">내 얼굴형에 맞는<br>헤어스타일 찾기</p>
          <p class="ai-banner-desc">사진 한 장으로 얼굴형 분석 + 맞춤 스타일 추천</p>
        </div>
        <div class="ai-banner-cta">분석 시작 →</div>
      </RouterLink>

      <!-- 랭킹 섹션 -->
      <section class="ranking-section">
        <div class="section-header">
          <div>
            <h2 class="section-title">지역별 랭킹</h2>
            <p class="section-subtitle">예약 수와 평점을 종합한 실시간 랭킹입니다</p>
          </div>
          <RouterLink to="/ranking" class="btn btn-ghost btn-sm">전체보기 →</RouterLink>
        </div>

        <div class="rank-district-tabs">
          <button
            v-for="d in districts"
            :key="d"
            class="d-chip"
            :class="{ active: rankDistrict === d }"
            @click="rankDistrict = d; loadRanking()"
          >{{ d }}</button>
        </div>

        <div v-if="rankLoading" class="state-center" style="padding: 40px 0">
          <div class="spinner"></div>
        </div>

        <div v-else-if="rankList.length === 0" class="state-center" style="padding: 40px 0">
          <p class="state-text">랭킹 데이터가 없습니다.</p>
        </div>

        <div v-else class="rank-list">
          <RouterLink
            v-for="(item, idx) in rankList.slice(0, 5)"
            :key="item.stylistId"
            :to="`/stylist/${item.stylistId}`"
            class="rank-item"
          >
            <span class="rank-num" :class="{ top3: idx < 3 }">{{ idx + 1 }}</span>
            <div class="rank-info">
              <p class="rank-name">{{ item.stylistName }}</p>
              <p class="rank-salon">{{ item.salonName }} · {{ item.district }}</p>
            </div>
            <div class="rank-stats">
              <span class="rank-rating">★ {{ item.avgRating?.toFixed(1) }}</span>
              <span class="rank-divider">|</span>
              <span class="rank-review">리뷰 {{ item.reviewCount }}</span>
            </div>
          </RouterLink>
        </div>
      </section>
    </div>
  </main>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import StylistCard from '@/components/StylistCard.vue'
import { stylistApi } from '@/api/stylist'
import { rankingApi } from '@/api/ranking'

const searchQuery  = ref('')
const districtQuery = ref('')
const activeFilter = ref('all')
const sortBy       = ref('rating')
const loading      = ref(false)
const stylists     = ref([])

const rankDistrict = ref('강남구')
const rankLoading  = ref(false)
const rankList     = ref([])

const districts = ['강남구', '서초구', '마포구', '용산구', '종로구', '송파구']
const popularTags = ['커트', '펌', '염색', '클리닉', '헤어케어']
const filters = [
  { label: '전체', value: 'all' },
  { label: '커트', value: '커트' },
  { label: '펌', value: '펌' },
  { label: '염색', value: '염색' },
  { label: '클리닉', value: '클리닉' },
]

const sortedStylists = computed(() => stylists.value)

async function search() {
  loading.value = true
  try {
    const params = { keyword: searchQuery.value }
    if (districtQuery.value) params.district = districtQuery.value
    if (activeFilter.value !== 'all') params.category = activeFilter.value
    if (sortBy.value) params.sort = sortBy.value
    const res = await stylistApi.getStylists(params)
    stylists.value = res.data
  } catch { stylists.value = [] }
  finally { loading.value = false }
}

watch([activeFilter, sortBy], () => search())

function resetSearch() {
  searchQuery.value = ''
  districtQuery.value = ''
  search()
}

async function loadRanking() {
  rankLoading.value = true
  try {
    const res = await rankingApi.getRanking(rankDistrict.value)
    rankList.value = res.data
  } catch { rankList.value = [] }
  finally { rankLoading.value = false }
}

onMounted(() => { search(); loadRanking() })
</script>

<style scoped>
/* Hero */
.hero {
  background: var(--bg-card);
  border-bottom: 1px solid var(--border);
  padding: 48px 0 40px;
}
.hero-inner {
  display: grid;
  grid-template-columns: 1fr 1.4fr;
  gap: 48px;
  align-items: center;
}
.hero-eyebrow { font-size: 12px; font-weight: 700; color: var(--accent); letter-spacing: 0.08em; text-transform: uppercase; margin-bottom: 12px; }
.hero-title { font-size: 40px; font-weight: 800; line-height: 1.2; letter-spacing: -0.03em; margin-bottom: 12px; }
.hero-sub { font-size: 15px; color: var(--text-muted); }

.search-row { display: flex; gap: 8px; margin-bottom: 12px; }
.search-input {
  flex: 1; padding: 12px 16px; border: 1.5px solid var(--border);
  border-radius: var(--radius-md); font-size: 14px; font-family: var(--font);
  background: var(--bg); color: var(--text); transition: var(--transition);
}
.search-input:focus { border-color: var(--primary); outline: none; background: #fff; }
.search-btn { padding: 12px 24px; flex-shrink: 0; }

.search-actions { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.nearby-btn { font-size: 13px; }
.nearby-btn.active { border-color: var(--primary); color: var(--primary); background: var(--bg-surface); }

.quick-tags { display: flex; gap: 6px; flex-wrap: wrap; }
.tag-chip {
  padding: 4px 12px; border-radius: var(--radius-full);
  border: 1px solid var(--border); background: transparent;
  font-size: 12px; font-weight: 500; color: var(--text-muted);
  cursor: pointer; transition: var(--transition);
}
.tag-chip:hover { border-color: var(--primary); color: var(--primary); background: var(--bg-surface); }

/* Content */
.content-area { padding-top: 28px; }

/* Filter */
.filter-bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; gap: 12px; flex-wrap: wrap; }
.filter-chips { display: flex; gap: 6px; flex-wrap: wrap; }
.f-chip {
  padding: 6px 14px; border-radius: var(--radius-full);
  border: 1px solid var(--border); background: var(--bg-card);
  font-size: 13px; font-weight: 500; color: var(--text-muted);
  cursor: pointer; transition: var(--transition);
}
.f-chip:hover { border-color: var(--border-strong); color: var(--text-sub); }
.f-chip.active { border-color: var(--primary); background: var(--primary); color: #fff; }

.sort-select {
  padding: 6px 12px; border: 1px solid var(--border); border-radius: var(--radius-sm);
  font-size: 13px; background: var(--bg-card); color: var(--text-sub); cursor: pointer;
}

/* Grid */
.stylists-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; margin-bottom: 48px; }

/* Ranking Section */
.ai-banner {
  display: flex; align-items: center; justify-content: space-between;
  background: linear-gradient(135deg, #7c5cef 0%, #a97cf8 100%);
  border-radius: 20px; padding: 28px 32px; margin: 40px 0 0;
  color: #fff; text-decoration: none; transition: opacity 0.2s;
}
.ai-banner:hover { opacity: 0.92; }
.ai-banner-eyebrow { font-size: 0.8rem; font-weight: 600; opacity: 0.85; margin-bottom: 6px; }
.ai-banner-title { font-size: 1.3rem; font-weight: 800; line-height: 1.3; margin-bottom: 8px; }
.ai-banner-desc { font-size: 0.85rem; opacity: 0.8; }
.ai-banner-cta {
  flex-shrink: 0; background: rgba(255,255,255,0.2); border-radius: 12px;
  padding: 10px 20px; font-size: 0.9rem; font-weight: 700; white-space: nowrap;
}
.ranking-section { padding-top: 40px; border-top: 1px solid var(--border); }
.section-header { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 20px; }

.rank-district-tabs { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 16px; }
.d-chip {
  padding: 6px 14px; border-radius: var(--radius-full);
  border: 1px solid var(--border); background: var(--bg-card);
  font-size: 13px; font-weight: 500; color: var(--text-muted);
  cursor: pointer; transition: var(--transition);
}
.d-chip:hover { border-color: var(--border-strong); color: var(--text-sub); }
.d-chip.active { border-color: var(--primary); background: var(--primary); color: #fff; }

.rank-list { display: flex; flex-direction: column; gap: 2px; }
.rank-item {
  display: flex; align-items: center; gap: 16px;
  padding: 14px 16px; border-radius: var(--radius-md);
  background: var(--bg-card); border: 1px solid var(--border);
  transition: var(--transition);
}
.rank-item:hover { border-color: var(--border-strong); box-shadow: var(--shadow); transform: translateX(2px); }

.rank-num {
  width: 28px; height: 28px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 13px; font-weight: 800; flex-shrink: 0;
  background: var(--bg-surface); color: var(--text-muted);
}
.rank-num.top3 { background: var(--primary); color: #fff; }

.rank-info { flex: 1; min-width: 0; }
.rank-name { font-size: 15px; font-weight: 700; color: var(--text); }
.rank-salon { font-size: 12px; color: var(--text-muted); margin-top: 2px; }

.rank-stats { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--text-muted); flex-shrink: 0; }
.rank-rating { color: var(--gold); font-weight: 700; }
.rank-divider { color: var(--border-strong); }

@media (max-width: 768px) {
  .hero-inner { grid-template-columns: 1fr; gap: 24px; }
  .hero-title { font-size: 28px; }
  .search-row { flex-direction: column; }
  .stylists-grid { grid-template-columns: 1fr; }
  .section-header { flex-direction: column; gap: 12px; }
}
</style>
