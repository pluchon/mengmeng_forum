/// <reference types="vite/client" />

/** 声明环境变量的类型 */
interface ImportMetaEnv {
  /** 开发服务器端口，默认 vite.config 内为 9527 */
  readonly VITE_DEV_PORT?: string
  readonly VITE_API_PREFIX: string
  readonly VITE_API_BASE_URL: string
  readonly VITE_BASE: string
  /** 用户端登录页完整 URL，管理后台登录页「回到用户端」跳转用 */
  readonly VITE_FRONT_SIGN_IN_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
