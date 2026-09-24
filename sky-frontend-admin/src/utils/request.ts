import axios, { AxiosResponse } from 'axios'
import { getToken } from './cookies'

// Redirect to login (hash router) — mirrors the Vue app's router.push('/login')
function goLogin() {
  if (window.location.hash !== '#/login') {
    window.location.hash = '#/login'
  }
}

const service = axios.create({
  // nginx proxies /api/ -> backend /admin/ ; dev proxy mirrors this.
  baseURL: '/api',
  timeout: 600000,
})

// Request interceptor: attach the admin token as header `token`
service.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers = config.headers || {}
      ;(config.headers as any)['token'] = token
    }
    return config
  },
  (error) => Promise.reject(error),
)

// Response interceptor: 401 -> login. Envelope success is code===1 (handled by callers).
service.interceptors.response.use(
  (response: AxiosResponse) => {
    if (response.data && response.data.status === 401) {
      goLogin()
    }
    return response
  },
  (error) => {
    if (error && error.response) {
      if (error.response.status === 401) {
        goLogin()
      } else if (error.response.status === 405) {
        error.message = 'Request error'
      }
    }
    return Promise.reject(error)
  },
)

export default service
