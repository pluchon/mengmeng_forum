<template>
  <div v-loading="loading" class="tetris-room-page animate-fade-in">
    <div class="tetris-room-inner">
      <header class="tetris-room-head">
        <button type="button" class="game-icon-btn game-icon-btn--dark" aria-label="返回游戏中心" title="返回游戏中心" @click="backCenter">
          <el-icon><HomeFilled /></el-icon>
        </button>
        <div class="tetris-room-title">
          <h1>俄罗斯方块<span class="tetris-mode-tag"> · {{ modeLabel }}</span></h1>
        </div>
        <div class="tetris-room-conn" :class="{ 'is-online': engine.playing.value && !engine.pause.value }">
          <span />
          {{ statusText }}
        </div>
      </header>

      <main class="tetris-stage">
        <aside class="tetris-rail tetris-rail--left">
          <div class="tetris-rail-card tetris-piece-card">
            <span class="tetris-rail-label">HOLD</span>
            <div class="tetris-piece-preview">
              <canvas ref="holdRef" width="96" height="96" class="tetris-piece-canvas" aria-label="暂存方块" />
            </div>
          </div>

          <div class="tetris-rail-card tetris-score-card">
            <span class="tetris-rail-label">分数</span>
            <strong>{{ engine.points.value }}</strong>
          </div>

          <div class="tetris-rail-card tetris-time-card">
            <span class="tetris-rail-label">时间</span>
            <strong>{{ elapsedText }}</strong>
          </div>
        </aside>

        <section class="tetris-board-shell">
          <div class="tetris-board-area">
            <canvas
              ref="boardRef"
              class="tetris-board-canvas"
              :width="BOARD_WIDTH"
              :height="BOARD_HEIGHT"
              aria-label="俄罗斯方块主棋盘"
            />
            <div v-if="engine.pause.value && engine.playing.value" class="tetris-board-overlay">
              <strong>暂停中</strong>
            </div>
            <div v-else-if="engine.gameOver.value" class="tetris-board-overlay">
              <strong>本局结束</strong>
              <el-button class="tetris-btn-star" :icon="RefreshRight" @click="restartGame">再来一局</el-button>
            </div>
            <div
              v-if="engine.comboFlash.value >= 2"
              class="tetris-combo-flash"
              :class="{ 'is-triple': engine.comboFlash.value >= 3 }"
              aria-hidden="true"
            >
              ×{{ engine.comboFlash.value }}
            </div>
          </div>
        </section>

        <aside class="tetris-rail tetris-rail--right">
          <div class="tetris-rail-card tetris-piece-card">
            <span class="tetris-rail-label">NEXT</span>
            <div class="tetris-piece-preview">
              <canvas ref="nextRef" width="96" height="96" class="tetris-piece-canvas" aria-label="下一个方块" />
            </div>
          </div>

          <div class="tetris-rail-actions">
            <el-button
              class="tetris-btn-star"
              size="large"
              :icon="engine.pause.value ? VideoPlay : VideoPause"
              :disabled="!engine.playing.value"
              @click="togglePause"
            >
              {{ engine.pause.value ? '继续' : '暂停' }}
            </el-button>
            <el-button class="tetris-btn-star" size="large" :icon="RefreshRight" @click="restartGame">重开</el-button>
          </div>

          <div class="tetris-rail-card tetris-keys tetris-keys--relaxed">
            <strong>操作说明</strong>
            <div class="tetris-keys-grid">
              <div class="tetris-key-row is-full">
                <span class="tetris-key-badges"><kbd>←</kbd><kbd>→</kbd></span>
                <span class="tetris-key-label">改变位置</span>
              </div>
              <div class="tetris-key-row is-full">
                <span class="tetris-key-badges"><kbd>↑</kbd></span>
                <span class="tetris-key-label">旋转方块</span>
              </div>
              <div class="tetris-key-row is-full">
                <span class="tetris-key-badges"><kbd>↓</kbd></span>
                <span class="tetris-key-label">加速下移</span>
              </div>
              <div class="tetris-key-row is-full">
                <span class="tetris-key-badges"><kbd>空格</kbd></span>
                <span class="tetris-key-label">直接下落</span>
              </div>
              <div class="tetris-key-row is-full">
                <span class="tetris-key-badges"><kbd>S</kbd></span>
                <span class="tetris-key-label">暂存</span>
              </div>
            </div>
          </div>
        </aside>
      </main>
    </div>

    <el-dialog
      v-model="gameOverVisible"
      width="440px"
      align-center
      :show-close="false"
      :close-on-click-modal="false"
      class="tetris-over-dialog"
    >
      <div class="tetris-over-card">
        <h2 class="tetris-over-title">游戏结束</h2>
        
        <div class="tetris-over-body">
          <div v-if="settleResult && settleResult.newBest" class="tetris-over-best-tag">
            <span class="best-shimmer" />
            ★ NEW 刷新个人纪录
          </div>
          <div class="tetris-over-score-center">
            <span class="tetris-over-score-label">本局得分</span>
            <!-- 以服务端回执为准：重放校验上线后，服务端算出的分数可能与本地不同 -->
            <strong class="tetris-over-score-number">
              {{ settleResult ? settleResult.score : engine.points.value }}
            </strong>
          </div>
          <div v-if="settleResult" class="tetris-over-best">
            历史最高 <strong>{{ settleResult.bestScore }}</strong>
          </div>
          <p v-if="settling" class="tetris-over-saving">成绩保存中…</p>
          <div v-else-if="settleError" class="tetris-over-error">
            <span>{{ settleError }}</span>
            <button type="button" class="tetris-over-retry" @click="retrySettle">重试</button>
          </div>
        </div>

        <div class="tetris-over-actions">
          <el-button
            class="tetris-btn-star tetris-action-btn primary-btn"
            type="primary"
            :disabled="settling"
            @click="restartGame"
          >
            再来一局
          </el-button>
          <el-button class="tetris-over-close tetris-action-btn ghost-btn" @click="backCenter">
            返回游戏中心
          </el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup src="@scripts/views/TetrisGame.js"></script>
<style scoped src="@/assets/styles/tetris-game.css"></style>
<style src="@/assets/styles/tetris-game-over-dialog.css"></style>
