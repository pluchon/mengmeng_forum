import { ElMessage } from 'element-plus'

/** 用户 state=1 表示禁言 */
export function isUserMuted(userOrState) {
  if (userOrState == null) return false
  if (typeof userOrState === 'object') {
    const s = userOrState.state != null ? userOrState.state : userOrState
    return Number(s) === 1
  }
  return Number(userOrState) === 1
}

export function warnUserMuted() {
  ElMessage.warning('您已被禁言，无法发表内容或私信，请联系管理员')
  return false
}

export function blockIfMuted(userStore) {
  if (isUserMuted(userStore)) {
    warnUserMuted()
    return true
  }
  return false
}
