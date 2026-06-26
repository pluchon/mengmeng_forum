import { useMessageCenterUiStore } from '@/stores/messageCenterUi'

/** 从路由 query 打开消息中心（兼容 /messages?targetUserId=） */
export function openMessageCenterFromRoute(router, query) {
  const ui = useMessageCenterUiStore()
  const rawId = query?.targetUserId
  if (rawId != null && rawId !== '') {
    ui.open({
      userId: Number(rawId),
      nickname: typeof query.nickname === 'string' ? query.nickname : '',
      avatarUrl: typeof query.avatarUrl === 'string' ? query.avatarUrl : '',
    })
  } else {
    ui.open()
  }
  if (router.currentRoute.value.name === 'messages') {
    router.replace({ path: router.currentRoute.value.path, query: {} })
  }
}
