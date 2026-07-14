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
          <div class="personalization-control">
            <el-tooltip content="编辑感兴趣的内容" placement="top">
              <el-button
                class="interest-edit-button"
                circle
                :icon="EditPen"
                :disabled="loadingPersonalization || savingPersonalization"
                aria-label="编辑感兴趣的内容"
                @click="openInterestEditor"
              />
            </el-tooltip>
            <el-switch
              v-model="personalizedEnabled"
              :loading="savingPersonalization"
              :disabled="loadingPersonalization || savingPersonalization"
              @change="togglePersonalization"
            />
          </div>
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

    <el-dialog
      v-model="interestDialogVisible"
      class="recommendation-interest-dialog"
      title="兴趣卡片"
      width="min(620px, calc(100vw - 32px))"
      :close-on-click-modal="!savingPersonalization"
      :close-on-press-escape="!savingPersonalization"
    >
      <div v-loading="interestLoading" class="settings-interest-editor">
        <el-result
          v-if="interestError"
          icon="error"
          title="兴趣板块加载失败"
          :sub-title="interestError"
        >
          <template #extra>
            <el-button type="primary" @click="openInterestEditor">重试</el-button>
          </template>
        </el-result>
        <div v-else-if="categoriesWithId.length" class="recommendation-interest-groups">
          <section v-for="item in categoriesWithId" :key="item.category.id" class="recommendation-interest-group">
            <h3>{{ item.category.name }}</h3>
            <el-checkbox-group v-model="interestDraftBoardIds" :disabled="savingPersonalization">
              <el-checkbox v-for="board in item.boardList || []" :key="board.id" :value="Number(board.id)">
                {{ board.name }}
              </el-checkbox>
            </el-checkbox-group>
          </section>
        </div>
        <el-empty v-else-if="!interestLoading" :image-size="64" description="暂无可选择的兴趣板块" />
      </div>
      <template #footer>
        <div class="recommendation-dialog-actions">
          <el-button
            class="recommendation-save-button"
            type="primary"
            :loading="savingPersonalization"
            :disabled="interestLoading || !!interestError"
            @click="saveInterestPreferences"
          >
            保存
          </el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<script setup src="./AccountSecurity.js"></script>

<style scoped src="@/assets/styles/settings.css"></style>
<style scoped lang="scss" src="./AccountSecurity.scss"></style>
