<template>
  <div class="exam-bank-page shell-page-scroll" v-loading="pageLoading">
    <section v-if="permissionError" class="exam-bank-state exam-bank-state--blocked">
      <el-icon><Lock /></el-icon>
      <strong>无权限访问</strong>
      <span>{{ permissionError }}</span>
    </section>

    <template v-else>
      <header class="exam-bank-hero">
        <div>
          <h1>{{ currentSubject }}题库</h1>
        </div>
        <button type="button" class="exam-ghost-button" @click="toggleManagePanel">
          <el-icon><MagicStick /></el-icon>
          <span>题库管理</span>
        </button>
      </header>

      <section v-if="showManagePanel" class="exam-manage-panel">
        <div class="exam-manage-copy">
          <strong>{{ isExamAdmin ? '导入题库' : '题库切换' }}</strong>
        </div>
        <div class="exam-manage-actions" :class="{ 'is-reader': !isExamAdmin }">
          <label class="exam-manage-field exam-subject-field">
            <el-select
              v-model="subject"
              class="exam-subject-input"
              filterable
              allow-create
              default-first-option
              placeholder="选择考试科目"
              @change="onSubjectChange"
            >
              <el-option v-for="item in subjectOptions" :key="item" :label="item" :value="item" />
              <template #prefix>
                <el-icon><School /></el-icon>
              </template>
            </el-select>
          </label>
          <div v-if="isExamAdmin" class="exam-manage-field exam-upload-field">
            <el-upload
              class="exam-upload"
              accept=".docx,.pdf,.md,.markdown"
              :auto-upload="false"
              :limit="1"
              :file-list="uploadFiles"
              :on-change="onFileChange"
              :on-remove="onFileRemove"
            >
              <el-button class="exam-upload-button">
                <el-icon><UploadFilled /></el-icon>
                上传文件
                <span v-if="selectedFile" class="exam-upload-ok">✓</span>
              </el-button>
            </el-upload>
          </div>
          <div v-if="isExamAdmin" class="exam-manage-field exam-manage-field--action">
            <el-button type="primary" :loading="analyzing" :disabled="!selectedFile || !subject.trim()" @click="analyzeFile">
              <el-icon><MagicStick /></el-icon>
              解析并入库
            </el-button>
          </div>
        </div>
      </section>

      <el-alert
        v-if="errorMessage"
        class="exam-bank-alert"
        type="error"
        :closable="false"
        :title="errorMessage"
        show-icon
      />
      <el-alert
        v-if="warningText"
        class="exam-bank-alert"
        type="warning"
        :closable="false"
        :title="warningText"
        show-icon
      />

      <section v-if="emptyState" class="exam-bank-state">
        <el-icon><Document /></el-icon>
        <strong>暂无题目</strong>
        <span>请先导入 Word 文档，或加载本地毛概题库。</span>
      </section>

      <template v-else-if="modeView === 'overview'">
        <section class="exam-overview-grid">
          <article class="exam-overview-panel">
            <div class="exam-panel-head">
              <span>学习概览</span>
              <strong>{{ completionPercent }}%</strong>
            </div>
            <div class="exam-overview-metrics">
              <div>
                <span>题目总数</span>
                <strong>{{ stats.total }}</strong>
              </div>
              <div>
                <span>选择题</span>
                <strong>{{ stats.choice }}</strong>
              </div>
              <div>
                <span>判断题</span>
                <strong>{{ stats.judgement }}</strong>
              </div>
              <div>
                <span>大题</span>
                <strong>{{ stats.subjective }}</strong>
              </div>
            </div>
          </article>

          <button type="button" class="exam-mode-card exam-mode-card--practice" @click="enterMode('practice')">
            <span class="exam-mode-icon"><DocumentChecked /></span>
            <strong>做题模式</strong>
          </button>

          <button type="button" class="exam-mode-card exam-mode-card--memory" @click="enterMode('memory')">
            <span class="exam-mode-icon"><Reading /></span>
            <strong>背题模式</strong>
          </button>
        </section>

        <section class="exam-topic-grid">
          <article v-for="item in topicCards" :key="item.value" class="exam-topic-card">
            <div class="exam-topic-head">
              <h2>{{ item.title }}</h2>
            </div>
            <div class="exam-topic-progress">
              <span>题目数量</span>
              <b>{{ item.count }}/{{ stats.total }}</b>
            </div>
            <div class="exam-topic-bar">
              <i :style="{ width: item.percent + '%' }"></i>
            </div>
            <button type="button" @click="enterTopic(item.value)">进入练习</button>
          </article>
        </section>
      </template>

      <main v-else class="exam-workspace">
        <section class="exam-main-column">
          <div class="exam-mode-header">
            <button type="button" class="exam-back-button" @click="returnOverview">返回主页</button>
            <div>
              <strong class="exam-question-count">第 {{ activeDisplayNo }} / {{ visibleQuestions.length }} 题</strong>
            </div>
            <div class="exam-mode-switch">
              <button type="button" :class="{ 'is-active': studyMode === 'practice' }" @click="switchMode('practice')">做题</button>
              <button type="button" :class="{ 'is-active': studyMode === 'memory' }" @click="switchMode('memory')">背题</button>
            </div>
          </div>

          <div class="exam-progress-line">
            <i :style="{ width: questionProgress + '%' }"></i>
          </div>

          <article class="exam-question-panel">
            <div class="exam-question-title-row">
              <h3>{{ activeQuestion?.stem }}</h3>
              <div class="exam-title-tags">
                <span v-if="activeQuestion?.needsOptionReview" class="exam-review-pill">需复核</span>
                <span :class="['exam-type-pill', `is-${activeQuestion?.type || 'question'}`]">{{ activeTypeLabel }}</span>
              </div>
            </div>

            <div v-if="isObjectiveActive" class="exam-option-list">
              <button
                v-for="option in activeQuestion?.options || []"
                :key="option.label"
                type="button"
                class="exam-option-card"
                :class="optionCardClass(option)"
                :disabled="isOptionLocked"
                @click="chooseOption(option.label)"
              >
                <span class="exam-option-letter">{{ option.label }}</span>
                <span class="exam-option-text">{{ option.text }}</span>
                <el-icon v-if="isFeedbackAnswerOption(option.label)" class="exam-option-mark"><CircleCheck /></el-icon>
                <el-icon v-else-if="isWrongSelectedOption(option.label)" class="exam-option-mark"><CircleClose /></el-icon>
              </button>

              <el-input
                v-if="activeQuestion?.needsOptionReview"
                v-model="shortAnswer"
                class="exam-short-answer"
                maxlength="80"
                clearable
                placeholder="输入答案"
                :disabled="isOptionLocked"
              />
            </div>

            <div v-else class="exam-subjective-block">
              <el-input
                v-model="subjectiveAnswer"
                type="textarea"
                :rows="7"
                maxlength="2000"
                show-word-limit
                resize="none"
                placeholder="输入你的答案"
                :disabled="studyMode === 'memory' && shouldRevealAnswer"
              />
            </div>

            <div class="exam-action-row">
              <div class="exam-action-left">
                <button
                  v-if="studyMode === 'practice' && isObjectiveActive"
                  type="button"
                  class="exam-action-button is-primary"
                  :disabled="activeObjectiveChecked"
                  @click="submitObjective"
                >
                  <el-icon><Check /></el-icon>
                  <span>{{ activeObjectiveChecked ? '已提交' : '提交答案' }}</span>
                </button>
                <span
                  v-if="activeObjectiveResultText"
                  class="exam-answer-result"
                  :class="activeObjectiveResultClass"
                >
                  {{ activeObjectiveResultText }}
                </span>
                <button
                  v-if="studyMode === 'practice' && isSubjectiveActive"
                  type="button"
                  class="exam-action-button is-primary"
                  :disabled="activeScoring"
                  @click="scoreSubjective"
                >
                  <el-icon><Aim /></el-icon>
                  <span>{{ activeScoring ? '评分中' : 'AI 评分' }}</span>
                </button>
              </div>
              <div class="exam-action-right">
                <button v-if="isExamAdmin" type="button" class="exam-action-button is-neutral" @click="openEditQuestion">
                  <el-icon><Edit /></el-icon>
                  <span>修改题目</span>
                </button>
                <button type="button" class="exam-action-button is-neutral" @click="resetActiveAnswer">
                  <el-icon><RefreshLeft /></el-icon>
                  <span>重做本题</span>
                </button>
                <button v-if="studyMode === 'practice' && !isBookMode" type="button" class="exam-action-button is-blue" @click="markFocusQuestion">
                  <el-icon><DocumentChecked /></el-icon>
                  <span>{{ activeProgress?.focus ? '已加入重点' : '我不熟悉此题' }}</span>
                </button>
                <button v-else-if="isBookMode" type="button" class="exam-action-button is-blue" @click="removeFromCurrentBook">
                  <el-icon><DocumentChecked /></el-icon>
                  <span>{{ bookActionLabel }}</span>
                </button>
              </div>
            </div>

            <section v-if="showExplanationPanel" class="exam-explain-panel">
              <div class="exam-explain-title">
                <el-icon><DocumentChecked /></el-icon>
                <span>{{ isSubjectiveActive ? '参考答案' : '解析' }}</span>
              </div>
              <p v-if="isSubjectiveActive">{{ activeQuestion?.answer || '暂无标准答案' }}</p>
              <p v-if="activeQuestion?.explanation">{{ activeQuestion.explanation }}</p>
            </section>

            <section v-if="activeJudgeResult" class="exam-judge-panel">
              <div class="exam-judge-score">
                <strong>{{ activeJudgeResult.score }}</strong>
                <span>分</span>
              </div>
              <div class="exam-judge-copy">
                <b>{{ activeJudgeResult.passed ? '基本匹配' : '需要补充' }}</b>
                <p>{{ activeJudgeResult.comment }}</p>
              </div>
            </section>
          </article>
        </section>

        <aside class="exam-map-panel">
          <div class="exam-map-head">
            <strong>题目导航</strong>
            <span>{{ mapRangeText }}</span>
          </div>

          <div v-if="showNavFilterRow" class="exam-filter-row">
            <button
              v-for="item in navFilterOptions"
              :key="item.value"
              type="button"
              :class="{ 'is-active': activeMapFilter === item.value }"
              @click="selectFilter(item.value)"
            >
              {{ item.label }}
            </button>
          </div>

          <div class="exam-map-legend">
            <span><i class="is-current"></i>当前</span>
            <span><i class="is-correct"></i>正确</span>
            <span><i class="is-wrong"></i>错误</span>
            <span><i class="is-unread"></i>未答</span>
          </div>

          <div class="exam-map-grid">
            <button
              v-for="item in pagedQuestions"
              :key="item.question.id"
              type="button"
              :class="mapButtonClass(item)"
              @click="selectQuestion(item.index)"
            >
              {{ item.index + 1 }}
            </button>
          </div>

          <div class="exam-map-pager">
            <button type="button" :disabled="mapPage <= 1" @click="prevMapPage">上一页</button>
            <span>第 {{ mapPage }} / {{ mapPageCount }} 页</span>
            <button type="button" :disabled="mapPage >= mapPageCount" @click="nextMapPage">下一页</button>
          </div>
        </aside>
      </main>

      <el-dialog v-model="showEditDialog" title="修改题目" width="680px" class="exam-edit-dialog">
        <div class="exam-edit-form">
          <label class="exam-edit-field">
            <span>题干</span>
            <el-input v-model="editForm.stem" type="textarea" :rows="4" maxlength="1000" show-word-limit resize="none" />
          </label>
          <div class="exam-edit-field">
            <span>选项</span>
            <div class="exam-edit-options">
              <div v-for="(option, index) in editForm.options" :key="index" class="exam-edit-option-row">
                <el-input v-model="option.label" maxlength="2" class="exam-edit-option-label" />
                <el-input v-model="option.text" maxlength="500" />
                <button type="button" @click="removeEditOption(index)">删除</button>
              </div>
              <button type="button" class="exam-edit-add" @click="addEditOption">添加选项</button>
            </div>
          </div>
          <label class="exam-edit-field">
            <span>答案</span>
            <el-input v-model="editForm.answer" maxlength="1000" clearable />
          </label>
          <label class="exam-edit-field">
            <span>解析</span>
            <el-input v-model="editForm.explanation" type="textarea" :rows="3" maxlength="2000" show-word-limit resize="none" />
          </label>
        </div>
        <template #footer>
          <button type="button" class="exam-action-button is-neutral" @click="showEditDialog = false">取消</button>
          <button type="button" class="exam-action-button is-primary" :disabled="editSaving" @click="submitEditQuestion">
            {{ editSaving ? '保存中' : '保存修改' }}
          </button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script src="@scripts/views/ExamQuestionBank.js"></script>
