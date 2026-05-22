import api from './index'

export const communityApi = {
  getPosts: (lastId) => api.get('/api/community', { params: { lastId } }),
  getLocalPosts: (lastId) => api.get('/api/community/local', { params: { lastId } }),
  getPost: (id) => api.get(`/api/community/${id}`),
  createPost: (data) => api.post('/api/community', data),
  deletePost: (id) => api.delete(`/api/community/${id}`),
  toggleLike: (id) => api.post(`/api/community/${id}/like`),
  getComments: (postId) => api.get(`/api/posts/${postId}/comments`),
  addComment: (postId, content) => api.post(`/api/posts/${postId}/comments`, { content }),
  deleteComment: (postId, commentId) => api.delete(`/api/posts/${postId}/comments/${commentId}`),
}
