<template>
  <section class="settings-section animate-fade-in">
    <div class="security-card">
      <div class="security-info">
        <div class="security-status">
          <span class="status-dot green" />
          <span class="status-text">账号安全：中等</span>
        </div>
        <p class="security-tip">建议定期更改密码以确保账号安全</p>
      </div>
      <el-button type="primary" plain round @click="emit('open-password')">
        <span class="security-pwd-btn-inner">
          <img :src="editPwdIconUrl" alt="" class="security-pwd-icon" />
          <span>修改登录密码</span>
        </span>
      </el-button>
    </div>

    <div class="settings-list mt-32">
      <div class="setting-item disabled">
        <div class="setting-label setting-label--text">
          登录日志
          <el-tag size="small" type="info" class="ml-8">开发中</el-tag>
        </div>
        <div class="setting-content">
          <span class="desc-text">查看最近的异常登录活动</span>
          <el-button link type="info" disabled>查看详情</el-button>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import editPwdIconUrl from '@/assets/svg/修改.svg?url'
import { useAccountSecurity } from '@scripts/components/settings/AccountSecurity'

const emit = defineEmits(['open-password'])

useAccountSecurity()
</script>

<style scoped src="@/assets/styles/settings.css"></style>
