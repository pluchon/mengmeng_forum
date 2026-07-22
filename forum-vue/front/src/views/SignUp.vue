<template>
  <div class="auth-page">
    <div class="auth-container animate-fade-up">
      <div class="auth-card">
        <div class="auth-layout">
          <div class="brand-side">
            <img
              class="brand-side__img"
              :src="registerScene"
              alt="女孩在社区活动桌旁准备加入讨论"
              loading="eager"
              decoding="async"
            >
            <div class="image-mask" aria-hidden="true" />
            <div class="auth-scene-copy">
              <h2 class="auth-scene-copy__title">加入萌部落</h2>
              <p class="auth-scene-copy__description">认识新朋友，也分享你的兴趣与日常。</p>
            </div>
          </div>

          <main class="form-side form-side--signup">
            <div class="auth-form-body">
            <header class="auth-brand-header">
              <h1 class="auth-page-title auth-page-title--standalone">创建账号</h1>
            </header>

            <el-form
              ref="formRef"
              :model="regForm"
              :rules="rules"
              :show-message="false"
              label-position="top"
              class="signup-form"
            >
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

              <el-button
                type="primary"
                class="flat-submit-btn"
                :loading="loading"
                @click="handleSignUp"
              >
                创建账号
              </el-button>

              <div class="auth-secondary-links">
                <span>已有账号？</span>
                <button type="button" class="auth-text-link" @click="$router.push('/sign-in')">
                  返回登录
                </button>
              </div>
            </div>
            </div>

            <SiteIcpLink variant="auth" class="form-side__footer-icp" />
          </main>
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
import SiteIcpLink from '@/components/layout/SiteIcpLink.vue'
import { REGISTER_WEBP_URL as registerScene } from '@/utils/clientOss'

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
