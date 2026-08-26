// 认证表单校验规则 与后端 RegexUtil 保持一致；后端为最终裁决

export const EMAIL_RE = /^[a-z0-9]+([._\-]*[a-z0-9])*@([a-z0-9]+[-a-z0-9]*[a-z0-9]+\.){1,63}[a-z0-9]+$/i
export const PHONE_RE = /^1[3-9]\d{9}$/
export const USERNAME_RE = /^[\u4e00-\u9fa5a-zA-Z0-9]{4,20}$/
export const NICKNAME_RE = /^[\u4e00-\u9fa5a-zA-Z0-9]{2,20}$/
export const PASSWORD_RE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[A-Za-z\d]{8,20}$/
export const SMS_CODE_RE = /^\d{4}$/
export const MAIL_CODE_RE = /^\d{6}$/

const DANGEROUS_INPUT_RE =
  /(<\s*script|<\/\s*script|javascript:|onerror\s*=|onload\s*=|union\s+select|insert\s+into|drop\s+table|delete\s+from|--|\/\*|\*\/|;\s*--)/i

export function containsDangerousInput(value) {
  return DANGEROUS_INPUT_RE.test(String(value || ''))
}

export function isValidEmail(value) {
  const text = String(value || '').trim()
  return !!text && !containsDangerousInput(text) && EMAIL_RE.test(text)
}

export function isValidPhone(value) {
  const text = String(value || '').trim()
  return !!text && !containsDangerousInput(text) && PHONE_RE.test(text)
}

export function isValidUserName(value) {
  const text = String(value || '').trim()
  return !!text && !containsDangerousInput(text) && USERNAME_RE.test(text)
}

export function isValidNickname(value) {
  const text = String(value || '').trim()
  return !!text && !containsDangerousInput(text) && NICKNAME_RE.test(text)
}

export function isValidPassword(value) {
  const text = String(value || '')
  return !!text && !containsDangerousInput(text) && PASSWORD_RE.test(text)
}

export function isValidSmsCode(value) {
  return SMS_CODE_RE.test(String(value || '').trim())
}

export function isValidMailCode(value) {
  return MAIL_CODE_RE.test(String(value || '').trim())
}

// 手机号输入：只保留数字并截断
export function digitsOnlyPhone(value, maxLen = 11) {
  return String(value || '').replace(/\D/g, '').slice(0, maxLen)
}

export const AUTH_MSG = {
  userName: '用户名须为 4~20 位中文、字母或数字',
  nickname: '昵称须为 2~20 位中文、字母或数字',
  password: '密码须为 8~20 位，且同时包含大小写字母与数字',
  phone: '手机号格式不正确',
  email: '邮箱格式不正确',
  smsCode: '验证码须为 4 位数字',
  mailCode: '验证码须为 6 位数字',
  dangerous: '输入包含非法内容',
}

// Element Plus 表单规则工厂
export function createAuthRules() {
  return {
    userName: [
      { required: true, message: '请输入用户名', trigger: 'blur' },
      {
        validator: (_rule, value, callback) => {
          if (containsDangerousInput(value)) {
            callback(new Error(AUTH_MSG.dangerous))
            return
          }
          if (!isValidUserName(value)) {
            callback(new Error(AUTH_MSG.userName))
            return
          }
          callback()
        },
        trigger: 'blur',
      },
    ],
    nickname: [
      { required: true, message: '请输入昵称', trigger: 'blur' },
      {
        validator: (_rule, value, callback) => {
          if (containsDangerousInput(value)) {
            callback(new Error(AUTH_MSG.dangerous))
            return
          }
          if (!isValidNickname(value)) {
            callback(new Error(AUTH_MSG.nickname))
            return
          }
          callback()
        },
        trigger: 'blur',
      },
    ],
    password: [
      { required: true, message: '请输入密码', trigger: 'blur' },
      {
        validator: (_rule, value, callback) => {
          if (!isValidPassword(value)) {
            callback(new Error(AUTH_MSG.password))
            return
          }
          callback()
        },
        trigger: 'blur',
      },
    ],
    phoneRequired: [
      { required: true, message: '请输入手机号', trigger: 'blur' },
      {
        validator: (_rule, value, callback) => {
          if (!isValidPhone(value)) {
            callback(new Error(AUTH_MSG.phone))
            return
          }
          callback()
        },
        trigger: 'blur',
      },
    ],
    phoneOptional: [
      {
        validator: (_rule, value, callback) => {
          const text = String(value || '').trim()
          if (!text) {
            callback()
            return
          }
          if (!isValidPhone(text)) {
            callback(new Error(AUTH_MSG.phone))
            return
          }
          callback()
        },
        trigger: 'blur',
      },
    ],
    emailRequired: [
      { required: true, message: '请输入邮箱', trigger: 'blur' },
      {
        validator: (_rule, value, callback) => {
          if (!isValidEmail(value)) {
            callback(new Error(AUTH_MSG.email))
            return
          }
          callback()
        },
        trigger: 'blur',
      },
    ],
    emailOptional: [
      {
        validator: (_rule, value, callback) => {
          const text = String(value || '').trim()
          if (!text) {
            callback()
            return
          }
          if (!isValidEmail(text)) {
            callback(new Error(AUTH_MSG.email))
            return
          }
          callback()
        },
        trigger: 'blur',
      },
    ],
    smsCode: [
      { required: true, message: '请输入验证码', trigger: 'blur' },
      {
        validator: (_rule, value, callback) => {
          if (!isValidSmsCode(value)) {
            callback(new Error(AUTH_MSG.smsCode))
            return
          }
          callback()
        },
        trigger: 'blur',
      },
    ],
    mailCode: [
      { required: true, message: '请输入验证码', trigger: 'blur' },
      {
        validator: (_rule, value, callback) => {
          if (!isValidMailCode(value)) {
            callback(new Error(AUTH_MSG.mailCode))
            return
          }
          callback()
        },
        trigger: 'blur',
      },
    ],
  }
}
