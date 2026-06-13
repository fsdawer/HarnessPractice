import api from './index'

export const notificationApi = {
  getList: () => api.get('/api/notifications'),
  markRead: (id) => api.patch(`/api/notifications/${id}/read`),
  markAllRead: () => api.patch('/api/notifications/read-all'),
  getUnreadCount: () => api.get('/api/notifications/unread-count'),
  ack: (messageId) => api.post('/api/notifications/ack', { messageId }),
}
