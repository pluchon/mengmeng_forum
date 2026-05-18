<template>
  <div class="auth-page">
    <div class="auth-container animate-fade-up">
      <div class="auth-card">
        <div class="auth-layout">
          <div class="auth-form-side">
            <div class="form-header-block">
              <h1 class="site-title">找回密码</h1>
              <p class="site-tagline">安全验证后即可重置您的登录密码</p>
              <el-divider class="site-divider" />
            </div>

            <div class="method-selector-wrap">
              <el-radio-group v-model="form.type" class="method-selector" size="large">
                <el-radio-button label="EMAIL">邮箱找回</el-radio-button>
                <el-radio-button label="PHONE">手机找回</el-radio-button>
              </el-radio-group>
            </div>

            <div class="input-item mt-20">
              <el-input
                v-model="form.account"
                :placeholder="form.type === 'PHONE' ? '请输入手机号' : '请输入常用邮箱'"
              />
            </div>

            <div class="input-item mt-20">
              <el-input
                v-model="form.code"
                :placeholder="form.type === 'PHONE' ? '4 位验证码' : '6 位验证码'"
                :maxlength="form.type === 'PHONE' ? 4 : 6"
              />
              <el-button
                class="code-btn"
                :disabled="countdown > 0"
                :loading="sendingCode"
                @click="handleSendCode"
              >
                {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
              </el-button>
            </div>

            <div class="input-item mt-20">
              <el-input
                v-model="form.newPassword"
                type="password"
                placeholder="设置新密码（6–12 位）"
                show-password
              />
            </div>

            <el-button
              type="primary"
              class="flat-submit-btn"
              :loading="loading"
              @click="handleSubmit"
            >
              重置密码并登录
            </el-button>

            <div class="footer-links">
              <el-button link class="back-link" @click="$router.push('/sign-in')">
                <el-icon><ArrowLeft /></el-icon>
                返回登录
              </el-button>
            </div>
          </div>

          <div class="auth-brand-side">
            <div class="image-mask" />
          </div>
        </div>
      </div>
    </div>
    <BehaviorCaptchaDialog ref="captchaDialogRef" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useForgotPassword } from '@scripts/views/ForgotPassword'
import BehaviorCaptchaDialog from '@/components/captcha/BehaviorCaptchaDialog.vue'

const captchaDialogRef = ref()

const {
  ArrowLeft,
  countdown,
  form,
  handleSendCode,
  handleSubmit,
  loading,
  sendingCode,
} = useForgotPassword(captchaDialogRef)
</script>

<style scoped src="@/assets/styles/forgot.css"></style>
