<template>
  <div class="red-settings-page shell-page-scroll animate-fade-up">
    <div class="settings-container">
      <h1 class="settings-title">设置</h1>

      <div class="settings-layout">
        <aside class="settings-side">
          <el-menu :default-active="activeMenu" class="settings-nav" @select="(index) => (activeMenu = index)">
            <el-menu-item index="profile">
              <el-icon><User /></el-icon>
              <span>个人资料</span>
            </el-menu-item>
            <el-menu-item index="account">
              <el-icon><Lock /></el-icon>
              <span>账号与安全</span>
            </el-menu-item>
          </el-menu>
        </aside>

        <div class="settings-main">
          <BasicInfo
            v-if="activeMenu === 'profile'"
            @open-email="emailDialogVisible = true"
            @open-phone="phoneDialogVisible = true"
          />

          <AccountSecurity v-if="activeMenu === 'account'" @open-password="pwdDialogVisible = true" />
        </div>
      </div>
    </div>

    <el-dialog
      v-model="pwdDialogVisible"
      width="450px"
      align-center
      class="premium-dialog premium-dialog--pwd"
      :show-close="false"
    >
      <template #header>
        <div class="premium-dialog-head-center">
          <template v-if="!pwdMethodSelected">
            <div class="pwd-dlg-main-title">安全验证</div>
            <p class="pwd-dlg-subtitle">为了您的账号安全，请先完成身份验证</p>
          </template>
          <div v-else class="pwd-dlg-step-header">
            <el-button link class="pwd-dlg-back" aria-label="返回" @click="pwdMethodSelected = false">
              <el-icon><ArrowLeft /></el-icon>
            </el-button>
            <span class="pwd-dlg-step-title">{{ pwdStepMethod === 'email' ? '邮箱验证' : '手机验证' }}</span>
          </div>
        </div>
      </template>

      <div v-if="!pwdMethodSelected">
        <div class="verify-method-grid">
          <div
            v-if="userStore.email"
            class="verify-method-card"
            :class="{ selected: pwdStepMethod === 'email' }"
            @click="pwdStepMethod = 'email'"
          >
            <div class="method-card-icon">
              <el-icon :size="32"><Message /></el-icon>
            </div>
            <div class="method-card-title">邮箱验证</div>
            <div class="method-card-desc">向 {{ maskContact(userStore.email, 'email') }} 发送验证码</div>
          </div>
          <div
            v-if="userStore.phoneNum"
            class="verify-method-card"
            :class="{ selected: pwdStepMethod === 'phone' }"
            @click="pwdStepMethod = 'phone'"
          >
            <div class="method-card-icon">
              <el-icon :size="32"><Phone /></el-icon>
            </div>
            <div class="method-card-title">手机验证</div>
            <div class="method-card-desc">向 {{ maskContact(userStore.phoneNum, 'phone') }} 发送验证码</div>
          </div>
        </div>
      </div>

      <div v-else>
        <el-form :model="pwdForm" label-position="top" class="mt-20">
          <el-form-item label="验证码">
            <div class="code-input-group">
              <el-input v-model="pwdForm.code" placeholder="验证码" />
              <template v-if="pwdStepMethod === 'email'">
                <el-button
                  :disabled="emailCodeBtnDisabledPwd"
                  :loading="sendingEmailCode"
                  @click="sendPwdCode('EMAIL')"
                >
                  {{ emailCodeBtnTextPwd }}
                </el-button>
              </template>
              <template v-else>
                <el-button
                  :disabled="phoneCodeBtnDisabledPwd"
                  :loading="sendingPhoneCode"
                  @click="sendPwdCode('PHONE')"
                >
                  {{ phoneCodeBtnTextPwd }}
                </el-button>
              </template>
            </div>
          </el-form-item>
          <el-form-item label="设置新密码">
            <el-input v-model="pwdForm.newPassword" type="password" show-password />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button round @click="pwdDialogVisible = false">取消</el-button>
          <el-button
            v-if="!pwdMethodSelected"
            type="primary"
            round
            :disabled="!pwdStepMethod"
            @click="pwdMethodSelected = true"
          >
            下一步
          </el-button>
          <el-button v-else type="primary" round @click="submitPwd">确认重置</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="emailDialogVisible"
      title="邮箱验证"
      width="400px"
      align-center
      class="premium-dialog"
      :show-close="false"
    >
      <el-form :model="emailForm" label-position="top">
        <el-form-item label="新邮箱地址">
          <el-input v-model="emailForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="验证码">
          <div class="code-input-group">
            <el-input v-model="emailForm.code" placeholder="6 位验证码" />
            <el-button :disabled="emailCodeBtnDisabled" :loading="sendingEmailCode" @click="sendCode('email')">
              {{ emailCodeBtnText }}
            </el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button round @click="emailDialogVisible = false">取消</el-button>
          <el-button type="primary" round @click="submitBindEmail">确认绑定</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="phoneDialogVisible"
      title="手机号绑定"
      width="400px"
      align-center
      class="premium-dialog"
      :show-close="false"
    >
      <el-form :model="phoneForm" label-position="top">
        <el-form-item label="手机号码">
          <el-input v-model="phoneForm.phoneNumber" placeholder="请输入手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="验证码">
          <div class="code-input-group">
            <el-input v-model="phoneForm.code" placeholder="4 位验证码" maxlength="4" />
            <el-button :disabled="phoneCodeBtnDisabled" :loading="sendingPhoneCode" @click="sendCode('sms')">
              {{ phoneCodeBtnText }}
            </el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button round @click="phoneDialogVisible = false">取消</el-button>
          <el-button type="primary" round @click="submitBindPhone">确认绑定</el-button>
        </div>
      </template>
    </el-dialog>

    <BehaviorCaptchaDialog ref="captchaDialogRef" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useSettings } from '@scripts/views/Settings'
import BehaviorCaptchaDialog from '@/components/captcha/BehaviorCaptchaDialog.vue'

const captchaDialogRef = ref()

const {
  AccountSecurity,
  ArrowLeft,
  BasicInfo,
  ElMessage,
  Lock,
  Message,
  Phone,
  User,
  activeMenu,
  emailCodeBtnDisabled,
  emailCodeBtnDisabledPwd,
  emailCodeBtnText,
  emailCodeBtnTextPwd,
  emailDialogVisible,
  emailForm,
  maskContact,
  phoneCodeBtnDisabled,
  phoneCodeBtnDisabledPwd,
  phoneCodeBtnText,
  phoneCodeBtnTextPwd,
  phoneDialogVisible,
  phoneForm,
  pwdDialogVisible,
  pwdForm,
  pwdMethodSelected,
  pwdStepMethod,
  sendCode,
  sendPwdCode,
  sendingEmailCode,
  sendingPhoneCode,
  submitBindEmail,
  submitBindPhone,
  submitPwd,
  userStore,
} = useSettings(captchaDialogRef)
</script>

<style scoped src="@/assets/styles/settings.css"></style>
