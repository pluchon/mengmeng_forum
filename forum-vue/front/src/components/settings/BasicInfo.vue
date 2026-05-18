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
        <el-upload
          :before-upload="handleAvatarUpload"
          accept="image/*"
          :show-file-list="false"
          class="avatar-uploader"
        >
          <div class="avatar-mask">
            <el-icon><Camera /></el-icon>
            <span>修改头像</span>
          </div>
        </el-upload>
      </div>
      <div class="profile-info-summary">
        <h3>{{ userStore.nickname }}</h3>
        <p>{{ userStore.remark || '这是一条有个性的简介' }}</p>
      </div>
    </div>

    <div class="settings-list">
      <div class="setting-item">
        <div class="setting-label" title="昵称">
          <img :src="nicknameIconUrl" alt="" class="setting-label-icon" />
        </div>
        <div class="setting-content">
          <template v-if="!editing.nickname">
            <span class="value-text">{{ profileForm.nickname }}</span>
            <el-button class="edit-btn" @click="startEdit('nickname')">修改</el-button>
          </template>
          <template v-else>
            <div class="edit-box">
              <el-input v-model="profileForm.nickname" maxlength="20" show-word-limit />
              <div class="edit-actions">
                <el-button type="primary" size="small" :loading="saving" @click="saveSingleField('nickname')">
                  保存
                </el-button>
                <el-button size="small" @click="cancelEdit('nickname')">取消</el-button>
              </div>
            </div>
          </template>
        </div>
      </div>

      <div class="setting-item">
        <div class="setting-label setting-label--text" title="性别">
          <img :src="genderIconUrl" alt="" class="setting-label-icon" />
          <span>性别</span>
        </div>
        <div class="setting-content setting-content--gender">
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
      </div>

      <div class="setting-item">
        <div class="setting-label" title="个人简介">
          <img :src="bioIconUrl" alt="" class="setting-label-icon" />
        </div>
        <div class="setting-content">
          <template v-if="!editing.remark">
            <span class="value-text secondary">{{ profileForm.remark || '未设置简介' }}</span>
            <el-button class="edit-btn" @click="startEdit('remark')">修改</el-button>
          </template>
          <template v-else>
            <div class="edit-box">
              <el-input v-model="profileForm.remark" maxlength="100" show-word-limit />
              <div class="edit-actions">
                <el-button type="primary" size="small" :loading="saving" @click="saveSingleField('remark')">
                  保存
                </el-button>
                <el-button size="small" @click="cancelEdit('remark')">取消</el-button>
              </div>
            </div>
          </template>
        </div>
      </div>

      <div class="setting-item">
        <div class="setting-label" title="电子邮箱">
          <img :src="emailIconUrl" alt="" class="setting-label-icon" />
        </div>
        <div class="setting-content">
          <span class="value-text">{{ profileForm.email || '未绑定邮箱' }}</span>
          <el-button class="edit-btn" @click="emit('open-email')">
            {{ profileForm.email ? '修改' : '绑定' }}
          </el-button>
        </div>
      </div>

      <div class="setting-item">
        <div class="setting-label" title="手机号码">
          <img :src="phoneIconUrl" alt="" class="setting-label-icon" />
        </div>
        <div class="setting-content">
          <span class="value-text">{{ profileForm.phoneNum || '未绑定手机' }}</span>
          <el-button class="edit-btn" @click="emit('open-phone')">
            {{ profileForm.phoneNum ? '修改' : '绑定' }}
          </el-button>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import nicknameIconUrl from '@/assets/svg/昵称.svg?url'
import genderIconUrl from '@/assets/svg/性别.svg?url'
import bioIconUrl from '@/assets/svg/个人简介.svg?url'
import emailIconUrl from '@/assets/svg/邮箱.svg?url'
import phoneIconUrl from '@/assets/svg/手机号码.svg?url'
import { useBasicInfo } from '@scripts/components/settings/BasicInfo'

const emit = defineEmits(['open-email', 'open-phone'])

const {
  Camera,
  DEFAULT_AVATAR,
  cancelEdit,
  editing,
  handleAvatarUpload,
  profileForm,
  saveSingleField,
  saveGender,
  saving,
  startEdit,
  userStore,
} = useBasicInfo()
</script>

<style scoped src="@/assets/styles/settings.css"></style>
