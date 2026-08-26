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
              alt="女孩在温暖的社区空间里阅读讨论"
              loading="eager"
              decoding="async"
              @load="onPosterLoad"
              @error="onPosterError"
            >
            <div class="image-mask" aria-hidden="true" />
            <div class="auth-scene-copy">
              <h2 class="auth-scene-copy__title">遇见同好</h2>
              <p class="auth-scene-copy__description">同好相聚的地方</p>
            </div>
          </div>

          <main class="form-side form-side--signin">
            <div class="auth-form-body">
              <AuthBrandTitle
                :src="loginTitle"
                alt="萌部落"
                fallback-text="萌部落"
                @ready="onTitleReady"
              />

              <el-tabs v-model="loginTab" class="auth-tabs auth-tabs--stretch">
                <el-tab-pane label="短信验证码" name="phone">
                  <el-form
                    ref="phoneFormRef"
                    :model="loginForm"
                    :rules="rules"
                    :show-message="false"
                    label-position="top"
                    class="labeled-auth-form"
                  >
                    <el-form-item label="手机号" prop="phoneNum">
                      <el-input
                        :model-value="loginForm.phoneNum"
                        placeholder=""
                        maxlength="11"
                        inputmode="numeric"
                        autocomplete="tel"
                        @update:model-value="onPhoneNumInput"
                      >
                        <template #prefix>+86</template>
                      </el-input>
                    </el-form-item>
                    <el-form-item label="验证码" prop="code">
                      <el-input
                        v-model="loginForm.code"
                        placeholder=""
                        maxlength="4"
                        @keyup.enter="handleLogin"
                      >
                        <template #suffix>
                          <button
                            type="button"
                            class="code-action"
                            :class="{
                              'code-action--sending': smsCodePhase === 'sending',
                              'code-action--success': smsCodePhase === 'success',
                              'code-action--countdown': smsCodePhase === 'countdown',
                              'code-action--expired': smsCodePhase === 'expired',
                            }"
                            :disabled="smsCodeBusy"
                            @click="handleSendCode"
                          >
                            <span
                              v-if="smsCodePhase === 'sending'"
                              class="code-action__spinner"
                              aria-hidden="true"
                            />
                            <img
                              v-else-if="smsCodePhase === 'success'"
                              class="code-action__ok"
                              :src="publishedIcon"
                              alt=""
                            >
                            <span v-if="smsCodeLabel" class="code-action__text">{{ smsCodeLabel }}</span>
                          </button>
                        </template>
                      </el-input>
                    </el-form-item>
                  </el-form>
                </el-tab-pane>

                <el-tab-pane label="账号密码" name="userName">
                  <el-form
                    ref="userNameFormRef"
                    :model="loginForm"
                    :rules="rules"
                    :show-message="false"
                    label-position="top"
                    class="labeled-auth-form"
                  >
                    <el-form-item label="用户名" prop="userName">
                      <el-input
                        v-model="loginForm.userName"
                        placeholder=""
                        maxlength="20"
                        @keyup.enter="handleLogin"
                      />
                    </el-form-item>
                    <el-form-item label="密码" prop="password">
                      <el-input
                        v-model="loginForm.password"
                        type="password"
                        placeholder=""
                        show-password
                        maxlength="20"
                        class="password-input-with-forgot"
                        @keyup.enter="handleLogin"
                      >
                        <template #suffix>
                          <button
                            type="button"
                            class="password-forgot-button"
                            @click="$router.push('/forgot-password')"
                          >
                            忘记密码
                          </button>
                        </template>
                      </el-input>
                    </el-form-item>
                  </el-form>
                </el-tab-pane>

                <el-tab-pane label="邮箱验证码" name="emailCode">
                  <el-form
                    ref="emailCodeFormRef"
                    :model="loginForm"
                    :rules="rules"
                    :show-message="false"
                    label-position="top"
                    class="labeled-auth-form"
                  >
                    <el-form-item label="邮箱" prop="email">
                      <el-input
                        v-model="loginForm.email"
                        placeholder=""
                      />
                    </el-form-item>
                    <el-form-item label="验证码" prop="emailCode">
                      <el-input
                        v-model="loginForm.emailCode"
                        placeholder=""
                        maxlength="6"
                        @keyup.enter="handleLogin"
                      >
                        <template #suffix>
                          <button
                            type="button"
                            class="code-action"
                            :class="{
                              'code-action--sending': mailCodePhase === 'sending',
                              'code-action--success': mailCodePhase === 'success',
                              'code-action--countdown': mailCodePhase === 'countdown',
                              'code-action--expired': mailCodePhase === 'expired',
                            }"
                            :disabled="mailCodeBusy"
                            @click="handleSendMailCode"
                          >
                            <span
                              v-if="mailCodePhase === 'sending'"
                              class="code-action__spinner"
                              aria-hidden="true"
                            />
                            <img
                              v-else-if="mailCodePhase === 'success'"
                              class="code-action__ok"
                              :src="publishedIcon"
                              alt=""
                            >
                            <span v-if="mailCodeLabel" class="code-action__text">{{ mailCodeLabel }}</span>
                          </button>
                        </template>
                      </el-input>
                    </el-form-item>
                  </el-form>
                </el-tab-pane>

                <el-tab-pane label="邮箱密码" name="emailPassword">
                  <el-form
                    ref="emailPasswordFormRef"
                    :model="loginForm"
                    :rules="rules"
                    :show-message="false"
                    label-position="top"
                    class="labeled-auth-form"
                  >
                    <el-form-item label="邮箱" prop="email">
                      <el-input
                        v-model="loginForm.email"
                        placeholder=""
                        @keyup.enter="handleLogin"
                      />
                    </el-form-item>
                    <el-form-item label="密码" prop="emailPassword">
                      <el-input
                        v-model="loginForm.emailPassword"
                        type="password"
                        placeholder=""
                        show-password
                        maxlength="20"
                        class="password-input-with-forgot"
                        @keyup.enter="handleLogin"
                      >
                        <template #suffix>
                          <button
                            type="button"
                            class="password-forgot-button"
                            @click="$router.push('/forgot-password')"
                          >
                            忘记密码
                          </button>
                        </template>
                      </el-input>
                    </el-form-item>
                  </el-form>
                </el-tab-pane>
              </el-tabs>

              <div class="action-section">
                <div class="policy-bar">
                  <el-checkbox v-model="agreed">
                    同意
                    <a href="javascript:;" class="link" @click.stop="$router.push('/terms')">用户协议</a>
                    与
                    <a href="javascript:;" class="link" @click.stop="$router.push('/privacy')">隐私政策</a>
                  </el-checkbox>
                </div>

                <el-button
                  type="primary"
                  class="submit-btn"
                  :loading="loading"
                  @click="handleLogin"
                >
                  登录
                </el-button>

                <div class="auth-secondary-links">
                  <button type="button" class="auth-secondary-action" @click="$router.push('/sign-up')">
                    <span>第一次来?</span>
                    <strong>创建账号</strong>
                  </button>
                  <span class="auth-secondary-links__divider" aria-hidden="true">|</span>
                  <button type="button" class="auth-secondary-action auth-secondary-action--muted" @click="$router.push('/')">
                    先逛逛
                  </button>
                </div>
              </div>
            </div>

            <SiteIcpLink variant="auth" class="form-side__footer-icp" />
          </main>
        </div>
      </div>
    </div>
  </div>
  <BehaviorCaptchaDialog ref="captchaDialogRef" />
</template>

<script setup>
import { ref } from 'vue'
import { useSignIn } from '@scripts/views/SignIn'
import AuthBrandTitle from '@/components/auth/AuthBrandTitle.vue'
import BehaviorCaptchaDialog from '@/components/captcha/BehaviorCaptchaDialog.vue'
import SiteIcpLink from '@/components/layout/SiteIcpLink.vue'
import { useAuthShellMedia } from '@/composables/useAuthShellMedia'
import { LOGIN_TITLE_WEBP_URL as loginTitle, LOGIN_WEBP_URL as loginScene } from '@/utils/clientOss'
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
} = useAuthShellMedia(loginScene)

const {
  agreed,
  emailCodeFormRef,
  emailPasswordFormRef,
  handleLogin,
  handleSendCode,
  handleSendMailCode,
  loading,
  loginForm,
  loginTab,
  mailCodeBusy,
  mailCodeLabel,
  mailCodePhase,
  onPhoneNumInput,
  phoneFormRef,
  rules,
  smsCodeBusy,
  smsCodeLabel,
  smsCodePhase,
  userNameFormRef,
} = useSignIn(captchaDialogRef)
</script>

<style scoped src="../assets/styles/signin.css"></style>
