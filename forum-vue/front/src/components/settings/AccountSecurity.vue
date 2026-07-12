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
      <el-button class="security-pwd-btn" round @click="emit('open-password')">
        <span class="security-pwd-btn-inner">
          <img :src="editPwdIconUrl" alt="" class="security-pwd-icon" />
          <span>修改登录密码</span>
        </span>
      </el-button>
    </div>

    <div class="settings-list mt-32">
      <div class="setting-item">
        <div class="setting-label setting-label--text">登录日志</div>
        <div class="setting-content">
          <span class="desc-text">查看最近的登录活动</span>
          <el-button class="edit-btn" :loading="loadingLogs" @click="openLoginLogs">查看详情</el-button>
        </div>
      </div>
      <div class="setting-item">
        <div class="setting-label setting-label--text">个性化推荐</div>
        <div class="setting-content">
          <span class="desc-text">关闭后，“为你推荐”只展示公开内容流</span>
          <el-switch
            v-model="personalizedEnabled"
            :loading="savingPersonalization"
            :disabled="loadingPersonalization || savingPersonalization"
            @change="togglePersonalization"
          />
        </div>
      </div>
    </div>

    <el-dialog
      v-model="loginLogVisible"
      title="登录日志"
      width="min(560px, 92vw)"
      class="premium-dialog premium-dialog--login-log"
      align-center
      append-to-body
      destroy-on-close
    >
      <div v-if="loadingLogs" class="login-log-loading">加载中…</div>
      <div v-else-if="loginLogs.length">
        <el-table class="login-log-table" :data="pagedLoginLogs" size="small" stripe border>
          <el-table-column prop="loginTime" label="登录时间" min-width="150" />
          <el-table-column prop="loginTypeLabel" label="方式" width="108" />
          <el-table-column prop="ipLocation" label="IP归属地" width="120" show-overflow-tooltip />
          <el-table-column prop="deviceSummary" label="设备" min-width="120" show-overflow-tooltip />
        </el-table>
        <div class="login-log-pager">
          <el-pagination
            v-model:current-page="loginLogPage"
            background
            layout="prev, pager, next"
            :page-size="loginLogPageSize"
            :total="loginLogs.length"
            small
          />
        </div>
      </div>
      <div v-else class="login-log-empty">暂无登录记录，成功登录后将自动记录。</div>
    </el-dialog>
  </section>
</template>

<script setup>
import editPwdIconUrl from '@/assets/svg/修改.svg?url'
import { useAccountSecurity } from '@scripts/components/settings/AccountSecurity'

const emit = defineEmits(['open-password'])

const {
  loadingLogs,
  loginLogPage,
  loginLogPageSize,
  loginLogVisible,
  loginLogs,
  openLoginLogs,
  pagedLoginLogs,
  loadingPersonalization,
  personalizedEnabled,
  savingPersonalization,
  togglePersonalization,
} = useAccountSecurity()
</script>

<style scoped src="@/assets/styles/settings.css"></style>
