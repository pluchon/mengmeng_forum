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

// 已经报过的 URL 不再重复刷屏；只观测不干预，不影响任何既有行为
const noCodeSeen = new Set()

function warnIfNoBizCode(res, url) {
  if (res !== null && typeof res === 'object' && res.code !== undefined) {
    return
  }
  const key = String(url || '(unknown)').split('?')[0]
  if (noCodeSeen.has(key)) {
    return
  }
  noCodeSeen.add(key)
  console.warn(
    '[code-invariant] 该接口的响应不带 code 字段，拦截器无法拦截它的业务失败，' +
      '调用处的 catch / code 判断在这条链路上是活的，不可当作死代码删除：',
    key,
  )
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
    // 走到这里 code 只可能是 0 或不存在。组件里大量 catch 与错误分支都建立在
    // 「业务失败到不了组件」这条不变量上——不带 code 的响应会绕过上面的 reject，
    // 让那条不变量在这条链路上失效。真出现了必须知道，所以按 URL 去重报一次
    warnIfNoBizCode(res, response.config?.url)
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
