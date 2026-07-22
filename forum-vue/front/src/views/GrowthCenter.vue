<template>
  <section class="growth-center shell-page-scroll">
    <div class="growth-container">
      <el-skeleton v-if="loading" animated :rows="8" />

      <el-result
        v-else-if="error"
        icon="error"
        title="成长中心暂不可用"
        :sub-title="error"
      >
        <template #extra>
          <el-button type="primary" @click="load">重试</el-button>
        </template>
      </el-result>

      <template v-else-if="overview">
        <template v-if="!active">
          <header class="growth-profile-card">
            <div class="growth-profile-main">
              <UserAvatarVip
                :size="62"
                :src="userStore.avatarUrl"
                :vip-tier="Number(userStore.vipTier) || 0"
                :vip-expire-at="userStore.vipExpireAt"
              />
              <div class="growth-profile-copy">
                <h1>{{ userStore.nickname || '社区用户' }}</h1>
                <p>当前等级：{{ userTypeLabel }}</p>
              </div>
            </div>

              <div class="growth-level-card">
                <div class="growth-level-head">
                  <strong>Lv.{{ overview.growthLevel }}</strong>
                  <div class="growth-level-meta">
                    <span>{{ overview.experience }} / {{ overview.nextLevelExperience }} XP</span>
                    <button
                      type="button"
                      class="growth-record-trigger"
                      aria-label="查看成长记录"
                      title="成长记录"
                      @click="openRecordDialog"
                    >
                      <el-icon><Clock /></el-icon>
                    </button>
                  </div>
                </div>
              <div class="growth-level-progress">
                <i :style="{ width: progress + '%' }"></i>
              </div>
            </div>
          </header>

          <main class="growth-dashboard">
            <section
              v-loading="challengeLoading"
              class="growth-challenge-section"
            >
              <div v-if="challengeError" class="growth-challenge-state is-error">
                <span>{{ challengeError }}</span>
                <button type="button" @click="loadChallengePage(challengePage)">重新加载</button>
              </div>

              <div v-else-if="challenges.length" class="growth-challenge-grid">
                <article
                  v-for="item in challenges"
                  :key="item.challengeCode"
                  class="growth-challenge-card"
                  :class="{ 'is-done': item.status === 'REWARDED' }"
                >
                  <div class="growth-card-topline">
                    <span class="growth-card-icon">
                      <el-icon>
                        <CircleCheck v-if="item.status === 'REWARDED'" />
                        <Opportunity v-else />
                      </el-icon>
                    </span>
                    <em>+{{ item.experienceReward }} XP</em>
                  </div>

                  <div class="growth-card-copy">
                    <h3>{{ item.title }}</h3>
                  </div>

                  <div class="growth-card-footer">
                    <div class="growth-card-requirement">
                      <span><b>{{ item.questionCount }}</b> 题</span>
                      <i></i>
                      <span><b>{{ item.passingScore }}</b> 分通过</span>
                    </div>
                    <button
                      type="button"
                      :disabled="item.status === 'REWARDED'"
                      @click="start(item)"
                    >
                      {{ item.status === 'REWARDED' ? '已完成' : '开始挑战' }}
                      <el-icon v-if="item.status !== 'REWARDED'"><ArrowRight /></el-icon>
                    </button>
                  </div>
                </article>
              </div>

              <div v-else class="growth-challenge-state">暂无可参与的成长挑战</div>

              <nav v-if="challengeTotal > 0" class="growth-challenge-pagination" aria-label="成长挑战分页">
                <button
                  type="button"
                  :disabled="challengePage <= 1 || challengeLoading"
                  @click="loadChallengePage(challengePage - 1)"
                >
                  <el-icon><ArrowLeft /></el-icon>
                  上一页
                </button>
                <span>第 {{ challengePage }} / {{ challengePages }} 页 · 共 {{ challengeTotal }} 项</span>
                <button
                  type="button"
                  :disabled="challengePage >= challengePages || challengeLoading"
                  @click="loadChallengePage(challengePage + 1)"
                >
                  下一页
                  <el-icon><ArrowRight /></el-icon>
                </button>
              </nav>
            </section>

            <aside class="growth-milestone-card">
              <div class="growth-milestone-title">
                <div>
                  <el-icon><Medal /></el-icon>
                  <h2>成长里程</h2>
                </div>
                <button type="button" aria-label="查看等级说明" @click="levelDialogVisible = true">
                  <el-icon><QuestionFilled /></el-icon>
                </button>
              </div>
              <div class="growth-milestone-list">
                <div
                  v-for="item in milestoneLevels"
                  :key="item.level"
                  class="growth-milestone-node"
                  :class="`is-${item.status}`"
                >
                  <i></i>
                  <div>
                    <strong>{{ item.title }}</strong>
                    <span>{{ item.status === 'current' ? '当前成长等级' : item.requirement }}</span>
                  </div>
                </div>
              </div>
            </aside>
          </main>

          <el-dialog
            v-model="recordDialogVisible"
            class="growth-record-dialog"
            width="min(760px, calc(100vw - 32px))"
            append-to-body
            align-center
          >
            <template #header>
              <h2 class="growth-record-dialog-title">成长记录</h2>
            </template>

            <div v-if="recordForbidden" class="growth-record-dialog-state">暂无权限查看成长记录</div>
            <div v-else-if="recordError" class="growth-record-dialog-state is-error">
              <span>{{ recordError }}</span>
              <button type="button" @click="loadRecordPage(recordPage)">重新加载</button>
            </div>
            <div v-else-if="!recordLoading && !records.length" class="growth-record-dialog-state">
              暂无成长记录
            </div>
            <el-table
              v-else
              v-loading="recordLoading"
              :data="records"
              class="growth-record-table"
              stripe
            >
              <el-table-column label="名称" min-width="190" show-overflow-tooltip>
                <template #default="{ row }">
                  <strong class="growth-record-name">{{ row.remark || row.sourceLabel }}</strong>
                </template>
              </el-table-column>
              <el-table-column prop="sourceLabel" label="来源" min-width="120" show-overflow-tooltip />
              <el-table-column label="时间" width="160">
                <template #default="{ row }">{{ formatRecordTime(row.createTime) }}</template>
              </el-table-column>
              <el-table-column label="经验" width="100" align="right">
                <template #default="{ row }">
                  <strong class="growth-record-xp">+{{ row.experienceDelta }} XP</strong>
                </template>
              </el-table-column>
            </el-table>

            <div v-if="recordPages > 1 && !recordError" class="growth-record-dialog-pagination">
              <el-pagination
                v-model:current-page="recordPage"
                :page-size="10"
                :total="recordTotal"
                layout="prev, pager, next"
                background
                :disabled="recordLoading"
                @current-change="onRecordPageChange"
              />
            </div>
          </el-dialog>

          <el-dialog
            v-model="levelDialogVisible"
            class="growth-level-dialog"
            width="min(560px, calc(100vw - 32px))"
          >
            <template #header>
              <h2 class="growth-level-dialog-title">成长等级说明</h2>
            </template>
            <div class="growth-level-dialog-list">
              <article
                v-for="item in milestoneLevels"
                :key="item.level"
                :class="{ 'is-achieved': item.achieved }"
              >
                <span class="growth-level-dialog-badge">Lv.{{ item.level }}</span>
                <div class="growth-level-dialog-copy">
                  <strong>{{ item.name }}</strong>
                  <span>{{ item.requirement }}</span>
                  <p>{{ item.description }}</p>
                </div>
                <span v-if="item.achieved" class="growth-level-dialog-achieved" aria-label="已达到">
                  <el-icon><Check /></el-icon>
                </span>
              </article>
            </div>
          </el-dialog>
        </template>

        <section v-else class="growth-exam">
          <header class="growth-exam-header">
            <button type="button" class="growth-back-button" @click="exitChallenge">
              <el-icon><ArrowLeft /></el-icon>
              <span>退出挑战</span>
            </button>
            <div class="growth-exam-title">
              <h1>{{ active.title }}</h1>
            </div>
            <strong>第 {{ activeQuestionNo }} / {{ activeQuestionTotal }} 题</strong>
          </header>

          <div class="growth-exam-progress">
            <i :style="{ width: questionProgress + '%' }"></i>
          </div>

          <div class="growth-exam-workspace">
            <article class="growth-question-card">
              <div class="growth-question-heading">
                <span class="growth-question-number">{{ String(activeQuestionNo).padStart(2, '0') }}</span>
                <span class="growth-question-type">单选题</span>
              </div>
              <h2>{{ activeQuestion?.stem }}</h2>

              <div class="growth-option-list">
                <button
                  v-for="option in activeOptions"
                  :key="option.label"
                  type="button"
                  class="growth-option"
                  :class="{ 'is-selected': answers[activeQuestion?.id] === option.label }"
                  @click="chooseAnswer(option.label)"
                >
                  <span>{{ option.label }}</span>
                  <b>{{ option.text }}</b>
                  <el-icon v-if="answers[activeQuestion?.id] === option.label"><Check /></el-icon>
                </button>
              </div>

              <div class="growth-question-actions">
                <button
                  type="button"
                  class="is-secondary"
                  :disabled="activeQuestionIndex === 0"
                  @click="prevQuestion"
                >
                  <el-icon><ArrowLeft /></el-icon>
                  上一题
                </button>
                <button
                  v-if="activeQuestionIndex < activeQuestionTotal - 1"
                  type="button"
                  class="is-primary"
                  @click="nextQuestion"
                >
                  下一题
                  <el-icon><ArrowRight /></el-icon>
                </button>
                <button
                  v-else
                  type="button"
                  class="is-primary"
                  :disabled="submitting"
                  @click="submit"
                >
                  {{ submitting ? '提交中' : '提交挑战' }}
                  <el-icon><Check /></el-icon>
                </button>
              </div>
            </article>

            <aside class="growth-question-map">
              <div>
                <div class="growth-map-heading">
                  <strong>题目导航</strong>
                  <span>{{ answeredCount }}/{{ activeQuestionTotal }} 已答</span>
                </div>
                <div class="growth-map-legend">
                  <span><i class="is-current"></i>当前</span>
                  <span><i class="is-answered"></i>已答</span>
                  <span><i></i>未答</span>
                </div>
                <div class="growth-map-grid">
                  <button
                    v-for="item in pagedQuestions"
                    :key="item.question.id"
                    type="button"
                    :class="{
                      'is-active': item.index === activeQuestionIndex,
                      'is-answered': answers[item.question.id],
                    }"
                    @click="selectQuestion(item.index)"
                  >
                    {{ item.index + 1 }}
                  </button>
                </div>
              </div>

              <div v-if="mapPageCount > 1" class="growth-map-pagination">
                <button type="button" :disabled="mapPage <= 1" @click="prevMapPage">上一页</button>
                <span>{{ mapPage }} / {{ mapPageCount }}</span>
                <button type="button" :disabled="mapPage >= mapPageCount" @click="nextMapPage">下一页</button>
              </div>
            </aside>
          </div>
        </section>
      </template>
    </div>
  </section>
</template>

<script src="@/scripts/views/GrowthCenter.js"></script>
<style src="./GrowthCenter.scss" lang="scss"></style>
