<template>
  <div class="auth-page">
    <div class="auth-container animate-fade-up">
      <div class="auth-card">
        <div class="auth-layout">
          <div class="auth-form-side">
            <div class="form-header-block">
              <h1 class="site-title">加入萌萌论坛</h1>
              <p class="site-tagline">开启您的全新社交与知识共享体验</p>
              <el-divider class="site-divider" />
            </div>

            <el-form ref="formRef" :model="regForm" :rules="rules" label-position="top">
              <div class="form-grid">
                <el-form-item label="用户名" prop="userName" class="flat-form-item">
                  <el-input v-model="regForm.userName" placeholder="登录唯一账号" />
                </el-form-item>

                <el-form-item label="显示昵称" prop="nickname" class="flat-form-item">
                  <el-input v-model="regForm.nickname" placeholder="大家如何称呼你" />
                </el-form-item>
              </div>

              <el-form-item label="设置密码" prop="password" class="flat-form-item">
                <el-input
                  v-model="regForm.password"
                  type="password"
                  placeholder="6–12 位数字或字母"
                  show-password
                />
              </el-form-item>

              <div class="form-grid">
                <el-form-item label="手机号码（选填）" prop="phoneNum" class="flat-form-item">
                  <el-input v-model="regForm.phoneNum" placeholder="用于找回密码" />
                </el-form-item>

                <el-form-item label="电子邮箱（选填）" prop="email" class="flat-form-item">
                  <el-input v-model="regForm.email" placeholder="常用邮箱" />
                </el-form-item>
              </div>
            </el-form>

            <div class="action-section">
              <div class="form-policy">
                <el-checkbox v-model="agreed">
                  我已阅读并同意
                  <span class="link" @click="$router.push('/terms')">用户协议</span>
                  与
                  <span class="link" @click="$router.push('/privacy')">隐私政策</span>
                </el-checkbox>
              </div>

              <div class="submit-bar">
                <el-button type="primary" class="flat-submit-btn" :loading="loading" @click="handleSignUp">
                  立即创建账号
                </el-button>

                <el-button class="login-link-btn login-link-btn--text" @click="$router.push('/sign-in')">
                  直接登录
                </el-button>
              </div>
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
import { useSignUp } from '@scripts/views/SignUp'
import BehaviorCaptchaDialog from '@/components/captcha/BehaviorCaptchaDialog.vue'

const captchaDialogRef = ref()

const {
  agreed,
  formRef,
  handleSignUp,
  loading,
  regForm,
  rules,
} = useSignUp(captchaDialogRef)
</script>

<style scoped src="../assets/styles/signup.css"></style>
