import axios from 'axios'
import { ElMessage } from 'element-plus'
import { store } from './store'
import router from './router'

const api = axios.create({ baseURL: '/api', timeout: 30000 })

api.interceptors.request.use(config => {
  if (store.token) config.headers['X-Auth-Token'] = store.token
  return config
})

api.interceptors.response.use(
  resp => resp,
  err => {
    const status = err.response?.status
    const msg = err.response?.data?.error || err.message || '请求失败'
    if (status === 401) {
      store.logout()
      if (router.currentRoute.value.path !== '/login') {
        ElMessage.warning('登录已失效，请重新登录')
        router.push('/login')
      }
    } else {
      ElMessage.error(msg)
    }
    return Promise.reject(err)
  }
)

export default api
