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

export function updatePasswordBySms(phoneNumber, code, newPassword, captchaTicket) {
  return request({
    url: '/user/findPasswordBySms',
    method: 'post',
    params: { phoneNumber, code, newPassword, captchaTicket },
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
  if (type === 'EMAIL' || (contact && contact.includes('@'))) {
    return request({
      url: '/user/findPasswordByMail',
      method: 'post',
      params: { email: contact, captchaTicket },
    })
  }
  return request({
    url: '/user/findPasswordBySms',
    method: 'post',
    params: { phoneNumber: contact, captchaTicket },
  })
}

export function sendMailCode(email) {
  return request({ url: '/mail/verifyAndBind', method: 'post', params: { email } })
}

export function sendSmsCode(phoneNumber) {
  return request({ url: '/sms/verifyAndBind', method: 'post', params: { phoneNumber } })
}

export function verifyAndBindEmail(email, code) {
  return request({ url: '/mail/verifyAndBind', method: 'post', params: { email, code } })
}

export function verifyAndBindPhone(phoneNumber, code) {
  return request({ url: '/sms/verifyAndBind', method: 'post', params: { phoneNumber, code } })
}

export function getLoginLogs(limit = 20) {
  return request({ url: '/user/loginLogs', method: 'get', params: { limit } })
}

export function getSecurityAssessment() {
  return request({ url: '/user/securityAssessment', method: 'get' })
}
