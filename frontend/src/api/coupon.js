import api from './index'

export const couponApi = {
  getMyCoupons: () => api.get('/api/coupons/me'),
  getStylistCoupons: () => api.get('/api/coupons/stylist'),
  createCoupon: (data) => api.post('/api/coupons', data),
  grantCoupon: (couponId, targetUserId) => api.post(`/api/coupons/${couponId}/grant/${targetUserId}`),
  useCoupon: (userCouponId) => api.post('/api/coupons/use', null, { params: { userCouponId } }),
}
