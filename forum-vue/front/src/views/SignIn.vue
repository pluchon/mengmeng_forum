<template>
  <div class="auth-page">
    <div class="auth-container animate-fade-up">
      <div class="auth-card">
        <div class="auth-layout">
          <div class="brand-side">
            <div class="image-mask" />
          </div>

          <div class="form-side">
            <div class="form-header-block">
              <h1 class="site-title">萌萌论坛</h1>
              <p class="site-tagline">发现一万种可能</p>
              <el-divider class="site-divider" />
            </div>

            <el-tabs v-model="loginTab" class="auth-tabs auth-tabs--stretch auth-tabs--center">
              <el-tab-pane label="验证码登录" name="phone">
                <el-form
                  ref="phoneFormRef"
                  :model="loginForm"
                  :rules="rules"
                  label-position="left"
                  label-width="88px"
                  class="mt-20 labeled-auth-form"
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
                      />
                      <el-button
                        class="code-btn"
                        :disabled="countdown > 0"
                        @click="handleSendCode"
                      >
                        {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
                      </el-button>
                    </div>
                  </el-form-item>
                </el-form>
              </el-tab-pane>

              <el-tab-pane label="用户名登录" name="userName">
                <el-form
                  ref="userNameFormRef"
                  :model="loginForm"
                  :rules="rules"
                  label-position="left"
                  label-width="88px"
                  class="mt-20 labeled-auth-form"
                >
                  <el-form-item label="用户名" prop="userName">
                    <el-input
                      v-model="loginForm.userName"
                      placeholder="登录用户名"
                      @keyup.enter="handleLogin"
                    />
                  </el-form-item>
                  <el-form-item label="密码" prop="password" class="pwd-form-item">
                    <div class="pwd-with-hint">
                      <el-input
                        v-model="loginForm.password"
                        class="pwd-with-hint__input"
                        type="password"
                        placeholder="请输入密码"
                        show-password
                        @keyup.enter="handleLogin"
                      />
                      <el-button
                        class="pwd-forgot-inline"
                        link
                        type="primary"
                        title="找回密码"
                        @click="$router.push('/forgot-password')"
                      >
                        忘记密码
                      </el-button>
                    </div>
                  </el-form-item>
                </el-form>
              </el-tab-pane>

              <el-tab-pane label="邮箱登录" name="email">
                <div
                  class="email-tab-pane-inner"
                  :class="{ 'email-tab-pane-inner--pick': !emailSubTab }"
                >
                  <el-form
                    ref="emailFormRef"
                    :model="loginForm"
                    :rules="rules"
                    label-position="left"
                    label-width="88px"
                    :class="['labeled-auth-form', 'email-inner-form', { 'mt-20': !!emailSubTab }]"
                  >
                    <div v-if="!emailSubTab" class="email-mode-pick">
                      <div class="email-mode-tiles" role="group" aria-label="邮箱登录方式">
                        <button
                          type="button"
                          class="email-mode-tile"
                          @click="emailSubTab = 'password'"
                        >
                          <el-icon class="email-mode-tile__icon" :size="36">
                            <Lock />
                          </el-icon>
                          <span class="email-mode-tile__title">密码登录</span>
                          <span class="email-mode-tile__desc">使用已绑定邮箱的账号密码</span>
                        </button>
                        <button
                          type="button"
                          class="email-mode-tile"
                          @click="emailSubTab = 'code'"
                        >
                          <el-icon class="email-mode-tile__icon" :size="36">
                            <MailIcon />
                          </el-icon>
                          <span class="email-mode-tile__title">验证码登录</span>
                          <span class="email-mode-tile__desc">验证码将发送至您的邮箱</span>
                        </button>
                      </div>
                    </div>
                    <template v-else>
                      <div class="email-field-wrap">
                        <el-tooltip content="返回选择登录方式" placement="left">
                          <el-button
                            class="email-back-icon"
                            text
                            circle
                            @click="emailSubTab = null"
                          >
                            <el-icon :size="20">
                              <ArrowLeft />
                            </el-icon>
                          </el-button>
                        </el-tooltip>
                        <el-form-item prop="email" label="邮箱" class="email-row-item">
                          <el-input
                            v-model="loginForm.email"
                            placeholder="name@example.com"
                            @keyup.enter="handleLogin"
                          />
                        </el-form-item>
                      </div>
                      <template v-if="emailSubTab === 'password'">
                        <el-form-item label="密码" prop="emailPassword" class="pwd-form-item flat-item">
                          <div class="pwd-with-hint">
                            <el-input
                              v-model="loginForm.emailPassword"
                              class="pwd-with-hint__input"
                              type="password"
                              placeholder="密码"
                              show-password
                              @keyup.enter="handleLogin"
                            />
                            <el-button
                              class="pwd-forgot-inline"
                              link
                              type="primary"
                              title="找回密码"
                              @click="$router.push('/forgot-password')"
                            >
                              忘记密码
                            </el-button>
                          </div>
                        </el-form-item>
                      </template>
                      <template v-else>
                        <el-form-item label="验证码" prop="emailCode" class="flat-item">
                          <div class="code-input-wrap">
                            <el-input
                              v-model="loginForm.emailCode"
                              placeholder="6 位验证码"
                              maxlength="6"
                            />
                            <el-button
                              class="code-btn"
                              :disabled="mailCountdown > 0"
                              @click="handleSendMailCode"
                            >
                              {{ mailCountdown > 0 ? `${mailCountdown}s` : '获取验证码' }}
                            </el-button>
                          </div>
                        </el-form-item>
                      </template>
                    </template>
                  </el-form>
                </div>
              </el-tab-pane>
            </el-tabs>

            <div
              v-show="!(loginTab === 'email' && !emailSubTab)"
              class="action-section"
            >
              <div class="action-primary-login">
                <el-button
                  type="primary"
                  class="submit-btn submit-btn--wide"
                  :loading="loading"
                  @click="handleLogin"
                >
                  <el-icon class="btn-icon">
                    <Key />
                  </el-icon>
                  登录
                </el-button>
              </div>
              <div class="action-secondary-row">
                <el-button class="reg-btn reg-btn--pair" @click="$router.push('/sign-up')">
                  <el-icon class="btn-icon">
                    <CirclePlus />
                  </el-icon>
                  立即创建账号
                </el-button>
                <el-button type="primary" plain class="browse-btn browse-btn--pair" @click="$router.push('/')">
                  <el-icon class="btn-icon">
                    <UserFilled />
                  </el-icon>
                  随便看看
                </el-button>
              </div>

              <div class="policy-bar">
                <el-checkbox v-model="agreed">
                  我已阅读并同意
                  <a
                    href="javascript:;"
                    class="link"
                    @click.stop="$router.push('/terms')"
                  >用户协议</a>
                  与
                  <a
                    href="javascript:;"
                    class="link"
                    @click.stop="$router.push('/privacy')"
                  >隐私政策</a>
                </el-checkbox>
              </div>
            </div>
          </div>
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

const captchaDialogRef = ref()

const {
  AnnouncementBoard,
  ArrowLeft,
  CirclePlus,
  Key,
  Lock,
  MailIcon,
  UserFilled,
  agreed,
  announcementRef,
  countdown,
  emailFormRef,
  emailSubTab,
  handleLogin,
  handleSendCode,
  handleSendMailCode,
  loading,
  loginForm,
  loginTab,
  mailCountdown,
  phoneFormRef,
  rules,
  userNameFormRef,
} = useSignIn(captchaDialogRef)
</script>

<style scoped src="../assets/styles/signin.css"></style>
