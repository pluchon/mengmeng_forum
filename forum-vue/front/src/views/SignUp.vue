<template>
  <div class="auth-page">
    <div class="auth-container">
      <div class="auth-card">
        <div
          class="auth-shell-loader"
          :class="{ 'is-leaving': !isShellLoading }"
          :aria-hidden="!isShellLoading"
          :aria-busy="isShellLoading"
        >
          <div class="auth-shell-loader__track" role="progressbar" :aria-valuenow="Math.round(progress)" aria-valuemin="0" aria-valuemax="100">
            <div class="auth-shell-loader__bar" :style="{ width: `${progress}%` }" />
          </div>
          <p class="auth-shell-loader__hint">加载中</p>
        </div>

        <div class="auth-layout" :class="shellLayoutClass">
          <div class="brand-side">
            <img
              ref="posterImgRef"
              class="brand-side__img"
              :src="posterSrc"
              alt="女孩在社区活动桌旁准备加入讨论"
              loading="eager"
              decoding="async"
              @load="onPosterLoad"
              @error="onPosterError"
            >
            <div class="image-mask" aria-hidden="true" />
            <div class="auth-scene-copy">
              <h2 class="auth-scene-copy__title">加入萌部落</h2>
              <p class="auth-scene-copy__description">认识新朋友，也分享你的兴趣与日常</p>
            </div>
          </div>

          <main class="form-side form-side--signup">
            <div class="auth-form-body">
              <AuthBrandTitle
                :src="createAccountTitle"
                alt="创建账户"
                fallback-text="创建账户"
                @ready="onTitleReady"
              />

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
                    <el-input v-model="regForm.userName" placeholder="4–20位中英数" maxlength="20" />
                  </el-form-item>

                  <el-form-item label="显示昵称" prop="nickname" class="flat-form-item">
                    <el-input v-model="regForm.nickname" placeholder="2–20位中英数" maxlength="20" />
                  </el-form-item>
                </div>

                <el-form-item label="设置密码" prop="password" class="flat-form-item">
                  <el-input
                    v-model="regForm.password"
                    type="password"
                    placeholder="8~20 位，需含大小写字母和数字"
                    show-password
                    maxlength="20"
                  />
                </el-form-item>

                <div class="form-grid">
                  <el-form-item label="手机号码（选填）" prop="phoneNum" class="flat-form-item">
                    <el-input
                      :model-value="regForm.phoneNum"
                      placeholder=""
                      maxlength="11"
                      inputmode="numeric"
                      autocomplete="tel"
                      @update:model-value="onPhoneNumInput"
                    />
                  </el-form-item>

                  <el-form-item label="电子邮箱（选填）" prop="email" class="flat-form-item">
                    <el-input v-model="regForm.email" placeholder="" />
                  </el-form-item>
                </div>
              </el-form>

              <div class="action-section">
                <div class="form-policy">
                  <el-checkbox v-model="agreed">
                    同意
                    <a href="javascript:;" class="link" @click.stop="$router.push('/terms')">用户协议</a>
                    与
                    <a href="javascript:;" class="link" @click.stop="$router.push('/privacy')">隐私政策</a>
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
import AuthBrandTitle from '@/components/auth/AuthBrandTitle.vue'
import BehaviorCaptchaDialog from '@/components/captcha/BehaviorCaptchaDialog.vue'
import SiteIcpLink from '@/components/layout/SiteIcpLink.vue'
import { useAuthShellMedia } from '@/composables/useAuthShellMedia'
import {
  CREATE_ACCOUNT_TITLE_WEBP_URL as createAccountTitle,
  REGISTER_WEBP_URL as registerScene,
} from '@/utils/clientOss'

const captchaDialogRef = ref()

const {
  posterSrc,
  posterImgRef,
  progress,
  isShellLoading,
  shellLayoutClass,
  onPosterLoad,
  onPosterError,
  onTitleReady,
} = useAuthShellMedia(registerScene)

const {
  agreed,
  formRef,
  handleSignUp,
  loading,
  onPhoneNumInput,
  regForm,
  rules,
} = useSignUp(captchaDialogRef)
</script>

<style scoped src="../assets/styles/signup.css"></style>
