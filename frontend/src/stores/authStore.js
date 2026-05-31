import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'

/** JWT payload의 exp(만료 시각, 초 단위)를 검사해 만료 여부 반환 */
function isTokenExpired(jwt) {
  try {
    const payload = JSON.parse(atob(jwt.split('.')[1]))
    return payload.exp * 1000 < Date.now()
  } catch {
    return true // 파싱 실패 = 유효하지 않은 토큰
  }
}

/** localStorage에서 토큰을 읽되, 만료됐으면 즉시 삭제하고 null 반환 */
function loadToken() {
  const raw = localStorage.getItem('token')
  if (!raw) return null
  if (isTokenExpired(raw)) {
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('user')
    return null
  }
  return raw
}

export const useAuthStore = defineStore('auth', () => {
  const token        = ref(loadToken())
  const user         = ref(token.value ? JSON.parse(localStorage.getItem('user') || 'null') : null)
  const refreshToken = ref(token.value ? localStorage.getItem('refreshToken') : null)

  const isLoggedIn = computed(() => !!token.value)
  const isStylist  = computed(() => user.value?.role === 'STYLIST')

  function setAuth(userData, accessToken, newRefreshToken = null) {
    user.value  = userData
    token.value = accessToken
    localStorage.setItem('user',  JSON.stringify(userData))
    localStorage.setItem('token', accessToken)

    if (newRefreshToken) {
      refreshToken.value = newRefreshToken
      localStorage.setItem('refreshToken', newRefreshToken)
    }
  }

  function updateTokens(newAccessToken, newRefreshToken) {
    token.value        = newAccessToken
    refreshToken.value = newRefreshToken
    localStorage.setItem('token',        newAccessToken)
    localStorage.setItem('refreshToken', newRefreshToken)
  }

  async function logout() {
    try {
      if (token.value) await authApi.logout()
    } catch (_) {}
    finally {
      user.value         = null
      token.value        = null
      refreshToken.value = null
      localStorage.removeItem('user')
      localStorage.removeItem('token')
      localStorage.removeItem('refreshToken')
    }
  }

  return { user, token, refreshToken, isLoggedIn, isStylist, setAuth, updateTokens, logout }
})
