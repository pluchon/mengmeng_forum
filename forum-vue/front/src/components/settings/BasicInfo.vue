<template>
  <section class="settings-section animate-fade-in">
    <div class="profile-header">
      <div class="avatar-wrapper">
        <UserAvatarVip
          :key="userStore.avatarUrl"
          :src="userStore.avatarUrl || DEFAULT_AVATAR"
          :size="100"
          :vip-tier="Number(userStore.vipTier) || 0"
          :vip-expire-at="userStore.vipExpireAt"
        />
      </div>
      <div class="profile-info-summary">
        <h3>{{ userStore.nickname }}</h3>
        <p>{{ userStore.remark || '这是一条有个性的简介' }}</p>
      </div>
      <el-upload
        :before-upload="handleAvatarUpload"
        accept="image/*"
        :show-file-list="false"
        class="profile-avatar-upload"
      >
        <el-button class="profile-avatar-change">
          <el-icon><EditPen /></el-icon>
          更换头像
        </el-button>
      </el-upload>
    </div>

    <div class="settings-list">
      <div class="setting-item">
        <div class="setting-label setting-label--text setting-label--with-icon setting-label--pink">
          <el-icon><User /></el-icon>
          <span>昵称</span>
        </div>
        <div class="setting-content">
          <template v-if="!editing.nickname">
            <div class="setting-value-stack">
              <span class="value-text">{{ profileForm.nickname }}</span>
              <span
                v-if="reviewStatusText('nickname')"
                class="profile-review-status"
                :class="reviewStatusClass('nickname')"
              >
                {{ reviewStatusText('nickname') }}
                <template v-if="reviewState.nickname?.pendingContent">
                  · {{ reviewState.nickname.pendingContent }}
                </template>
              </span>
            </div>
            <el-button class="edit-btn" @click="startEdit('nickname')">
              <el-icon><EditPen /></el-icon>
              修改
            </el-button>
          </template>
          <template v-else>
            <div class="edit-box">
              <el-input v-model="profileForm.nickname" maxlength="20" show-word-limit />
              <div class="edit-actions">
                <el-button class="edit-actions__save" size="small" :loading="saving" loading-text="保存中…" @click="saveSingleField('nickname')">
                  <el-icon><Check /></el-icon>
                  保存
                </el-button>
                <el-button class="edit-actions__cancel" size="small" @click="cancelEdit('nickname')">
                  <el-icon><Close /></el-icon>
                  取消
                </el-button>
              </div>
            </div>
          </template>
        </div>
      </div>

      <div class="setting-item">
        <div class="setting-label setting-label--text setting-label--with-icon setting-label--violet">
          <el-icon><Avatar /></el-icon>
          <span>性别</span>
        </div>
        <div class="setting-content">
          <template v-if="!editing.gender">
            <span class="value-text">{{ genderLabel(profileForm.gender) }}</span>
            <el-button class="edit-btn" @click="startEdit('gender')">
              <el-icon><EditPen /></el-icon>
              修改
            </el-button>
          </template>
          <template v-else>
            <div class="edit-box edit-box--gender">
              <el-radio-group
                v-model="profileForm.gender"
                class="setting-gender-group"
                :disabled="saving"
                @change="saveGender"
              >
                <el-radio :value="0" border>女</el-radio>
                <el-radio :value="1" border>男</el-radio>
                <el-radio :value="2" border>保密</el-radio>
              </el-radio-group>
            </div>
          </template>
        </div>
      </div>

      <div class="setting-item">
        <div class="setting-label setting-label--text setting-label--with-icon setting-label--blue">
          <el-icon><Document /></el-icon>
          <span>个人简介</span>
        </div>
        <div class="setting-content">
          <template v-if="!editing.remark">
            <div class="setting-value-stack">
              <span class="value-text secondary">{{ profileForm.remark || '未设置简介' }}</span>
              <span
                v-if="reviewStatusText('remark')"
                class="profile-review-status"
                :class="reviewStatusClass('remark')"
              >
                {{ reviewStatusText('remark') }}
                <template v-if="reviewState.remark?.pendingContent">
                  · {{ reviewState.remark.pendingContent }}
                </template>
              </span>
            </div>
            <el-button class="edit-btn" @click="startEdit('remark')">
              <el-icon><EditPen /></el-icon>
              修改
            </el-button>
          </template>
          <template v-else>
            <div class="edit-box">
              <el-input v-model="profileForm.remark" maxlength="50" show-word-limit />
              <div class="edit-actions">
                <el-button class="edit-actions__save" size="small" :loading="saving" loading-text="审核中…" @click="saveSingleField('remark')">
                  <el-icon><Check /></el-icon>
                  保存
                </el-button>
                <el-button class="edit-actions__cancel" size="small" @click="cancelEdit('remark')">
                  <el-icon><Close /></el-icon>
                  取消
                </el-button>
              </div>
            </div>
          </template>
        </div>
      </div>

      <div class="setting-item">
        <div class="setting-label setting-label--text setting-label--with-icon setting-label--pink">
          <el-icon><Message /></el-icon>
          <span>邮箱</span>
        </div>
        <div class="setting-content">
          <span class="value-text">{{ profileForm.email || '未绑定邮箱' }}</span>
          <el-button class="edit-btn" @click="emit('open-email')">
            <el-icon><EditPen /></el-icon>
            {{ profileForm.email ? '修改' : '绑定' }}
          </el-button>
        </div>
      </div>

      <div class="setting-item">
        <div class="setting-label setting-label--text setting-label--with-icon setting-label--violet">
          <el-icon><Iphone /></el-icon>
          <span>手机号码</span>
        </div>
        <div class="setting-content">
          <span class="value-text">{{ maskPhone(profileForm.phoneNum) || '未绑定手机' }}</span>
          <el-button class="edit-btn" @click="emit('open-phone')">
            <el-icon><EditPen /></el-icon>
            {{ profileForm.phoneNum ? '修改' : '绑定' }}
          </el-button>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { Avatar, Check, Close, Document, EditPen, Iphone, Message, User } from '@element-plus/icons-vue'
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import { useBasicInfo } from '@scripts/components/settings/BasicInfo'

const emit = defineEmits(['open-email', 'open-phone'])

const {
  DEFAULT_AVATAR,
  cancelEdit,
  editing,
  genderLabel,
    handleAvatarUpload,
    maskPhone,
  profileForm,
  reviewState,
  reviewStatusClass,
  reviewStatusText,
  saveSingleField,
  saveGender,
  saving,
  startEdit,
  userStore,
} = useBasicInfo()
</script>

<style scoped src="@/assets/styles/settings.css"></style>
