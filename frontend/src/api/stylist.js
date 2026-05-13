import api from './index'

export const stylistApi = {
  /** GET /api/stylists — 스타일리스트 목록 (keyword, location 검색) */
  getStylists: (params) => api.get('/api/stylists', { params }),

  /** GET /api/stylists/{id} — 스타일리스트 상세 조회 */
  getStylist: (id) => api.get(`/api/stylists/${id}`),

  /** GET /api/stylists/me — 내 프로필 조회 (미용사) */
  getMyProfile: () => api.get('/api/stylists/me'),

  /** PUT /api/stylists/me — 내 프로필 수정 */
  updateProfile: (data) => api.put('/api/stylists/me', data),

  // ── 서비스 관리 ──────────────────────────────────────────────

  /** POST /api/stylists/me/services — 서비스 추가 */
  addService: (data) => api.post('/api/stylists/me/services', data),

  /** PUT /api/stylists/me/services/{id} — 서비스 수정 */
  updateService: (serviceId, data) => api.put(`/api/stylists/me/services/${serviceId}`, data),

  /** DELETE /api/stylists/me/services/{id} — 서비스 삭제 */
  deleteService: (serviceId) => api.delete(`/api/stylists/me/services/${serviceId}`),

  // ── 영업시간 관리 ─────────────────────────────────────────────

  /** PUT /api/stylists/me/hours — 영업시간 수정 (요일 하나 단위) */
  updateHours: (data) => api.put('/api/stylists/me/hours', data),

  /** GET /api/stylists/nearby — 위치 기반 주변 미용사 조회 */
  getNearbyStylists: (lat, lng, radius = 3000) =>
    api.get('/api/stylists/nearby', { params: { lat, lng, radius } }),


  /** GET /api/stylists/dashboard — 미용사 대시보드 (예약/매출/리뷰 요약 + 최근 5건) */
  getDashboard: () => api.get('/api/stylists/dashboard'),

  /** GET /api/stylist/dashboard/stats — 매출 통계 조회 */
  getDashboardStats: (startDate, endDate) =>
    api.get('/api/stylist/dashboard/stats', { params: { startDate, endDate } }),
}
