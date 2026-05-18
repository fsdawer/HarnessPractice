<template>
  <div class="community-page">
    <div class="container">

      <!-- 헤더 -->
      <div class="page-header">
        <div class="header-left">
          <h1 class="page-title">커뮤니티</h1>
          <p class="page-sub">미용 이야기를 자유롭게 나눠요</p>
        </div>
        <RouterLink to="/community/write" class="btn btn-primary btn-sm write-btn">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          글쓰기
        </RouterLink>
      </div>

      <!-- 탭 -->
      <div class="tabs">
        <button class="tab-btn" :class="{ active: activeTab === 'all' }" @click="switchTab('all')">
          전체 커뮤니티
        </button>
        <button class="tab-btn" :class="{ active: activeTab === 'local' }" @click="switchTab('local')">
          우리 동네
          <span v-if="auth.user?.verifiedDistrict" class="tab-district">{{ auth.user.verifiedDistrict }}</span>
        </button>
      </div>

      <!-- 우리 동네: 위치 미인증 -->
      <div v-if="activeTab === 'local' && !auth.user?.verifiedDistrict" class="district-banner">
        <div class="banner-icon">📍</div>
        <p class="banner-title">우리 동네 게시글을 보려면 위치 인증이 필요해요</p>
        <p class="banner-sub">현재 위치를 기반으로 같은 동네 글만 보여드려요</p>
        <button class="btn-locate" @click="doLocate" :disabled="locating">
          <span v-if="locating" class="spinner"></span>
          <svg v-else width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
            <circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3"/>
          </svg>
          {{ locating ? '위치 감지 중...' : '내 위치로 인증하기' }}
        </button>
        <p v-if="locateError" class="locate-error">{{ locateError }}</p>
      </div>

      <!-- 게시글 목록 -->
      <div class="post-list">
        <div
          v-for="post in posts"
          :key="post.id"
          class="post-card"
          @click="$router.push(`/community/${post.id}`)"
        >
          <div class="card-badges">
            <span class="badge-district">{{ post.district }}</span>
            <span v-if="post.price != null" class="badge-price">{{ post.price.toLocaleString() }}원</span>
          </div>
          <h3 class="card-title">{{ post.title }}</h3>
          <p class="card-preview">{{ post.content.slice(0, 80) }}{{ post.content.length > 80 ? '...' : '' }}</p>
          <div class="card-footer">
            <span class="card-author">{{ post.authorName }}</span>
            <span class="card-dot">·</span>
            <span class="card-date">{{ formatDate(post.createdAt) }}</span>
            <div class="card-stats">
              <span class="stat">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                {{ post.viewCount }}
              </span>
              <span class="stat">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>
                {{ post.likeCount }}
              </span>
            </div>
          </div>
        </div>

        <div v-if="posts.length === 0 && !loading" class="empty-state">
          <p class="empty-icon">✍️</p>
          <p class="empty-text">아직 게시글이 없어요</p>
          <p class="empty-sub">첫 번째 글을 작성해보세요!</p>
        </div>

        <div ref="scrollTrigger" class="scroll-trigger"></div>
        <div v-if="loading" class="loading-row">
          <span class="loading-dot"></span>
          <span class="loading-dot"></span>
          <span class="loading-dot"></span>
        </div>
      </div>

    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { communityApi } from '@/api/community'
import { userApi } from '@/api/user'
import { useAuthStore } from '@/stores/authStore'

const auth = useAuthStore()

const activeTab = ref('all')
const posts = ref([])
const loading = ref(false)
const hasMore = ref(true)
const lastId = ref(null)
const scrollTrigger = ref(null)

const locating = ref(false)
const locateError = ref('')

let observer = null

async function fetchPosts(reset = false) {
  if (loading.value || (!hasMore.value && !reset)) return
  if (activeTab.value === 'local' && !auth.user?.verifiedDistrict) return

  if (reset) {
    posts.value = []
    lastId.value = null
    hasMore.value = true
  }

  loading.value = true
  try {
    const res = activeTab.value === 'all'
      ? await communityApi.getPosts(lastId.value)
      : await communityApi.getLocalPosts(lastId.value)

    const newPosts = res.data
    posts.value.push(...newPosts)
    if (newPosts.length > 0) lastId.value = newPosts[newPosts.length - 1].id
    if (newPosts.length < 20) hasMore.value = false
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function switchTab(tab) {
  activeTab.value = tab
  fetchPosts(true)
}

async function doLocate() {
  if (!navigator.geolocation) {
    locateError.value = '이 브라우저는 위치 서비스를 지원하지 않아요.'
    return
  }
  locating.value = true
  locateError.value = ''
  navigator.geolocation.getCurrentPosition(
    async (pos) => {
      try {
        const { data } = await userApi.getDistrictByCoords(pos.coords.latitude, pos.coords.longitude)
        await userApi.verifyDistrict(data.district)
        auth.user = { ...auth.user, verifiedDistrict: data.district }
        localStorage.setItem('user', JSON.stringify(auth.user))
        await fetchPosts(true)
      } catch {
        locateError.value = '위치 인증에 실패했습니다. 다시 시도해 주세요.'
      } finally {
        locating.value = false
      }
    },
    () => {
      locateError.value = '위치 권한을 허용해 주세요.'
      locating.value = false
    }
  )
}

function formatDate(dateStr) {
  const d = new Date(dateStr)
  const now = new Date()
  const diff = Math.floor((now - d) / 1000)
  if (diff < 60) return '방금 전'
  if (diff < 3600) return `${Math.floor(diff / 60)}분 전`
  if (diff < 86400) return `${Math.floor(diff / 3600)}시간 전`
  return `${d.getMonth() + 1}/${d.getDate()}`
}

function setupObserver() {
  if (observer) observer.disconnect()
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0].isIntersecting && hasMore.value && !loading.value) fetchPosts()
    },
    { threshold: 0.1 }
  )
  if (scrollTrigger.value) observer.observe(scrollTrigger.value)
}

onMounted(() => { fetchPosts(true); setupObserver() })
onUnmounted(() => { if (observer) observer.disconnect() })
</script>

<style scoped>
.community-page { padding: 32px 0 80px; min-height: 80vh; }

/* 헤더 */
.page-header {
  display: flex; align-items: flex-end; justify-content: space-between;
  margin-bottom: 24px;
}
.header-left { display: flex; flex-direction: column; gap: 4px; }
.page-title { font-size: 22px; font-weight: 700; color: var(--text); }
.page-sub { font-size: 13px; color: var(--text-muted); }
.write-btn {
  display: inline-flex; align-items: center; gap: 6px;
  white-space: nowrap; text-decoration: none;
}

/* 탭 */
.tabs {
  display: flex; gap: 0; margin-bottom: 20px;
  border-bottom: 1.5px solid var(--border);
}
.tab-btn {
  display: flex; align-items: center; gap: 6px;
  padding: 10px 20px; background: none; border: none;
  font-size: 14px; font-weight: 500; color: var(--text-muted);
  cursor: pointer; border-bottom: 2px solid transparent;
  margin-bottom: -1.5px; transition: color 0.15s;
}
.tab-btn.active { color: var(--text); border-bottom-color: var(--text); font-weight: 700; }
.tab-btn:hover:not(.active) { color: var(--text); }
.tab-district {
  font-size: 11px; background: var(--text); color: #fff;
  padding: 1px 7px; border-radius: 20px; font-weight: 600;
}

/* 위치 인증 배너 */
.district-banner {
  display: flex; flex-direction: column; align-items: center;
  background: var(--bg-surface); border: 1px solid var(--border);
  border-radius: var(--radius-lg); padding: 32px 24px;
  margin-bottom: 20px; text-align: center;
}
.banner-icon { font-size: 28px; margin-bottom: 10px; }
.banner-title { font-size: 15px; font-weight: 700; color: var(--text); margin-bottom: 6px; }
.banner-sub { font-size: 13px; color: var(--text-muted); margin-bottom: 20px; }

.btn-locate {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 11px 22px; background: var(--text); color: #fff;
  border: none; border-radius: var(--radius-sm);
  font-size: 14px; font-weight: 600; cursor: pointer;
  transition: background 0.15s;
}
.btn-locate:hover:not(:disabled) { background: var(--primary-hover); }
.btn-locate:disabled { opacity: 0.55; cursor: not-allowed; }

.spinner {
  width: 14px; height: 14px;
  border: 2px solid rgba(255,255,255,0.35);
  border-top-color: #fff; border-radius: 50%;
  animation: spin 0.65s linear infinite; display: inline-block;
}
@keyframes spin { to { transform: rotate(360deg); } }
.locate-error { margin-top: 12px; font-size: 13px; color: #e53e3e; }

/* 게시글 목록 */
.post-list { display: flex; flex-direction: column; gap: 0; }

.post-card {
  background: #fff; border-bottom: 1px solid var(--border);
  padding: 18px 0; cursor: pointer; transition: background 0.12s;
}
.post-card:first-child { border-top: 1px solid var(--border); }
.post-card:hover { background: var(--bg-surface); }

.card-badges { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; }
.badge-district {
  font-size: 11px; font-weight: 600; color: var(--text-muted);
  background: #f4f4f5; padding: 2px 8px; border-radius: 20px;
}
.badge-price {
  font-size: 12px; font-weight: 700; color: var(--text);
  background: #f4f4f5; padding: 2px 8px; border-radius: 20px;
}

.card-title {
  font-size: 15px; font-weight: 600; color: var(--text);
  margin-bottom: 5px; line-height: 1.4;
}
.card-preview {
  font-size: 13px; color: var(--text-muted); line-height: 1.55;
  margin-bottom: 12px;
}

.card-footer {
  display: flex; align-items: center; gap: 6px;
  font-size: 12px; color: var(--text-muted);
}
.card-author { font-weight: 500; }
.card-dot { color: var(--border-strong); }
.card-stats { display: flex; align-items: center; gap: 10px; margin-left: auto; }
.stat { display: flex; align-items: center; gap: 3px; }

/* 빈 상태 */
.empty-state {
  padding: 60px 0; text-align: center;
}
.empty-icon { font-size: 36px; margin-bottom: 12px; }
.empty-text { font-size: 15px; font-weight: 600; color: var(--text); margin-bottom: 6px; }
.empty-sub { font-size: 13px; color: var(--text-muted); }

.scroll-trigger { height: 1px; }

/* 로딩 점 애니메이션 */
.loading-row {
  display: flex; justify-content: center; align-items: center;
  gap: 6px; padding: 24px;
}
.loading-dot {
  width: 7px; height: 7px; border-radius: 50%;
  background: var(--border-strong); animation: bounce 1s ease-in-out infinite;
}
.loading-dot:nth-child(2) { animation-delay: 0.15s; }
.loading-dot:nth-child(3) { animation-delay: 0.3s; }
@keyframes bounce {
  0%, 100% { transform: translateY(0); opacity: 0.4; }
  50% { transform: translateY(-5px); opacity: 1; }
}

@media (max-width: 768px) {
  .page-title { font-size: 18px; }
  .post-card { padding: 16px 0; }
}
</style>
