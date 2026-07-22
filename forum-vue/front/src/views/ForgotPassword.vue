<template>
  <div class="auth-page">
    <div class="auth-container animate-fade-up">
      <div class="auth-card">
        <div class="auth-layout">
          <div class="brand-side">
            <img
              class="brand-side__img"
              :src="recoverScene"
              alt="女孩在安静的社区服务台旁重新整理账号信息"
              loading="eager"
              decoding="async"
            >
            <div class="image-mask" aria-hidden="true" />
            <div class="auth-scene-copy">
              <h2 class="auth-scene-copy__title">重新出发</h2>
              <p class="auth-scene-copy__description">验证账号后，很快就能回到社区。</p>
            </div>
          </div>

          <main class="form-side form-side--recover">
            <div class="auth-form-body">
            <header class="auth-brand-header">
              <h1 class="auth-page-title auth-page-title--standalone">找回密码</h1>
            </header>

            <div class="recover-switch" role="tablist" aria-label="找回方式">
              <button
                type="button"
                class="recover-switch__item"
                :class="{ 'is-active': form.type === 'EMAIL' }"
                role="tab"
                :aria-selected="form.type === 'EMAIL'"
                @click="switchRecoveryType('EMAIL')"
              >
                邮箱
              </button>
              <button
                type="button"
                class="recover-switch__item"
                :class="{ 'is-active': form.type === 'PHONE' }"
                role="tab"
                :aria-selected="form.type === 'PHONE'"
                @click="switchRecoveryType('PHONE')"
              >
                手机
              </button>
              <div
                class="recover-switch__thumb"
                :style="{ transform: `translateX(${form.type === 'PHONE' ? '100%' : '0%'})` }"
                aria-hidden="true"
              />
            </div>

            <el-form
              ref="recoverFormRef"
              :model="form"
              :rules="rules"
              :show-message="false"
              class="recover-form"
              label-position="top"
            >
              <el-form-item prop="account" class="recover-row">
                <div class="recover-row__label">{{ form.type === 'PHONE' ? '手机号' : '邮箱' }}</div>
                <el-input
                  v-model="form.account"
                  :placeholder="form.type === 'PHONE' ? '请输入手机号' : '请输入常用邮箱'"
                />
              </el-form-item>

              <el-form-item prop="code" class="recover-row">
                <div class="recover-row__label">验证码</div>
                <div class="code-input-wrap">
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
              </el-form-item>

              <el-form-item prop="newPassword" class="recover-row">
                <div class="recover-row__label">新密码</div>
                <el-input
                  v-model="form.newPassword"
                  type="password"
                  placeholder="6–12 位数字或字母"
                  show-password
                  @keyup.enter="handleSubmit"
                />
              </el-form-item>

              <el-button
                type="primary"
                class="flat-submit-btn"
                :loading="loading"
                @click="handleSubmit"
              >
                重置密码
              </el-button>
            </el-form>

            <div class="auth-secondary-links">
              <span>想起密码了？</span>
              <button type="button" class="auth-text-link" @click="$router.push('/sign-in')">
                返回登录
              </button>
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
import SiteIcpLink from '@/components/layout/SiteIcpLink.vue'
import { useForgotPassword } from '@scripts/views/ForgotPassword'
import BehaviorCaptchaDialog from '@/components/captcha/BehaviorCaptchaDialog.vue'
import { FIND_WEBP_URL as recoverScene } from '@/utils/clientOss'

const captchaDialogRef = ref()

const {
  countdown,
  form,
  handleSendCode,
  handleSubmit,
  loading,
  recoverFormRef,
  rules,
  sendingCode,
  switchRecoveryType,
} = useForgotPassword(captchaDialogRef)
</script>

<style scoped src="@/assets/styles/forgot.css"></style>
