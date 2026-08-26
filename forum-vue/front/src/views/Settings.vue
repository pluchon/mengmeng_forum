<template>
  <div class="red-settings-page shell-page-scroll animate-fade-up">
    <div class="settings-container">
      <header class="settings-page-head">
        <h1 class="settings-title">设置</h1>
        <button type="button" class="settings-back-link" @click="goBack">
          <el-icon :size="16"><ArrowLeft /></el-icon>
          <span>返回</span>
        </button>
      </header>

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
            <el-menu-item index="preference">
              <el-icon><Operation /></el-icon>
              <span>偏好设置</span>
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
          <section v-if="activeMenu === 'preference'" class="settings-section settings-section--preference animate-fade-in">
            <div class="preference-row">
              <div class="preference-row__icon preference-row__icon--pink">
                <el-icon><Avatar /></el-icon>
              </div>
              <div class="preference-row__content">
                <h2>看板娘显示</h2>
                <p>在页面中显示陪伴你的看板娘</p>
              </div>
              <el-switch
                :model-value="mascotUi.visible"
                class="settings-brand-switch"
                aria-label="看板娘显示"
                @change="mascotUi.setVisible"
              />
            </div>
            <div class="preference-row">
              <div class="preference-row__icon">
                <el-icon><MagicStick /></el-icon>
              </div>
              <div class="preference-row__content">
                <h2>个性化推荐</h2>
                <p>根据你的互动偏好推荐更适合的内容</p>
              </div>
              <el-switch
                v-model="personalizedEnabled"
                class="settings-brand-switch"
                :loading="preferenceLoading"
                :disabled="preferenceLoading"
                aria-label="个性化推荐"
                @change="saveRecommendationSetting"
              />
            </div>
            <div class="preference-row">
              <div class="preference-row__icon preference-row__icon--pink">
                <el-icon><Operation /></el-icon>
              </div>
              <div class="preference-row__content">
                <h2>兴趣版块</h2>
                <p>选择你感兴趣的板块吧</p>
              </div>
              <div class="preference-row__action">
                <span
                  v-if="interestBoardSummary"
                  class="preference-board-summary"
                  :title="interestBoardSummary"
                >
                  {{ interestBoardSummary }}
                </span>
                <el-button
                  class="edit-btn preference-board-edit"
                  :disabled="preferenceLoading || interestSaving || !personalizedEnabled"
                  :loading="interestSaving"
                  @click="openInterestBoardDialog"
                >
                  编辑
                </el-button>
              </div>
            </div>
            <div class="preference-row">
              <div class="preference-row__icon">
                <el-icon><Message /></el-icon>
              </div>
              <div class="preference-row__content">
                <h2>回车发送消息</h2>
                <p>私聊或群聊输入时，按回车键发送（Shift+回车换行）</p>
              </div>
              <el-switch
                v-model="enterToSendEnabled"
                class="settings-brand-switch"
                aria-label="回车发送消息"
                @change="saveEnterToSendEnabled"
              />
            </div>
          </section>
        </div>
      </div>
    </div>

    <el-dialog
      v-model="interestBoardDialogVisible"
      width="480px"
      align-center
      class="premium-dialog settings-interest-dialog"
      :show-close="false"
      :close-on-click-modal="!interestSaving"
      @closed="draftInterestBoardIds = []"
    >
      <template #header>
        <div class="settings-interest-dialog__header">
          <h2>选择兴趣版块</h2>
        </div>
      </template>
      <p class="settings-interest-dialog__tip">最多选择 5 个版块，已选 {{ draftInterestBoardIds.length }}/5</p>
      <el-checkbox-group
        :model-value="draftInterestBoardIds"
        class="settings-interest-checkbox-group"
        @change="onDraftInterestBoardChange"
      >
        <section
          v-for="group in interestBoardGroups"
          :key="group.categoryId"
          class="settings-interest-group"
        >
          <h3 class="settings-interest-group__title">{{ group.categoryName }}</h3>
          <div class="settings-interest-group__boards">
            <el-checkbox
              v-for="item in group.boards"
              :key="item.value"
              :value="item.value"
              :disabled="interestSaving || (draftInterestBoardIds.length >= 5 && !draftInterestBoardIds.includes(item.value))"
            >
              {{ item.label }}
            </el-checkbox>
          </div>
        </section>
      </el-checkbox-group>
      <template #footer>
        <div class="settings-interest-dialog__actions">
          <el-button :disabled="interestSaving" @click="closeInterestBoardDialog">取消</el-button>
          <el-button
            class="settings-verify-confirm"
            :loading="interestSaving"
            @click="confirmInterestBoards"
          >
            确认
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="pwdDialogVisible"
      width="450px"
      align-center
      class="premium-dialog premium-dialog--pwd settings-password-dialog"
      :show-close="false"
    >
      <template #header>
        <div class="settings-password-dialog__header">
          <template v-if="!pwdMethodSelected">
            <h2>安全验证</h2>
          </template>
          <template v-else>
            <el-button text class="settings-password-dialog__back" aria-label="返回安全验证方式" @click="pwdMethodSelected = false">
              <el-icon><ArrowLeft /></el-icon>
            </el-button>
            <h2>{{ pwdStepMethod === 'email' ? '邮箱验证' : '手机验证' }}</h2>
          </template>
          <el-button text class="settings-password-dialog__close" aria-label="关闭安全验证" @click="pwdDialogVisible = false">
            <el-icon><Close /></el-icon>
          </el-button>
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
        <el-form :model="pwdForm" class="settings-verify-form settings-password-dialog__form">
          <el-form-item label="验证码" class="settings-verify-code-row">
            <el-input v-model="pwdForm.code" :placeholder="pwdStepMethod === 'email' ? '6 位验证码' : '4 位验证码'" :maxlength="pwdStepMethod === 'email' ? 6 : 4">
              <template #suffix>
                <button
                  v-if="pwdStepMethod === 'email'"
                  type="button"
                  class="settings-code-action"
                  :disabled="emailCodeBtnDisabledPwd || sendingEmailCode"
                  @click="sendPwdCode('EMAIL')"
                >
                  {{ emailCodeBtnTextPwd }}
                </button>
                <button
                  v-else
                  type="button"
                  class="settings-code-action"
                  :disabled="phoneCodeBtnDisabledPwd || sendingPhoneCode"
                  @click="sendPwdCode('PHONE')"
                >
                  {{ phoneCodeBtnTextPwd }}
                </button>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item label="设置新密码" class="settings-verify-code-row">
            <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="8–20位，需含大小写字母和数字" />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <div class="settings-verify-actions settings-password-dialog__actions">
          <el-button
            v-if="!pwdMethodSelected"
            class="settings-verify-confirm"
            :disabled="!pwdStepMethod"
            @click="pwdMethodSelected = true"
          >
            下一步
          </el-button>
          <el-button v-else class="settings-verify-confirm" @click="submitPwd">确认重置</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="emailDialogVisible"
      width="400px"
      align-center
      class="premium-dialog settings-verify-dialog"
      :show-close="false"
    >
      <template #header>
        <div class="settings-verify-dialog__header">
          <h2>邮箱验证</h2>
          <el-button text class="settings-verify-dialog__close" aria-label="关闭邮箱验证" @click="emailDialogVisible = false">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
      </template>
      <el-form :model="emailForm" class="settings-verify-form">
        <div class="settings-verify-inline-row">
          <label for="settings-email-input">新邮箱地址</label>
          <el-input id="settings-email-input" v-model="emailForm.email" placeholder="" />
        </div>
        <el-form-item label="验证码" class="settings-verify-code-row">
          <el-input v-model="emailForm.code" placeholder="6 位验证码" maxlength="6">
            <template #suffix>
              <button
                type="button"
                class="settings-code-action"
                :disabled="emailCodeBtnDisabled || sendingEmailCode"
                @click="sendCode('email')"
              >
                {{ emailCodeBtnText }}
              </button>
            </template>
          </el-input>
        </el-form-item>
      </el-form>
      <div class="settings-verify-actions">
        <el-button class="settings-verify-confirm" @click="submitBindEmail">确认</el-button>
      </div>
    </el-dialog>

    <el-dialog
      v-model="phoneDialogVisible"
      width="400px"
      align-center
      class="premium-dialog settings-verify-dialog"
      :show-close="false"
    >
      <template #header>
        <div class="settings-verify-dialog__header">
          <h2>手机验证</h2>
          <el-button text class="settings-verify-dialog__close" aria-label="关闭手机验证" @click="phoneDialogVisible = false">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
      </template>
      <el-form :model="phoneForm" class="settings-verify-form">
        <div class="settings-verify-inline-row">
          <label for="settings-phone-input">新手机号码</label>
          <el-input id="settings-phone-input" v-model="phoneForm.phoneNumber" placeholder="" maxlength="11" />
        </div>
        <el-form-item label="验证码" class="settings-verify-code-row">
          <el-input v-model="phoneForm.code" placeholder="4 位验证码" maxlength="4">
            <template #suffix>
              <button
                type="button"
                class="settings-code-action"
                :disabled="phoneCodeBtnDisabled || sendingPhoneCode"
                @click="sendCode('sms')"
              >
                {{ phoneCodeBtnText }}
              </button>
            </template>
          </el-input>
        </el-form-item>
      </el-form>
      <div class="settings-verify-actions">
        <el-button class="settings-verify-confirm" @click="submitBindPhone">确认</el-button>
      </div>
    </el-dialog>

    <BehaviorCaptchaDialog ref="captchaDialogRef" />
  </div>
</template>

<script setup src="@/views/Settings.js"></script>

<style scoped src="@/assets/styles/settings.css"></style>
