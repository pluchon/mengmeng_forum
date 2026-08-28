import request from './request'

export function updateUserInfo(data) {
  return request({ url: '/user/modifyUser', method: 'put', data })
}

export function submitProfileChange(data) {
  return request({ url: '/user/profile/change-request', method: 'post', data })
}

export function getProfileChangeStatus(fieldType) {
  return request({
    url: '/user/profile/change-request/status',
    method: 'get',
    params: { fieldType },
  })
}

// 上传头像文件后，将返回的 OSS URL 写入数据库 勿省略
export function updateAvatarUrl(url) {
  return request({
    url: '/user/updateAvatarUrl',
    method: 'post',
    params: { url },
  })
}

// 上传背景文件后落库
export function updateBackgroundUrl(url) {
  return request({
    url: '/user/updateBackgroundUrl',
    method: 'post',
    params: { url },
  })
}

export function updatePasswordByMail(email, code, newPassword, captchaTicket) {
  return request({
    url: '/user/findPasswordByMail',
    method: 'post',
    params: { email, code, newPassword, captchaTicket },
  })
}

// 设置页改密码走的是"当前账号已绑定的手机号"，号码由后端从会话里取，
// 前端手上只有掩码串，不该也不能把它当号码传上去
export function updatePasswordBySms(code, newPassword, captchaTicket) {
  return request({
    url: '/user/findPasswordBySms',
    method: 'post',
    params: { useBoundPhone: true, code, newPassword, captchaTicket },
  })
}

export function uploadAvatar(formData, { onUploadProgress } = {}) {
  return request({
    url: '/file/uploadAvatar',
    method: 'post',
    data: formData,
    onUploadProgress,
  })
}

export function uploadProfileBackground(formData, { onUploadProgress } = {}) {
  return request({
    url: '/file/uploadBackground',
    method: 'post',
    data: formData,
    onUploadProgress,
  })
}

export function sendUpdatePwdCode(contact, type, captchaTicket) {
  if (type === 'EMAIL') {
    return request({
      url: '/user/findPasswordByMail',
      method: 'post',
      params: { email: contact, captchaTicket },
    })
  }
  return request({
    url: '/user/findPasswordBySms',
    method: 'post',
    params: { useBoundPhone: true, captchaTicket },
  })
}

export function sendMailCode(email) {
  return request({ url: '/mail/verifyAndBind', method: 'post', params: { email } })
}

export function sendSmsCode(phoneNumber) {
  return request({ url: '/sms/verifyAndBind', method: 'post', params: { phoneNumber } })
}

// currentPassword 只有"改绑"（账号原本已绑过）时后端才要求，首次绑定可以不传
export function verifyAndBindEmail(email, code, currentPassword) {
  return request({ url: '/mail/verifyAndBind', method: 'post', params: { email, code, currentPassword } })
}

export function verifyAndBindPhone(phoneNumber, code, currentPassword) {
  return request({ url: '/sms/verifyAndBind', method: 'post', params: { phoneNumber, code, currentPassword } })
}

// 凭当前密码直接改密码，不走验证码，手机停机 / 邮箱登不上时也能自助改
export function changePasswordByCurrent(currentPassword, newPassword) {
  return request({
    url: '/user/changePassword',
    method: 'post',
    data: { currentPassword, newPassword },
  })
}

export function getLoginLogs(limit = 20) {
  return request({ url: '/user/loginLogs', method: 'get', params: { limit } })
}

export function getSecurityAssessment() {
  return request({ url: '/user/securityAssessment', method: 'get' })
}
