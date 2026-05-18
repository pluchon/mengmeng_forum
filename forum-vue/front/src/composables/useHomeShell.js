import { provide, inject } from 'vue'
import { useHome } from '@scripts/views/Home'

const HOME_SHELL_KEY = Symbol('homeShell')

export function provideHomeShellContext() {
  const ctx = useHome()
  provide(HOME_SHELL_KEY, ctx)
  return ctx
}

export function useHomeShellContext() {
  const ctx = inject(HOME_SHELL_KEY)
  if (!ctx) {
    throw new Error('useHomeShellContext() must be used within MainShellLayout')
  }
  return ctx
}
