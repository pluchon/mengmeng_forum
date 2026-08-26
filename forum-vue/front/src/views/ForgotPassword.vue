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
              alt="女孩在安静的社区服务台旁重新整理账号信息"
              loading="eager"
              decoding="async"
              @load="onPosterLoad"
              @error="onPosterError"
            >
            <div class="image-mask" aria-hidden="true" />
            <div class="auth-scene-copy">
              <h2 class="auth-scene-copy__title">重新出发</h2>
              <p class="auth-scene-copy__description">验证账号后，很快就能回到社区</p>
            </div>
          </div>

          <main class="form-side form-side--recover">
            <div class="auth-form-body">
              <AuthBrandTitle
                :src="recoverTitle"
                alt="找回密码"
                fallback-text="找回密码"
                @ready="onTitleReady"
              />

              <div class="recover-switch" role="tablist" aria-label="找回方式">
                <button
                  type="button"
                  class="recover-switch__item"
                  :class="{ 'is-active': form.type === 'EMAIL' }"
                  role="tab"
                  :aria-selected="form.type === 'EMAIL'"
                  @click="switchRecoveryType('EMAIL')"
                >
                  <span>邮箱</span>
                  <svg class="recover-switch__icon" viewBox="0 0 24 24" aria-hidden="true">
                    <path
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      d="M4 6h16a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1zm0 1 8 6 8-6"
                    />
                  </svg>
                </button>
                <button
                  type="button"
                  class="recover-switch__item"
                  :class="{ 'is-active': form.type === 'PHONE' }"
                  role="tab"
                  :aria-selected="form.type === 'PHONE'"
                  @click="switchRecoveryType('PHONE')"
                >
                  <span>手机</span>
                  <svg class="recover-switch__icon" viewBox="0 0 24 24" aria-hidden="true">
                    <path
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      d="M8 3h8a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2zm4 16h.01"
                    />
                  </svg>
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
                <el-form-item
                  :label="form.type === 'PHONE' ? '手机号' : '邮箱'"
                  prop="account"
                  class="recover-row"
                >
                  <el-input
                    :model-value="form.account"
                    :placeholder="form.type === 'PHONE' ? '请输入账号绑定的手机号' : '请输入账号绑定的邮箱'"
                    :maxlength="form.type === 'PHONE' ? 11 : undefined"
                    :inputmode="form.type === 'PHONE' ? 'numeric' : 'email'"
                    @update:model-value="onAccountInput"
                  />
                </el-form-item>

                <el-form-item label="验证码" prop="code" class="recover-row">
                  <el-input
                    v-model="form.code"
                    placeholder=""
                    :maxlength="form.type === 'PHONE' ? 4 : 6"
                  >
                    <template #suffix>
                      <button
                        type="button"
                        class="code-action"
                        :class="{
                          'code-action--sending': codePhase === 'sending',
                          'code-action--success': codePhase === 'success',
                          'code-action--countdown': codePhase === 'countdown',
                          'code-action--expired': codePhase === 'expired',
                        }"
                        :disabled="codeBusy"
                        @click="handleSendCode"
                      >
                        <span
                          v-if="codePhase === 'sending'"
                          class="code-action__spinner"
                          aria-hidden="true"
                        />
                        <img
                          v-else-if="codePhase === 'success'"
                          class="code-action__ok"
                          :src="publishedIcon"
                          alt=""
                        >
                        <span v-if="codeLabel" class="code-action__text">{{ codeLabel }}</span>
                      </button>
                    </template>
                  </el-input>
                </el-form-item>

                <el-form-item label="新密码" prop="newPassword" class="recover-row">
                  <el-input
                    v-model="form.newPassword"
                    :type="newPasswordVisible ? 'text' : 'password'"
                    autocomplete="new-password"
                    placeholder="8~20 位，需含大小写字母和数字"
                    maxlength="20"
                    @keyup.enter="handleSubmit"
                  >
                    <template #suffix>
                      <button
                        type="button"
                        class="password-eye-btn"
                        :aria-label="newPasswordVisible ? '隐藏密码' : '显示密码'"
                        @click="toggleNewPasswordVisible"
                      >
                        <el-icon>
                          <View v-if="newPasswordVisible" />
                          <Hide v-else />
                        </el-icon>
                      </button>
                    </template>
                  </el-input>
                </el-form-item>

                <el-form-item label="确认新密码" prop="confirmPassword" class="recover-row">
                  <el-input
                    v-model="form.confirmPassword"
                    :type="confirmPasswordVisible ? 'text' : 'password'"
                    autocomplete="new-password"
                    placeholder="请再次输入新密码"
                    maxlength="20"
                    @keyup.enter="handleSubmit"
                  >
                    <template #suffix>
                      <button
                        type="button"
                        class="password-eye-btn"
                        :aria-label="confirmPasswordVisible ? '隐藏密码' : '显示密码'"
                        @click="toggleConfirmPasswordVisible"
                      >
                        <el-icon>
                          <View v-if="confirmPasswordVisible" />
                          <Hide v-else />
                        </el-icon>
                      </button>
                    </template>
                  </el-input>
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
import { Hide, View } from '@element-plus/icons-vue'
import AuthBrandTitle from '@/components/auth/AuthBrandTitle.vue'
import SiteIcpLink from '@/components/layout/SiteIcpLink.vue'
import { useForgotPassword } from '@scripts/views/ForgotPassword'
import BehaviorCaptchaDialog from '@/components/captcha/BehaviorCaptchaDialog.vue'
import { useAuthShellMedia } from '@/composables/useAuthShellMedia'
import {
  FIND_PASSWORD_TITLE_WEBP_URL as recoverTitle,
  FIND_WEBP_URL as recoverScene,
} from '@/utils/clientOss'
import publishedIcon from '@/assets/svg/已发布.svg'

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
} = useAuthShellMedia(recoverScene)

const {
  codeBusy,
  codeLabel,
  codePhase,
  confirmPasswordVisible,
  form,
  handleSendCode,
  handleSubmit,
  loading,
  newPasswordVisible,
  onAccountInput,
  recoverFormRef,
  rules,
  switchRecoveryType,
  toggleConfirmPasswordVisible,
  toggleNewPasswordVisible,
} = useForgotPassword(captchaDialogRef)
</script>

<style scoped src="@/assets/styles/forgot.css"></style>
