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
              <span class="growth-seed-icon">
                <el-icon><Opportunity /></el-icon>
              </span>
              <div class="growth-profile-copy">
                <span class="growth-eyebrow">GROWTH CENTER</span>
                <h1>成长中心</h1>
                <p>{{ overview.formalUser ? '正式用户 · 社区创作权限已开启' : '完成新人试炼，开启社区创作' }}</p>
              </div>
            </div>

            <div class="growth-level-card">
              <div class="growth-level-head">
                <span>当前等级</span>
                <strong>Lv.{{ overview.growthLevel }}</strong>
              </div>
              <div class="growth-level-progress">
                <i :style="{ width: progress + '%' }"></i>
              </div>
              <div class="growth-level-foot">
                <span>{{ overview.experience }} / {{ overview.nextLevelExperience }} XP</span>
                <b>还差 {{ Math.max(0, overview.nextLevelExperience - overview.experience) }} XP</b>
              </div>
            </div>
          </header>

          <main class="growth-dashboard">
            <section class="growth-challenge-section">
              <div class="growth-section-heading">
                <el-icon><CircleCheck /></el-icon>
                <h2>成长挑战</h2>
              </div>

              <div v-if="overview.challenges?.length" class="growth-challenge-grid">
                <article
                  v-for="item in overview.challenges"
                  :key="item.challengeCode"
                  class="growth-challenge-card"
                  :class="{ 'is-done': item.status === 'REWARDED' }"
                >
                  <div class="growth-card-topline">
                    <span class="growth-card-icon">
                      <el-icon><CircleCheck v-if="item.status === 'REWARDED'" /><Opportunity v-else /></el-icon>
                    </span>
                    <em>+{{ item.experienceReward }} XP</em>
                  </div>
                  <div class="growth-card-copy">
                    <h3>{{ item.title }}</h3>
                    <p>{{ item.questionCount }} 题 · {{ item.passingScore }} 分通过</p>
                  </div>
                  <div class="growth-card-footer">
                    <span>{{ item.status === 'REWARDED' ? '挑战已完成' : '完成后获得成长经验' }}</span>
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

              <div v-else class="growth-empty-state">暂无可参与的成长挑战</div>
            </section>

            <aside class="growth-milestone-card">
              <div class="growth-milestone-title">
                <el-icon><Medal /></el-icon>
                <h2>成长里程</h2>
              </div>
              <div class="growth-milestone-list">
                <div class="growth-milestone-node is-current">
                  <i></i>
                  <div>
                    <strong>Lv.{{ overview.growthLevel }}</strong>
                    <span>当前成长等级</span>
                  </div>
                </div>
                <div class="growth-milestone-node">
                  <i></i>
                  <div>
                    <strong>Lv.{{ overview.growthLevel + 1 }}</strong>
                    <span>再积累 {{ Math.max(0, overview.nextLevelExperience - overview.experience) }} XP</span>
                  </div>
                </div>
              </div>
              <div class="growth-milestone-note">
                <el-icon><Opportunity /></el-icon>
                <span>成长等级当前用于展示，后续会逐步加入更多成长玩法。</span>
              </div>
            </aside>
          </main>
        </template>

        <section v-else class="growth-exam">
          <header class="growth-exam-header">
            <button type="button" class="growth-back-button" @click="exitChallenge">
              <el-icon><ArrowLeft /></el-icon>
              退出挑战
            </button>
            <div class="growth-exam-title">
              <span>成长挑战</span>
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
                  v-for="(question, index) in active.questions"
                  :key="question.id"
                  type="button"
                  :class="{
                    'is-active': index === activeQuestionIndex,
                    'is-answered': answers[question.id],
                  }"
                  @click="selectQuestion(index)"
                >
                  {{ index + 1 }}
                </button>
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
