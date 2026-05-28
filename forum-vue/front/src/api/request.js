import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { useUserStore } from '../stores/user'
import { extractApiErrorMessage } from '@/api/httpError'

const request = axios.create({
  // 开发环境下使用代理，因此不需要配置写死的 base URL，由 vite.config 代理
  baseURL: '/', 
  timeout: 30000 // 增加到 30 秒，防止邮件发送耗时导致误报 500/超时
})

// 请求拦截器
request.interceptors.request.use(
  config => {
    // 从 Pinia Store 获取 token 并注入请求头
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers['Authorization'] = userStore.token
    }
    return config
  },
  error => Promise.reject(error)
)

// 响应拦截器
request.interceptors.response.use(
  response => {
    const res = response.data
    // 如果返回的是 Blob（比如文件下载），直接返回
    if (response.config.responseType === 'blob') {
      return res
    }
    // 自动从响应头提取 JWT Token（登录接口会返回）
    const newToken = response.headers['authorization'] || response.headers['Authorization']
    if (newToken) {
      const userStore = useUserStore()
      userStore.token = newToken
      // 登录成功后拉取用户信息
      userStore.fetchUserInfo()
    }
    if (response.config?.url?.includes('/captcha/generate')) {
      return res
    }
    // 业务错误码处理
    if (res.code !== undefined && res.code !== 0) {
      const extraSilent = response.config?.silentBizCodes
      const silentBusinessCodes = [
        1115,
        1119,
        1132,
        ...(Array.isArray(extraSilent) ? extraSilent : []),
      ]
      if (res.code === 1104) {
        ElMessage.warning(res.message || '您已被禁言，无法发表内容，请联系管理员')
      } else if (!silentBusinessCodes.includes(res.code)) {
        ElMessage.error(res.message || '系统错误')
      }
      return Promise.reject(res)
    }
    return res
  },
  error => {
    if (error.response) {
      const status = error.response.status
      if (status === 401) {
        const userStore = useUserStore()
        userStore.logout() // 包含 router.push('/sign-in')
      } else {
        const msg = extractApiErrorMessage(error, `请求失败（${status}）`)
        ElMessage.error(msg)
      }
    } else if (error.code !== undefined && typeof error.message === 'string') {
      ElMessage.error(error.message || '操作失败')
    } else {
      ElMessage.error('网络错误或服务器无响应，请稍后再试')
    }
    return Promise.reject(error)
  }
)

export default request
