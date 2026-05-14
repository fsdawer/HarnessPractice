import api from './index'

export const hairApi = {
  analyze: (faceFile, styleFile) => {
    const form = new FormData()
    form.append('face', faceFile)
    form.append('style', styleFile)
    return api.post('/api/hair-analysis', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}
