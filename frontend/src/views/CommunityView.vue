<template>
  <div class="community-page">
    <div class="container">
      <div class="page-header">
        <h1 class="page-title">커뮤니티</h1>
      </div>

      <!-- 탭 -->
      <div class="tabs">
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'all' }"
          @click="switchTab('all')"
        >전체 커뮤니티</button>
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'local' }"
          @click="switchTab('local')"
        >우리 동네</button>
      </div>

      <!-- 우리 동네: 위치 인증 배너 -->
      <div v-if="activeTab === 'local' && !auth.user?.verifiedDistrict" class="district-banner">
        <p class="banner-text">우리 동네 게시글을 보려면 위치 인증이 필요합니다.</p>
        <div class="banner-form">
          <input
            v-model="districtInput"
            class="district-input"
            placeholder="예: 강남구"
            @keyup.enter="doVerifyDistrict"
          />
          <button class="btn btn-primary btn-sm" @click="doVerifyDistrict" :disabled="verifying">
            {{ verifying ? '인증 중...' : '인증하기' }}
          </button>
        </div>
      </div>

      <!-- 게시글 목록 -->
      <div class="post-list">
        <div
          v-for="post in posts"
          :key="post.id"
          class="post-card"
          @click="$router.push(`/community/${post.id}`)"
        >
          <div class="post-card-top">
            <span class="post-district">{{ post.district }}</span>
            <span v-if="post.price != null" class="post-price">{{ post.price.toLocaleString() }}원</span>
          </div>
          <h3 class="post-title">{{ post.title }}</h3>
          <p class="post-preview">{{ post.content.slice(0, 80) }}{{ post.content.length > 80 ? '...' : '' }}</p>
          <div class="post-footer">
            <span class="post-meta">{{ post.authorName }}</span>
            <span class="post-meta">{{ formatDate(post.createdAt) }}</span>
            <span class="post-stat">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
              {{ post.viewCount }}
            </span>
            <span class="post-stat">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>
              {{ post.likeCount }}
            </span>
          </div>
        </div>

        <div v-if="posts.length === 0 && !loading" class="empty-state">
          <p>게시글이 없습니다.</p>
        </div>

        <!-- 무한 스크롤 트리거 -->
        <div ref="scrollTrigger" class="scroll-trigger"></div>

        <div v-if="loading" class="loading-indicator">불러오는 중...</div>
      </div>
    </div>

    <!-- 글쓰기 플로팅 버튼 -->
    <RouterLink
      v-if="auth.isLoggedIn"
      to="/community/write"
      class="fab"
      aria-label="글쓰기"
    >
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
        <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
      </svg>
    </RouterLink>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
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

const districtInput = ref('')
const verifying = ref(false)

let observer = null

async function fetchPosts(reset = false) {
  if (loading.value || (!hasMore.value && !reset)) return

  // 우리동네 탭인데 인증 안 됐으면 중단
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

    if (newPosts.length > 0) {
      lastId.value = newPosts[newPosts.length - 1].id
    }
    if (newPosts.length < 20) {
      hasMore.value = false
    }
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

async function doVerifyDistrict() {
  if (!districtInput.value.trim()) return
  verifying.value = true
  try {
    await userApi.verifyDistrict(districtInput.value.trim())
    // authStore의 user 업데이트
    auth.user = { ...auth.user, verifiedDistrict: districtInput.value.trim() }
    localStorage.setItem('user', JSON.stringify(auth.user))
    await fetchPosts(true)
  } catch (e) {
    alert('위치 인증에 실패했습니다.')
  } finally {
    verifying.value = false
  }
}

function formatDate(dateStr) {
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

function setupObserver() {
  if (observer) observer.disconnect()
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0].isIntersecting && hasMore.value && !loading.value) {
        fetchPosts()
      }
    },
    { threshold: 0.1 }
  )
  if (scrollTrigger.value) observer.observe(scrollTrigger.value)
}

onMounted(() => {
  fetchPosts(true)
  setupObserver()
})

onUnmounted(() => {
  if (observer) observer.disconnect()
})
</script>

<style scoped>
.community-page { padding: 24px 0 80px; min-height: 80vh; }
.page-header { margin-bottom: 20px; }
.page-title { font-size: 22px; font-weight: 700; color: var(--text); }

.tabs { display: flex; gap: 8px; margin-bottom: 20px; border-bottom: 1px solid var(--border); }
.tab-btn {
  padding: 10px 18px; background: none; border: none;
  font-size: 14px; font-weight: 500; color: var(--text-muted);
  cursor: pointer; border-bottom: 2px solid transparent; transition: var(--transition);
}
.tab-btn.active { color: var(--primary); border-bottom-color: var(--primary); font-weight: 700; }
.tab-btn:hover { color: var(--text); }

.district-banner {
  background: #f0f4ff; border: 1px solid #c7d7ff;
  border-radius: var(--radius); padding: 16px; margin-bottom: 20px;
}
.banner-text { font-size: 14px; color: var(--text-sub); margin-bottom: 10px; }
.banner-form { display: flex; gap: 8px; }
.district-input {
  flex: 1; padding: 8px 12px; border: 1px solid var(--border);
  border-radius: var(--radius-sm); font-size: 14px; outline: none;
}
.district-input:focus { border-color: var(--primary); }

.post-list { display: flex; flex-direction: column; gap: 12px; }

.post-card {
  background: #fff; border: 1px solid var(--border);
  border-radius: var(--radius); padding: 16px;
  cursor: pointer; transition: var(--transition);
}
.post-card:hover { box-shadow: 0 2px 12px rgba(0,0,0,0.08); border-color: var(--primary); }

.post-card-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.post-district { font-size: 12px; color: var(--primary); font-weight: 600; background: #f0f4ff; padding: 2px 8px; border-radius: 20px; }
.post-price { font-size: 13px; font-weight: 700; color: var(--text); }

.post-title { font-size: 15px; font-weight: 700; color: var(--text); margin-bottom: 6px; }
.post-preview { font-size: 13px; color: var(--text-muted); line-height: 1.5; margin-bottom: 10px; }

.post-footer { display: flex; align-items: center; gap: 12px; }
.post-meta { font-size: 12px; color: var(--text-muted); }
.post-stat { display: flex; align-items: center; gap: 3px; font-size: 12px; color: var(--text-muted); margin-left: auto; }
.post-stat + .post-stat { margin-left: 8px; }

.empty-state { text-align: center; padding: 40px; color: var(--text-muted); font-size: 14px; }
.scroll-trigger { height: 1px; }
.loading-indicator { text-align: center; padding: 16px; font-size: 13px; color: var(--text-muted); }

.fab {
  position: fixed; bottom: 32px; right: 32px;
  width: 52px; height: 52px; border-radius: 50%;
  background: var(--primary); color: #fff;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 4px 16px rgba(0,0,0,0.2);
  transition: var(--transition); z-index: 100;
}
.fab:hover { transform: scale(1.08); box-shadow: 0 6px 20px rgba(0,0,0,0.25); }
</style>
