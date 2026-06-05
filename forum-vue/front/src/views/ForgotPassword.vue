<template>
  <div class="auth-page">
    <div class="auth-container animate-fade-up">
      <div class="auth-card">
        <div class="auth-layout">
          <div class="brand-side">
            <img
              class="brand-side__img"
              :src="findBg"
              alt=""
              loading="eager"
              decoding="async"
            >
            <div class="image-mask" aria-hidden="true" />
          </div>

          <div class="form-side form-side--auth-relaxed form-side--recover">
            <p class="form-side__brand-mobile">找回密码</p>

            <div class="form-side__main">
            <div class="recover-switch" role="tablist" aria-label="找回方式">
              <button
                type="button"
                class="recover-switch__item"
                :class="{ 'is-active': form.type === 'EMAIL' }"
                role="tab"
                :aria-selected="form.type === 'EMAIL'"
                @click="form.type = 'EMAIL'"
              >
                邮箱找回
              </button>
              <button
                type="button"
                class="recover-switch__item"
                :class="{ 'is-active': form.type === 'PHONE' }"
                role="tab"
                :aria-selected="form.type === 'PHONE'"
                @click="form.type = 'PHONE'"
              >
                手机找回
              </button>
              <div
                class="recover-switch__thumb"
                :style="{ transform: `translateX(${form.type === 'PHONE' ? '100%' : '0%'})` }"
                aria-hidden="true"
              />
            </div>

            <el-form class="recover-form" label-position="left" label-width="0px">
              <div class="recover-row">
                <div class="recover-row__label">{{ form.type === 'PHONE' ? '手机号' : '邮箱' }}</div>
                <div class="recover-row__control">
                  <el-input
                    v-model="form.account"
                    :placeholder="form.type === 'PHONE' ? '请输入手机号' : '请输入常用邮箱'"
                  />
                </div>
              </div>

              <div class="recover-row">
                <div class="recover-row__label">验证码</div>
                <div class="recover-row__control">
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
                </div>
              </div>

              <div class="recover-row">
                <div class="recover-row__label">设置新密码</div>
                <div class="recover-row__control">
                  <el-input
                    v-model="form.newPassword"
                    type="password"
                    placeholder="6–12 位数字或字母"
                    show-password
                  />
                </div>
              </div>

              <div class="recover-actions">
                <el-button
                  type="primary"
                  class="flat-submit-btn"
                  :loading="loading"
                  @click="handleSubmit"
                >
                  重置密码并登录
                </el-button>
                <el-button class="code-btn back-login-btn" @click="$router.push('/sign-in')">
                  <el-icon><ArrowLeft /></el-icon>
                  返回登录
                </el-button>
              </div>
            </el-form>
            </div>

            <SiteIcpLink variant="auth" class="form-side__footer-icp" />
          </div>
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
import { FIND_WEBP_URL } from '@/utils/clientOss'

const captchaDialogRef = ref()
const findBg = FIND_WEBP_URL

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
