<template>
  <div class="hair-analysis">
    <div class="page-header">
      <h1>AI 헤어스타일 궁합 분석</h1>
      <p class="subtitle">내 얼굴 사진과 원하는 헤어 사진을 올리면 AI가 궁합을 분석해드립니다.</p>
    </div>

    <div class="upload-row">
      <!-- 얼굴 사진 -->
      <div
        class="upload-zone"
        :class="{ 'has-image': facePreview }"
        @click="!facePreview && faceInput.click()"
      >
        <input ref="faceInput" type="file" accept="image/*" hidden @change="onFacePick" />
        <template v-if="!facePreview">
          <div class="upload-placeholder">
            <span class="upload-icon">🤳</span>
            <p class="upload-label">내 얼굴 사진</p>
            <p class="upload-hint">정면 얼굴이 잘 보이는 사진</p>
          </div>
        </template>
        <template v-else>
          <img :src="facePreview" class="preview-img" alt="얼굴 사진" />
          <button class="change-btn" @click.stop="faceInput.click()">변경</button>
          <p class="preview-label">내 얼굴 사진</p>
        </template>
      </div>

      <div class="upload-arrow">→</div>

      <!-- 레퍼런스 헤어 사진 -->
      <div
        class="upload-zone"
        :class="{ 'has-image': stylePreview }"
        @click="!stylePreview && styleInput.click()"
      >
        <input ref="styleInput" type="file" accept="image/*" hidden @change="onStylePick" />
        <template v-if="!stylePreview">
          <div class="upload-placeholder">
            <span class="upload-icon">💇</span>
            <p class="upload-label">원하는 헤어 사진</p>
            <p class="upload-hint">잡지, 인스타 등 레퍼런스 사진</p>
          </div>
        </template>
        <template v-else>
          <img :src="stylePreview" class="preview-img" alt="헤어 사진" />
          <button class="change-btn" @click.stop="styleInput.click()">변경</button>
          <p class="preview-label">원하는 헤어 사진</p>
        </template>
      </div>
    </div>

    <button class="analyze-btn" :disabled="!faceFile || !styleFile || loading" @click="analyze">
      <span v-if="loading" class="spinner" />
      {{ loading ? '분석 중...' : 'AI 궁합 분석 시작' }}
    </button>

    <div v-if="error" class="error-box">{{ error }}</div>

    <!-- 결과 -->
    <div v-if="result" class="result-section">

      <!-- 궁합 스코어 -->
      <div class="compat-card" :class="verdictClass">
        <div class="compat-header">
          <div>
            <p class="compat-label">레퍼런스 스타일</p>
            <p class="compat-style">{{ result.referenceStyle }}</p>
          </div>
          <div class="compat-score-wrap">
            <svg class="score-ring" viewBox="0 0 64 64">
              <circle cx="32" cy="32" r="28" fill="none" stroke="#e8e0f8" stroke-width="6" />
              <circle
                cx="32" cy="32" r="28" fill="none"
                :stroke="scoreColor" stroke-width="6"
                stroke-linecap="round"
                :stroke-dasharray="`${(result.compatibility.score / 100) * 175.9} 175.9`"
                stroke-dashoffset="44"
                transform="rotate(-90 32 32)"
              />
            </svg>
            <span class="score-num">{{ result.compatibility.score }}</span>
          </div>
        </div>
        <p class="compat-verdict">{{ result.compatibility.verdict }}</p>
        <p class="compat-reason">{{ result.compatibility.reason }}</p>
      </div>

      <!-- 얼굴형 -->
      <div class="face-shape-card">
        <h2>얼굴형 분석</h2>
        <div class="face-shape-badge">{{ result.faceShape }}</div>
        <ul class="face-features">
          <li v-for="f in result.faceFeatures" :key="f">{{ f }}</li>
        </ul>
      </div>

      <!-- 추천 -->
      <div class="recommendations">
        <h2>이 얼굴형에 어울리는 추천 스타일</h2>
        <div class="rec-grid">
          <div v-for="rec in result.recommendations" :key="rec.style" class="rec-card">
            <div class="rec-header">
              <span class="rec-style">{{ rec.style }}</span>
              <span class="rec-category">{{ rec.category }}</span>
            </div>
            <div class="suitability-bar">
              <div class="bar-fill" :style="{ width: rec.suitability + '%' }" />
              <span class="bar-label">{{ rec.suitability }}%</span>
            </div>
            <p class="rec-reason">{{ rec.reason }}</p>
          </div>
        </div>
      </div>

      <!-- 팁 -->
      <div class="styling-tips">
        <h2>스타일링 팁</h2>
        <p>{{ result.stylingTips }}</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { hairApi } from '@/api/hair'

const faceInput = ref(null)
const styleInput = ref(null)
const faceFile = ref(null)
const styleFile = ref(null)
const facePreview = ref(null)
const stylePreview = ref(null)
const loading = ref(false)
const error = ref(null)
const result = ref(null)

function onFacePick(e) {
  const file = e.target.files[0]
  if (!file) return
  faceFile.value = file
  facePreview.value = URL.createObjectURL(file)
  result.value = null
  error.value = null
}

function onStylePick(e) {
  const file = e.target.files[0]
  if (!file) return
  styleFile.value = file
  stylePreview.value = URL.createObjectURL(file)
  result.value = null
  error.value = null
}

const verdictClass = computed(() => {
  if (!result.value) return ''
  const s = result.value.compatibility.score
  if (s >= 75) return 'compat--good'
  if (s >= 50) return 'compat--ok'
  return 'compat--bad'
})

const scoreColor = computed(() => {
  if (!result.value) return '#a97cf8'
  const s = result.value.compatibility.score
  if (s >= 75) return '#6bcb77'
  if (s >= 50) return '#f4a261'
  return '#e76f51'
})

async function analyze() {
  loading.value = true
  error.value = null
  result.value = null
  try {
    const { data } = await hairApi.analyze(faceFile.value, styleFile.value)
    result.value = data
  } catch (e) {
    error.value = e.response?.data?.message ?? 'AI 분석에 실패했습니다. 잠시 후 다시 시도해주세요.'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.hair-analysis {
  max-width: 760px;
  margin: 0 auto;
  padding: 32px 16px 64px;
}

.page-header { text-align: center; margin-bottom: 32px; }
.page-header h1 { font-size: 1.8rem; font-weight: 700; margin-bottom: 8px; }
.subtitle { color: #666; font-size: 0.95rem; }

.upload-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.upload-arrow { font-size: 1.6rem; color: #a97cf8; flex-shrink: 0; }

.upload-zone {
  flex: 1;
  border: 2px dashed #d0d0d0;
  border-radius: 16px;
  min-height: 220px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
  position: relative;
  overflow: hidden;
}
.upload-zone:hover { border-color: #a97cf8; background: #f8f3ff; }
.upload-zone.has-image { border-style: solid; cursor: default; }

.upload-placeholder { text-align: center; color: #999; padding: 16px; }
.upload-icon { font-size: 2.4rem; display: block; margin-bottom: 10px; }
.upload-label { font-size: 0.9rem; font-weight: 600; color: #555; margin-bottom: 4px; }
.upload-hint { font-size: 0.78rem; }

.preview-img { width: 100%; height: 220px; object-fit: cover; display: block; }
.preview-label {
  position: absolute; bottom: 0; left: 0; right: 0;
  background: rgba(0,0,0,0.45); color: #fff;
  font-size: 0.8rem; font-weight: 600; text-align: center; padding: 6px;
}
.change-btn {
  position: absolute; top: 8px; right: 8px;
  background: rgba(0,0,0,0.55); color: #fff;
  border: none; border-radius: 8px; padding: 4px 12px; font-size: 0.8rem; cursor: pointer;
}

.analyze-btn {
  display: flex; align-items: center; justify-content: center; gap: 8px;
  width: 100%; margin-top: 16px; padding: 14px;
  background: linear-gradient(135deg, #a97cf8, #7c5cef);
  color: #fff; border: none; border-radius: 12px;
  font-size: 1rem; font-weight: 600; cursor: pointer; transition: opacity 0.2s;
}
.analyze-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.spinner {
  width: 16px; height: 16px;
  border: 2px solid rgba(255,255,255,0.4); border-top-color: #fff;
  border-radius: 50%; animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.error-box {
  margin-top: 16px; padding: 14px;
  background: #fff0f0; border-radius: 10px;
  color: #c0392b; font-size: 0.9rem; text-align: center;
}

.result-section { margin-top: 40px; display: flex; flex-direction: column; gap: 28px; }
.result-section h2 { font-size: 1.1rem; font-weight: 700; margin-bottom: 14px; color: #333; }

.compat-card {
  border-radius: 18px; padding: 24px;
  border: 2px solid #e8e0f8; background: #faf7ff;
}
.compat--good { border-color: #6bcb77; background: #f0faf1; }
.compat--ok   { border-color: #f4a261; background: #fffaf5; }
.compat--bad  { border-color: #e76f51; background: #fff5f3; }

.compat-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.compat-label { font-size: 0.78rem; color: #888; margin-bottom: 4px; }
.compat-style { font-size: 1.1rem; font-weight: 700; color: #333; }

.compat-score-wrap { position: relative; width: 64px; height: 64px; flex-shrink: 0; }
.score-ring { width: 64px; height: 64px; }
.score-num {
  position: absolute; inset: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 1rem; font-weight: 800; color: #333;
}

.compat-verdict { font-size: 1rem; font-weight: 700; color: #444; margin-bottom: 8px; }
.compat-reason { font-size: 0.88rem; color: #555; line-height: 1.6; }

.face-shape-card { background: #f8f3ff; border-radius: 16px; padding: 22px; }
.face-shape-badge {
  display: inline-block;
  background: linear-gradient(135deg, #a97cf8, #7c5cef);
  color: #fff; padding: 5px 18px; border-radius: 999px;
  font-size: 1rem; font-weight: 700; margin-bottom: 14px;
}
.face-features { list-style: none; padding: 0; display: flex; flex-wrap: wrap; gap: 8px; }
.face-features li {
  background: #fff; border: 1px solid #d9c8fc; border-radius: 8px;
  padding: 4px 12px; font-size: 0.85rem; color: #5a3eaa;
}

.rec-grid { display: flex; flex-direction: column; gap: 14px; }
.rec-card { background: #fff; border: 1px solid #e8e0f8; border-radius: 14px; padding: 18px; }
.rec-header { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.rec-style { font-size: 0.95rem; font-weight: 700; color: #333; }
.rec-category {
  font-size: 0.75rem; background: #ede8fc; color: #6b4cc8;
  padding: 2px 10px; border-radius: 999px;
}
.suitability-bar {
  position: relative; height: 7px; background: #f0ecfc;
  border-radius: 999px; margin-bottom: 20px;
}
.bar-fill {
  height: 100%; background: linear-gradient(to right, #a97cf8, #7c5cef);
  border-radius: 999px; transition: width 0.6s ease;
}
.bar-label {
  position: absolute; right: 0; top: -18px;
  font-size: 0.75rem; color: #7c5cef; font-weight: 600;
}
.rec-reason { font-size: 0.86rem; color: #555; line-height: 1.5; }

.styling-tips {
  background: #fffbf0; border: 1px solid #ffe8a0; border-radius: 16px; padding: 22px;
}
.styling-tips p { font-size: 0.9rem; color: #555; line-height: 1.65; }

@media (max-width: 560px) {
  .upload-row { flex-direction: column; }
  .upload-arrow { transform: rotate(90deg); }
}
</style>
