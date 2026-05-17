<template>
  <div class="post-page">
    <div class="container">
      <button class="back-btn" @click="$router.back()">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
        목록으로
      </button>

      <div v-if="loading" class="loading">불러오는 중...</div>

      <template v-else-if="post">
        <div class="post-header">
          <div class="post-tags">
            <span class="district-tag">{{ post.district }}</span>
            <span v-if="post.price != null" class="price-tag">{{ post.price.toLocaleString() }}원</span>
          </div>
          <h1 class="post-title">{{ post.title }}</h1>
          <div class="post-meta-row">
            <span class="author">{{ post.authorName }}</span>
            <span class="dot">·</span>
            <span class="date">{{ formatDate(post.createdAt) }}</span>
            <span class="dot">·</span>
            <span class="views">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
              {{ post.viewCount }}
            </span>
          </div>
        </div>

        <!-- 연결된 예약 요약 -->
        <div v-if="post.reservationSummary" class="reservation-card">
          <div class="res-card-label">연결된 예약</div>
          <div class="res-card-body">
            <span class="res-stylist">{{ post.reservationSummary.stylistName }}</span>
            <span class="res-sep">·</span>
            <span class="res-service">{{ post.reservationSummary.serviceName }}</span>
            <span class="res-sep">·</span>
            <span class="res-date">{{ formatDate(post.reservationSummary.reservedAt) }}</span>
          </div>
        </div>

        <!-- 본문 -->
        <div class="post-content">{{ post.content }}</div>

        <!-- 하단 액션 -->
        <div class="post-actions">
          <button
            class="like-btn"
            :class="{ liked: post.likedByMe }"
            @click="handleLike"
            :disabled="!auth.isLoggedIn"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" :fill="post.likedByMe ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>
            {{ post.likeCount }}
          </button>

          <button
            v-if="auth.user?.id === post.authorId"
            class="delete-btn"
            @click="handleDelete"
          >삭제</button>
        </div>
      </template>

      <div v-else class="error-state">게시글을 찾을 수 없습니다.</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { communityApi } from '@/api/community'
import { useAuthStore } from '@/stores/authStore'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const post = ref(null)
const loading = ref(true)

onMounted(async () => {
  try {
    const res = await communityApi.getPost(route.params.id)
    post.value = res.data
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
})

async function handleLike() {
  if (!auth.isLoggedIn) return
  try {
    await communityApi.toggleLike(post.value.id)
    if (post.value.likedByMe) {
      post.value.likedByMe = false
      post.value.likeCount -= 1
    } else {
      post.value.likedByMe = true
      post.value.likeCount += 1
    }
  } catch (e) {
    console.error(e)
  }
}

async function handleDelete() {
  if (!confirm('정말 삭제하시겠습니까?')) return
  try {
    await communityApi.deletePost(post.value.id)
    router.push('/community')
  } catch (e) {
    alert('삭제에 실패했습니다.')
  }
}

function formatDate(dateStr) {
  const d = new Date(dateStr)
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`
}
</script>

<style scoped>
.post-page { padding: 24px 0 60px; min-height: 80vh; }

.back-btn {
  display: inline-flex; align-items: center; gap: 6px;
  background: none; border: none; cursor: pointer;
  font-size: 14px; color: var(--text-muted); padding: 0;
  margin-bottom: 20px; transition: var(--transition);
}
.back-btn:hover { color: var(--text); }

.loading, .error-state {
  text-align: center; padding: 60px; color: var(--text-muted); font-size: 14px;
}

.post-header { margin-bottom: 20px; }
.post-tags { display: flex; gap: 8px; margin-bottom: 10px; }
.district-tag { font-size: 12px; color: var(--primary); font-weight: 600; background: #f0f4ff; padding: 3px 10px; border-radius: 20px; }
.price-tag { font-size: 13px; font-weight: 700; color: var(--text); background: #f5f5f5; padding: 3px 10px; border-radius: 20px; }

.post-title { font-size: 22px; font-weight: 700; color: var(--text); margin-bottom: 10px; line-height: 1.4; }

.post-meta-row { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--text-muted); }
.dot { color: var(--border); }
.views { display: flex; align-items: center; gap: 3px; }

.reservation-card {
  background: #f8f9ff; border: 1px solid #dce4ff;
  border-radius: var(--radius); padding: 14px 16px; margin-bottom: 20px;
}
.res-card-label { font-size: 11px; font-weight: 700; color: var(--primary); text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 6px; }
.res-card-body { display: flex; align-items: center; gap: 6px; font-size: 14px; color: var(--text-sub); flex-wrap: wrap; }
.res-stylist { font-weight: 700; color: var(--text); }
.res-sep { color: var(--border); }

.post-content {
  font-size: 15px; line-height: 1.8; color: var(--text);
  white-space: pre-wrap; min-height: 120px; margin-bottom: 32px;
  padding: 20px 0; border-top: 1px solid var(--border); border-bottom: 1px solid var(--border);
}

.post-actions { display: flex; align-items: center; gap: 12px; padding-top: 16px; }

.like-btn {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 8px 18px; border: 1px solid var(--border);
  border-radius: 20px; background: none; cursor: pointer;
  font-size: 14px; font-weight: 600; color: var(--text-muted);
  transition: var(--transition);
}
.like-btn:hover { border-color: #e45d5d; color: #e45d5d; }
.like-btn.liked { color: #e45d5d; border-color: #e45d5d; background: #fff0f0; }
.like-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.delete-btn {
  margin-left: auto; padding: 8px 16px;
  background: none; border: 1px solid var(--danger);
  border-radius: var(--radius-sm); color: var(--danger);
  font-size: 13px; cursor: pointer; transition: var(--transition);
}
.delete-btn:hover { background: var(--danger); color: #fff; }
</style>
