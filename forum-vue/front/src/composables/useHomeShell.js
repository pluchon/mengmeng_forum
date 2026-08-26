import { provide, inject } from 'vue'
import { useHome } from '@scripts/views/Home'

// 使用全局 Symbol，避免开发环境热更新后布局与已缓存信息流持有不同注入键
const HOME_SHELL_KEY = Symbol.for('luntan.homeShell')
let latestHomeShellContext = null

export function provideHomeShellContext() {
  const ctx = useHome()
  latestHomeShellContext = ctx
  provide(HOME_SHELL_KEY, ctx)
  return ctx
}

export function useHomeShellContext() {
  const ctx = inject(HOME_SHELL_KEY, latestHomeShellContext)
  if (!ctx) {
    throw new Error('useHomeShellContext() must be used within MainShellLayout')
  }
  return ctx
}
