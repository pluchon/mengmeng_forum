<template>
  <div v-loading="loading" class="drift-page shell-page-scroll">
    <div class="drift-shell">
      <section class="drift-hero">
        <div class="drift-hero-copy">
          <div class="drift-kicker">匿名树洞</div>
          <h1>漂流瓶</h1>
          <p>写下不能放进广场的话，交给一次随机相遇。</p>
        </div>
        <div class="drift-quota-row">
          <div class="drift-quota">
            <span>可扔</span>
            <strong>{{ quota?.createRemaining ?? '--' }}</strong>
          </div>
          <div class="drift-quota">
            <span>可捞</span>
            <strong>{{ quota?.pickRemaining ?? '--' }}</strong>
          </div>
          <div class="drift-quota">
            <span>可回</span>
            <strong>{{ quota?.commentRemaining ?? '--' }}</strong>
          </div>
        </div>
      </section>

      <main class="drift-grid">
        <section class="drift-panel drift-compose">
          <div class="drift-panel-head">
            <div>
              <h2>扔一个瓶子</h2>
              <p>20 到 500 字，不能包含外链。</p>
            </div>
            <el-icon><Compass /></el-icon>
          </div>

          <div class="drift-mood-row">
            <button
              v-for="mood in DRIFT_MOODS"
              :key="mood"
              type="button"
              class="drift-mood"
              :class="{ 'is-active': createForm.moodType === mood }"
              @click="createForm.moodType = mood"
            >
              {{ mood }}
            </button>
          </div>

          <el-input
            v-model="createForm.content"
            class="drift-textarea"
            type="textarea"
            :rows="8"
            maxlength="500"
            resize="none"
            placeholder="把今天想藏起来的话写进瓶子里..."
          />
          <div class="drift-compose-foot">
            <span :class="{ 'is-danger': contentCount > 500 || contentCount < 20 }">
              {{ contentCount }}/500
            </span>
            <el-button type="primary" round :loading="createSubmitting" @click="submitBottle">
              扔进海里
            </el-button>
          </div>
        </section>

        <section class="drift-panel drift-current">
          <div class="drift-panel-head">
            <div>
              <h2>捞到的瓶子</h2>
              <p>不会展示瓶主真实身份。</p>
            </div>
            <el-button round :icon="Refresh" :loading="pickLoading" @click="pickOne">
              捞一个
            </el-button>
          </div>

          <div v-if="hasActiveBottle" class="drift-bottle-card">
            <div class="drift-bottle-meta">
              <span>{{ activeBottle.moodType }}</span>
              <span>{{ formatTime(activeBottle.createTime) }}</span>
              <span v-if="activeBottle.isOwner">我的瓶子</span>
            </div>
            <p class="drift-bottle-content">{{ activeBottle.content }}</p>
            <div class="drift-bottle-actions">
              <span>{{ activeBottle.commentCount || 0 }} 条回应</span>
              <span>{{ activeBottle.pickedCount || 0 }} 次打捞</span>
              <button
                v-if="!activeBottle.isOwner"
                type="button"
                class="drift-link-btn"
                @click="reportCurrentBottle"
              >
                举报
              </button>
            </div>
          </div>

          <div v-else class="drift-empty-sea">
            <div class="drift-empty-mark">~</div>
            <p>{{ currentBottleEmptyText }}</p>
            <el-button type="primary" round :loading="pickLoading" @click="pickOne">现在去捞</el-button>
          </div>

          <div v-if="hasActiveBottle" class="drift-comments">
            <div class="drift-comments-head">
              <h3>匿名回应</h3>
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
                <button
                  v-if="!comment.isMine"
                  type="button"
                  class="drift-icon-btn"
                  title="举报评论"
                  @click="reportComment(comment)"
                >
                  <el-icon><Warning /></el-icon>
                </button>
              </article>
            </div>
            <p v-else class="drift-comment-empty">还没有人回应。</p>

            <div class="drift-comment-box">
              <el-input
                v-model="commentContent"
                type="textarea"
                :rows="3"
                maxlength="200"
                resize="none"
                placeholder="写一句温和的回应..."
              />
              <div class="drift-comment-foot">
                <span>{{ commentCount }}/200</span>
                <el-button type="primary" round :loading="commentSubmitting" @click="submitComment">
                  回应
                </el-button>
              </div>
            </div>
          </div>
        </section>

        <section class="drift-panel drift-mine">
          <div class="drift-panel-head">
            <div>
              <h2>我的瓶子</h2>
              <p>只展示给你自己管理。</p>
            </div>
            <el-icon><ChatDotRound /></el-icon>
          </div>

          <div v-loading="mineLoading" class="drift-mine-list">
            <article v-for="row in myBottles" :key="row.id" class="drift-mine-item" @click="openMyBottle(row)">
              <div class="drift-mine-top">
                <span>{{ row.moodType }}</span>
                <time>{{ formatTime(row.createTime) }}</time>
              </div>
              <p>{{ row.content }}</p>
              <div class="drift-mine-bottom">
                <span>{{ row.statusText }}</span>
                <span>{{ row.commentCount || 0 }} 条回应</span>
                <button type="button" class="drift-icon-btn is-danger" title="删除" @click.stop="deleteMine(row)">
                  <el-icon><Delete /></el-icon>
                </button>
              </div>
              <div v-if="row.latestComment" class="drift-latest">最近：{{ row.latestComment }}</div>
            </article>
            <p v-if="!mineLoading && !myBottles.length" class="drift-comment-empty">你还没有扔过瓶子。</p>
          </div>

          <el-pagination
            v-if="myTotal > myPageSize"
            v-model:current-page="myPage"
            layout="prev, pager, next"
            :page-size="myPageSize"
            :total="myTotal"
            small
            @current-change="onMinePageChange"
          />
        </section>
      </main>
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
  activeBottle,
  commentContent,
  commentCount,
  commentSubmitting,
  comments,
  contentCount,
  createForm,
  createSubmitting,
  currentBottleEmptyText,
  deleteMine,
  formatTime,
  hasActiveBottle,
  loading,
  mineLoading,
  myBottles,
  myPage,
  myPageSize,
  myTotal,
  onMinePageChange,
  openMyBottle,
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
