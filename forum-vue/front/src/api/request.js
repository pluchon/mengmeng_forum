import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { useUserStore } from '../stores/user'
import { extractApiErrorMessage } from '@/api/httpError'
import { promptLogin } from '@/utils/loginPrompt'

// 后端的业务失败几乎都经 GlobalExceptionHandler 映射成 4xx，走的是错误拦截器，
// 而静默名单和"被禁言"的专属文案原本只写在成功拦截器里，等于从没生效过。
// 两条路径共用同一套规则
const GLOBAL_SILENT_BIZ_CODES = [1115, 1119, 1132]
const MUTED_BIZ_CODE = 1104

function notifyBusinessError(code, message, config, fallback) {
  if (code === MUTED_BIZ_CODE) {
    ElMessage.warning(message || '您已被禁言，无法发表内容，请联系管理员')
    return
  }
  const extra = config?.silentBizCodes
  const silent = [
    ...GLOBAL_SILENT_BIZ_CODES,
    ...(Array.isArray(extra) ? extra : []),
  ]
  if (code != null && silent.includes(code)) {
    return
  }
  ElMessage.error(message || fallback)
}

const request = axios.create({
  // 开发环境下使用代理，因此不需要配置写死的 base URL，由 vite.config 代理
  baseURL: '/', 
  timeout: 30000
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
    // 如果返回的是 Blob 比如文件下载 ，直接返回
    if (response.config.responseType === 'blob') {
      return res
    }
    // 自动从响应头提取 JWT Token 登录接口会返回
    const newToken = response.headers['authorization'] || response.headers['Authorization']
    if (newToken) {
      const userStore = useUserStore()
      userStore.login(newToken)
    }
    if (response.config?.url?.includes('/captcha/generate')) {
      return res
    }
    // 业务错误码处理
    if (res.code !== undefined && res.code !== 0) {
      notifyBusinessError(res.code, res.message, response.config, '操作失败，请稍后重试')
      return Promise.reject(res)
    }
    return res
  },
  async error => {
    if (error.config?.silentHttpError) {
      return Promise.reject(error)
    }
    if (error.response) {
      const status = error.response.status
      if (status === 401) {
        const userStore = useUserStore()
        await userStore.logout({ redirect: false })
        if (error.config?.publicAnonymousFallback && !error.config?._anonymousRetried) {
          error.config._anonymousRetried = true
          if (error.config.headers) {
            delete error.config.headers.Authorization
            delete error.config.headers.authorization
          }
          return request(error.config)
        }
        if (error.config?.publicAnonymousFallback && error.config?._anonymousRetried) {
          return Promise.reject(error)
        }
        await promptLogin()
      } else {
        const fallbackByStatus = {
          400: '填写的内容有误，请检查后重试',
          403: '你没有权限进行该操作',
          404: '内容不存在或已被删除',
          413: '文件太大了，请压缩后再上传',
          429: '操作太频繁了，请稍后再试',
          500: '服务开小差了，请稍后再试',
          502: '服务暂时不可用，请稍后再试',
          503: '服务正忙，请稍后再试',
          504: '服务响应有点慢，请稍后再试',
        }
        // 业务码可能走非 200（GlobalExceptionHandler.resolveStatus 默认 400），
        // silentBizCodes 在这条路径上也要生效，否则调用方自己渲染了状态还会多一个 toast
        const msg = extractApiErrorMessage(error, fallbackByStatus[status] || '操作没有成功，请稍后再试')
        notifyBusinessError(error.response?.data?.code, msg, error.config, msg)
      }
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('服务响应有点慢，请稍后再试')
    } else {
      ElMessage.error('网络好像不太稳定，请检查后重试')
    }
    return Promise.reject(error)
  }
)

export default request
