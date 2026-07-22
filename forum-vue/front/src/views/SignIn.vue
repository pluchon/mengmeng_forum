<template>
  <div class="auth-page">
    <div class="auth-container animate-fade-up">
      <div class="auth-card">
        <div class="auth-layout">
          <div class="brand-side">
            <img
              class="brand-side__img"
              :src="loginScene"
              alt="女孩在温暖的社区空间里阅读讨论"
              loading="eager"
              decoding="async"
            >
            <div class="image-mask" aria-hidden="true" />
            <div class="auth-scene-copy">
              <h2 class="auth-scene-copy__title">遇见同好</h2>
              <p class="auth-scene-copy__description">一个交友、发帖与娱乐兼具的社区。</p>
            </div>
          </div>

          <main class="form-side form-side--signin">
            <div class="auth-form-body">
            <header class="auth-brand-header">
              <h1 class="auth-brand-title">{{ SITE_NAME }}</h1>
            </header>

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
                      v-model="loginForm.phoneNum"
                      placeholder="11 位手机号"
                      maxlength="11"
                    >
                      <template #prefix>+86</template>
                    </el-input>
                  </el-form-item>
                  <el-form-item label="验证码" prop="code">
                    <div class="code-input-wrap">
                      <el-input
                        v-model="loginForm.code"
                        placeholder="4 位验证码"
                        maxlength="4"
                        @keyup.enter="handleLogin"
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
                      placeholder="登录用户名"
                      @keyup.enter="handleLogin"
                    />
                  </el-form-item>
                  <el-form-item label="密码" prop="password">
                    <div class="password-input-row">
                      <el-input
                        v-model="loginForm.password"
                        type="password"
                        placeholder="请输入密码"
                        show-password
                        @keyup.enter="handleLogin"
                      />
                      <button
                        type="button"
                        class="password-forgot-button"
                        @click="$router.push('/forgot-password')"
                      >
                        忘记密码
                      </button>
                    </div>
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
                      placeholder="name@example.com"
                    />
                  </el-form-item>
                  <el-form-item label="验证码" prop="emailCode">
                    <div class="code-input-wrap">
                      <el-input
                        v-model="loginForm.emailCode"
                        placeholder="6 位验证码"
                        maxlength="6"
                        @keyup.enter="handleLogin"
                      />
                      <el-button
                        class="code-btn"
                        :disabled="mailCountdown > 0"
                        :loading="sendingMailCode"
                        @click="handleSendMailCode"
                      >
                        {{ mailCountdown > 0 ? `${mailCountdown}s` : '获取验证码' }}
                      </el-button>
                    </div>
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
                      placeholder="name@example.com"
                      @keyup.enter="handleLogin"
                    />
                  </el-form-item>
                  <el-form-item label="密码" prop="emailPassword">
                    <div class="password-input-row">
                      <el-input
                        v-model="loginForm.emailPassword"
                        type="password"
                        placeholder="请输入密码"
                        show-password
                        @keyup.enter="handleLogin"
                      />
                      <button
                        type="button"
                        class="password-forgot-button"
                        @click="$router.push('/forgot-password')"
                      >
                        忘记密码
                      </button>
                    </div>
                  </el-form-item>
                </el-form>
              </el-tab-pane>
            </el-tabs>

            <div class="action-section">
              <div class="policy-bar">
                <el-checkbox v-model="agreed">
                  我已阅读并同意
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
                  <span>第一次来？</span>
                  <strong>创建账号</strong>
                </button>
                <span class="auth-secondary-links__divider" aria-hidden="true" />
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
  <AnnouncementBoard ref="announcementRef" />
  <BehaviorCaptchaDialog ref="captchaDialogRef" />
</template>

<script setup>
import { ref } from 'vue'
import { useSignIn } from '@scripts/views/SignIn'
import BehaviorCaptchaDialog from '@/components/captcha/BehaviorCaptchaDialog.vue'
import SiteIcpLink from '@/components/layout/SiteIcpLink.vue'
import { LOGIN_WEBP_URL as loginScene } from '@/utils/clientOss'
import { SITE_NAME } from '@/constants/site'

const captchaDialogRef = ref()

const {
  AnnouncementBoard,
  agreed,
  announcementRef,
  countdown,
  emailCodeFormRef,
  emailPasswordFormRef,
  handleLogin,
  handleSendCode,
  handleSendMailCode,
  loading,
  loginForm,
  loginTab,
  mailCountdown,
  phoneFormRef,
  rules,
  sendingCode,
  sendingMailCode,
  userNameFormRef,
} = useSignIn(captchaDialogRef)
</script>

<style scoped src="../assets/styles/signin.css"></style>
