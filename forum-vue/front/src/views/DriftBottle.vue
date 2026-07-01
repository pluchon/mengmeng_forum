<template>
  <div v-loading="loading" class="drift-page shell-page-scroll">
    <div class="drift-shell">
      <header class="drift-hero">
        <div class="drift-hero-copy">
          <h1 class="drift-title">
            <el-icon class="drift-title-icon">
              <IceDrink />
            </el-icon>
            漂流瓶
          </h1>
        </div>
        <div class="drift-quota-row">
          <div class="drift-quota">
            <span>可扔</span>
            <strong>{{ quota?.createRemaining ?? 0 }}</strong>
          </div>
          <div class="drift-quota">
            <span>可捞</span>
            <strong>{{ quota?.pickRemaining ?? 0 }}</strong>
          </div>
          <div class="drift-quota">
            <span>可回</span>
            <strong>{{ quota?.commentRemaining ?? 0 }}</strong>
          </div>
        </div>
      </header>

      <main class="drift-grid">
        <section class="drift-panel drift-compose">
          <div class="drift-panel-head drift-panel-head-flex">
            <h2>
              <el-icon class="drift-panel-icon">
                <EditPen />
              </el-icon>
              扔一个瓶子
            </h2>
            <el-dropdown trigger="click" @command="createForm.moodType = $event">
              <el-button class="mood-selector-btn" round>
                <span class="mood-selector-text">{{ createForm.moodType }}</span>
                <el-icon class="el-icon--right">
                  <ArrowDown />
                </el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu class="drift-mood-dropdown">
                  <el-dropdown-item v-for="mood in DRIFT_MOODS" :key="mood" :command="mood"
                    :class="{ 'is-active': createForm.moodType === mood }"
                    style="display: flex; justify-content: space-between; align-items: center; min-width: 110px;">
                    <span>{{ mood }}</span>
                    <el-icon class="check-icon"
                      :style="{ marginLeft: '12px', opacity: createForm.moodType === mood ? 1 : 0, transition: 'opacity 0.2s' }">
                      <Check />
                    </el-icon>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>

          <el-input v-model="createForm.content" class="drift-textarea" type="textarea" :rows="8" maxlength="500"
            resize="none" placeholder="把今天想藏起来的话写进瓶子里..." />

          <div class="drift-compose-foot">
            <div class="drift-progress-wrapper" :class="{ 'is-danger': contentCount > 500 }">
              <el-icon>
                <Message />
              </el-icon>
              <div class="drift-progress-bar">
                <div class="drift-progress-fill" :style="{ width: Math.min((contentCount / 500) * 100, 100) + '%' }">
                </div>
              </div>
            </div>
            <el-button type="primary" round :loading="createSubmitting" @click="submitBottle($event)">
              <el-icon style="margin-right: 4px;">
                <Promotion />
              </el-icon>
              扔进海里
            </el-button>
          </div>
        </section>

        <section class="drift-panel drift-current">
          <div class="drift-panel-head">
            <h2>
              <el-icon class="drift-panel-icon">
                <Umbrella />
              </el-icon>
              捞到的瓶子
            </h2>
            <el-button type="primary" round class="drift-action-btn" :icon="Refresh" :loading="pickLoading"
              @click="pickOne">
              捞一个
            </el-button>
          </div>

          <div v-if="hasActiveBottle" class="drift-bottle-card">
            <div class="drift-bottle-seal">
              <el-icon>
                <IceDrink />
              </el-icon>
            </div>
            <div class="drift-bottle-meta">
              <span class="drift-mine-mood">{{ activeBottle.moodType }}</span>
              <span v-if="activeBottle.isOwner">我的瓶子</span>
            </div>

            <p class="drift-bottle-content">{{ activeBottle.content }}</p>

            <div class="drift-bottle-actions">
              <div class="drift-bottle-actions-left">
                <time>{{ formatTime(activeBottle.createTime) }}</time>
              </div>
              <div class="drift-bottle-actions-right">
                <button v-if="!activeBottle.isOwner" type="button" class="drift-icon-btn is-danger" title="举报"
                  @click="reportCurrentBottle">
                  <el-icon>
                    <Warning />
                  </el-icon>
                </button>
              </div>
            </div>

            <div class="drift-comments">
              <div class="drift-comments-head">
                <h3>瓶边回应</h3>
                <span>{{ comments.length }} 条</span>
              </div>
              <div v-if="comments.length" class="drift-comment-list">
                <article v-for="comment in comments" :key="comment.id" class="drift-comment">
                  <div class="drift-comment-main">
                    <div class="drift-comment-name">
                      {{ comment.anonymousName }}
                      <span v-if="comment.isMine">我</span>
                    </div>
                    <p>{{ comment.content }}</p>
                    <time>{{ formatTime(comment.createTime) }}</time>
                  </div>
                  <button v-if="!comment.isMine" type="button" class="drift-icon-btn" title="举报评论"
                    @click="reportComment(comment)">
                    <el-icon>
                      <Warning />
                    </el-icon>
                  </button>
                </article>
              </div>
              <p v-else class="drift-comment-empty">还没有人回应</p>

              <div class="drift-input-area" style="margin-top: 16px;">
                <el-input v-model="commentContent" type="textarea" :rows="3" placeholder="写一句温和的回应..." resize="none"
                  maxlength="200" :show-word-limit="false" class="drift-textarea" style="margin-bottom: 12px;" />
                <div class="drift-compose-foot">
                  <div class="drift-progress-wrapper" :class="{ 'is-danger': commentCount >= 200 }">
                    <div class="drift-progress-bar">
                      <div class="drift-progress-fill" :style="{ width: (commentCount / 200 * 100) + '%' }"></div>
                    </div>
                    <span>{{ commentCount }}/200</span>
                  </div>
                  <el-button type="primary" round class="drift-action-btn" :loading="commentSubmitting"
                    @click="submitComment">
                    回应
                  </el-button>
                </div>
              </div>
            </div>
          </div>

          <div v-else class="drift-empty-sea">
            <div class="drift-empty-scene" aria-hidden="true">
              <div class="drift-empty-orbit drift-empty-orbit-one"></div>
              <div class="drift-empty-orbit drift-empty-orbit-two"></div>
              <div class="drift-floating-bottle">
                <el-icon>
                  <IceDrink />
                </el-icon>
              </div>
              <div class="drift-empty-mark">
                <el-icon>
                  <Sunny />
                </el-icon>
              </div>
            </div>
            <h3>{{ currentBottleEmptyText }}</h3>
          </div>
        </section>

        <section class="drift-panel drift-mine">
          <div class="drift-panel-head">
            <h2>
              <el-icon class="drift-panel-icon">
                <ChatDotRound />
              </el-icon>
              我的瓶子
            </h2>
          </div>

          <div v-loading="mineLoading" class="drift-mine-list">
            <div v-for="row in myBottles" :key="row.id" class="drift-mine-wrapper">
              <div class="drift-mine-row" @click="openMyBottle(row)">
                <el-icon class="drift-mine-expand-icon">
                  <ArrowDown v-if="expandedBottles[row.id]" />
                  <ArrowRight v-else />
                </el-icon>
                <span class="drift-mine-mood">{{ row.moodType }}</span>
                <time class="drift-mine-time">{{ formatTime(row.createTime) }}</time>
                <span class="drift-mine-status">{{ row.statusText }}</span>
                <span class="drift-mine-preview">{{ row.content }}</span>
                <span class="drift-mine-replies">{{ row.commentCount || 0 }} 条回应</span>
                <button type="button" class="drift-icon-btn is-danger" title="删除" @click.stop="deleteMine(row)">
                  <el-icon>
                    <Delete />
                  </el-icon>
                </button>
              </div>

              <!-- Expanded Detail -->
              <div v-if="expandedBottles[row.id]" class="drift-mine-detail">
                <div class="drift-mine-content">{{ row.fullContent }}</div>

                <div class="drift-mine-divider"></div>

                <div class="drift-mine-comments-frame">
                  <template v-if="row.comments && row.comments.length > 0">
                    <div class="drift-mine-comments-list">
                      <template v-for="(comment, idx) in pagedComments(row)" :key="comment.id">
                        <div class="drift-mine-comment-item">
                          <div class="drift-mine-comment-main">
                            <p class="drift-mine-comment-body">
                              <span class="drift-mine-comment-author">{{ comment.anonymousName }}：</span>{{
                                comment.content }}
                            </p>
                            <time class="drift-mine-comment-time">{{ formatTime(comment.createTime) }}</time>
                          </div>
                          <button v-if="!comment.isMine" type="button" class="drift-icon-btn" title="举报评论"
                            @click="reportComment(comment)">
                            <el-icon>
                              <Warning />
                            </el-icon>
                          </button>
                        </div>
                        <div v-if="idx < pagedComments(row).length - 1" class="drift-mine-comment-divider"></div>
                      </template>
                    </div>
                    <el-pagination v-if="row.comments.length > commentPageSize" layout="prev, pager, next" small
                      :page-size="commentPageSize" :total="row.comments.length"
                      :current-page="commentPages[row.id] || 1"
                      @current-change="(p) => onCommentPageChange(row.id, p)" />
                  </template>
                  <div v-else class="drift-mine-no-comments">暂无回复~</div>
                </div>
              </div>
            </div>

            <div v-if="!mineLoading && !myBottles.length" class="drift-mine-empty">
              <el-icon class="drift-empty-icon">
                <Box />
              </el-icon>
              <p>你还没有扔过瓶子</p>
            </div>
          </div>

          <el-pagination v-if="myTotal > myPageSize" v-model:current-page="myPage" layout="prev, pager, next"
            :page-size="myPageSize" :total="myTotal" small @current-change="onMinePageChange" />
        </section>
      </main>
    </div>

    <!-- Ocean Wave Animation Element -->
    <div class="ocean-waves">
      <div class="wave wave1"></div>
      <div class="wave wave2"></div>
      <div class="wave wave3"></div>
    </div>
  </div>
</template>

<script setup>
import { useDriftBottle } from '@scripts/views/DriftBottle'

const {
  ChatDotRound,
  Compass,
  DRIFT_MOODS,
  Delete,
  Refresh,
  Warning,
  Sunny,
  Umbrella,
  EditPen,
  Document,
  Message,
  Promotion,
  ArrowDown,
  ArrowRight,
  IceDrink,
  Check,
  Box,
  activeBottle,
  commentContent,
  commentCount,
  commentPages,
  commentPageSize,
  commentSubmitting,
  comments,
  contentCount,
  createForm,
  createSubmitting,
  currentBottleEmptyText,
  deleteMine,
  expandedBottles,
  formatTime,
  hasActiveBottle,
  loading,
  mineLoading,
  moodDialogVisible,
  myBottles,
  myPage,
  myPageSize,
  myTotal,
  onCommentPageChange,
  onMinePageChange,
  openMyBottle,
  pagedComments,
  pickLoading,
  pickOne,
  quota,
  reportComment,
  reportCurrentBottle,
  submitBottle,
  submitComment,
} = useDriftBottle()
</script>

<style scoped src="@/assets/styles/drift-bottle.css"></style>
