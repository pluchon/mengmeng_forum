<template>
  <section class="settings-section settings-section--account animate-fade-in">
    <div class="security-overview">
      <div class="security-overview__icon">
        <el-icon><CircleCheck /></el-icon>
      </div>
      <div class="security-overview__content">
        <div class="security-overview__title-row">
          <h2>账号安全</h2>
          <span
            class="security-level"
            :class="{
              'security-level--warning': securityAssessment && securityAssessment.level !== '良好',
              'security-level--risk': securityAssessment?.loginRiskDetected,
            }"
          >
            · {{ securityAssessment?.level || (securityAssessmentLoading ? '评估中' : '待评估') }}
          </span>
        </div>
        <p>{{ securityAssessment?.description || (securityAssessmentLoading ? '正在读取账号安全信息' : '暂时无法读取，请重新评估') }}</p>
        <p
          v-if="securityAssessment?.loginRiskDetected && securityAssessment?.loginRiskHint"
          class="security-overview__risk"
        >
          {{ securityAssessment.loginRiskHint }}
        </p>
      </div>
      <span v-if="securityReassessing" class="security-overview__reassessing">重新评估中......</span>
      <el-tooltip v-else content="重新评估" placement="top">
        <el-button
          class="security-overview__refresh"
          circle
          :disabled="securityAssessmentLoading"
          aria-label="重新评估账号安全"
          @click="reassessSecurity"
        >
          <el-icon><RefreshRight /></el-icon>
        </el-button>
      </el-tooltip>
    </div>

    <div class="settings-feature-list">
      <div class="settings-feature-row">
        <div class="settings-feature-row__icon settings-feature-row__icon--violet">
          <el-icon><Key /></el-icon>
        </div>
        <div class="settings-feature-row__content">
          <h3>登录密码</h3>
          <p>建议定期更新密码</p>
        </div>
        <el-button class="settings-secondary-button" @click="emit('open-password')">
          修改密码
        </el-button>
      </div>
      <div class="settings-feature-row">
        <div class="settings-feature-row__icon settings-feature-row__icon--rose">
          <el-icon><Clock /></el-icon>
        </div>
        <div class="settings-feature-row__content">
          <h3>登录日志</h3>
          <p>查看最近的登录活动</p>
        </div>
        <el-button class="settings-secondary-button" :loading="loadingLogs" @click="openLoginLogs">
          查看详情
        </el-button>
      </div>
    </div>

    <el-dialog
      v-model="loginLogVisible"
      title="登录日志"
      width="min(860px, 94vw)"
      class="premium-dialog premium-dialog--login-log"
      align-center
      append-to-body
      destroy-on-close
    >
      <div v-if="loadingLogs" class="login-log-loading">加载中…</div>
      <div v-else-if="loginLogs.length">
        <el-table class="login-log-table" :data="pagedLoginLogs" size="small" stripe border>
          <el-table-column prop="loginTime" label="登录时间" min-width="165" />
          <el-table-column prop="loginTypeLabel" label="方式" width="112" />
          <el-table-column prop="ipAddress" label="IP" min-width="132" show-overflow-tooltip />
          <el-table-column prop="ipRegion" label="IP归属地" width="112" show-overflow-tooltip />
          <el-table-column prop="deviceSummary" label="设备" min-width="120" show-overflow-tooltip />
        </el-table>
        <div class="login-log-pager">
          <AppPagination
            v-model:current-page="loginLogPage"
            :page-size="loginLogPageSize"
            :total="loginLogs.length"
          />
        </div>
      </div>
      <div v-else class="login-log-empty">暂无登录记录，成功登录后将自动记录。</div>
    </el-dialog>

  </section>
</template>

<script setup src="./AccountSecurity.js"></script>

<style scoped src="./AccountSecurity.scss"></style>
