<template>
  <div v-loading="loading" class="tetris-room-page tetris-pk-room-page animate-fade-in">
    <div class="tetris-room-inner">
      <header class="tetris-room-head">
        <button type="button" class="game-icon-btn game-icon-btn--dark" aria-label="离开对局" title="离开对局" @click="backGame">
          <el-icon><HomeFilled /></el-icon>
        </button>
        <div class="tetris-room-title">
          <h1>俄罗斯方块<span class="tetris-mode-tag"> · 在线PK模式</span></h1>
        </div>
        <div class="tetris-room-conn" :class="{ 'is-online': roomSocket.connected.value }">
          <span />
          {{ roomSocket.connected.value ? '已连接' : '连接中' }}
        </div>
      </header>

      <div class="tetris-pk-bar">
        <div class="tetris-pk-bar-red" :style="{ width: `${pkBarLeftPercent}%` }">
          <span>{{ room.redScore ?? 0 }}</span>
        </div>
        <div class="tetris-pk-bar-seam" aria-hidden="true">
          <span class="tetris-pk-bar-seam-core" />
          <span class="tetris-pk-bar-seam-shimmer" />
          <span class="tetris-pk-bar-seam-spark" />
          <span class="tetris-pk-bar-seam-flare" />
        </div>
        <div class="tetris-pk-bar-blue">
          <span>{{ room.blueScore ?? 0 }}</span>
        </div>
      </div>

      <main class="tetris-stage tetris-pk-stage">
        <aside class="tetris-rail tetris-rail--left">
          <div v-if="isPlayer" class="tetris-rail-card tetris-piece-card">
            <span class="tetris-rail-label">HOLD</span>
            <div class="tetris-piece-preview">
              <canvas ref="holdRef" width="96" height="96" class="tetris-piece-canvas" aria-label="暂存方块" />
            </div>
          </div>

          <div class="tetris-rail-card tetris-score-card" :class="{ 'is-dual': isSpectator }">
            <span class="tetris-rail-label">分数</span>
            <template v-if="isSpectator">
              <div class="tetris-dual-score">
                <div class="tetris-dual-score-row is-red">
                  <em>左 · 红方</em>
                  <strong>{{ room.redScore ?? 0 }}</strong>
                </div>
                <div class="tetris-dual-score-row is-blue">
                  <em>右 · 蓝方</em>
                  <strong>{{ room.blueScore ?? 0 }}</strong>
                </div>
              </div>
            </template>
            <strong v-else>{{ myScore }}</strong>
          </div>

          <div class="tetris-rail-card tetris-time-card">
            <span class="tetris-rail-label">时间</span>
            <strong>{{ elapsedText }}</strong>
          </div>

          <section
            v-if="isPlayer && opponentProfile"
            class="gobang-opponent-card tetris-opponent-card"
            role="button"
            tabindex="0"
            @click="openOpponentStats"
            @keyup.enter="openOpponentStats"
          >
            <span class="gobang-avatar is-large" :class="{ 'is-vip': opponentProfile.vip }">
              <img v-if="opponentProfile.avatarUrl" :src="opponentProfile.avatarUrl" alt="" />
              <b v-else>{{ avatarText(opponentProfile) }}</b>
            </span>
            <div>
              <strong>{{ opponentProfile.nickname || opponentProfile.username || '对手' }}</strong>
              <em>对局玩家</em>
            </div>
          </section>

          <section v-else-if="isSpectator" class="tetris-spectator-players">
            <span class="tetris-spectator-players-title">观战选手</span>
            <button
              v-if="redPlayer"
              type="button"
              class="tetris-spectator-player-row is-red"
              @click="openPlayerStats(redPlayer)"
            >
              <span class="gobang-avatar">
                <img v-if="redPlayer.avatarUrl" :src="redPlayer.avatarUrl" alt="" />
                <b v-else>{{ avatarText(redPlayer) }}</b>
              </span>
              <div>
                <em>红方</em>
                <strong>{{ redPlayer.nickname || redPlayer.username || '红方' }}</strong>
              </div>
            </button>
            <button
              v-if="bluePlayer"
              type="button"
              class="tetris-spectator-player-row is-blue"
              @click="openPlayerStats(bluePlayer)"
            >
              <span class="gobang-avatar">
                <img v-if="bluePlayer.avatarUrl" :src="bluePlayer.avatarUrl" alt="" />
                <b v-else>{{ avatarText(bluePlayer) }}</b>
              </span>
              <div>
                <em>蓝方</em>
                <strong>{{ bluePlayer.nickname || bluePlayer.username || '蓝方' }}</strong>
              </div>
            </button>
          </section>

          <el-button
            v-if="isPlayer"
            class="tetris-btn-star tetris-surrender-btn"
            :icon="Flag"
            :loading="surrendering"
            :disabled="isFinished"
            @click="surrender"
          >
            认输
          </el-button>
        </aside>

        <section class="tetris-board-shell tetris-pk-board-shell">
          <div class="tetris-pk-duel">
            <div class="tetris-pk-board-col">
              <span class="tetris-pk-board-label is-red">{{ leftLabel }}</span>
              <canvas
                ref="myBoardRef"
                class="tetris-board-canvas"
                :width="BOARD_WIDTH"
                :height="BOARD_HEIGHT"
                aria-label="左侧棋盘"
              />
            </div>
            <div class="tetris-pk-board-col">
              <span class="tetris-pk-board-label is-blue">{{ rightLabel }}</span>
              <canvas
                ref="opponentBoardRef"
                class="tetris-board-canvas"
                :width="BOARD_WIDTH"
                :height="BOARD_HEIGHT"
                aria-label="右侧棋盘"
              />
            </div>
          </div>
          <div v-if="isFinished" class="tetris-pk-result-overlay">
            <div class="tetris-pk-result-card">
              <strong>{{ winnerText }}</strong>
              <em>{{ finishCountdown }} 秒后返回匹配页</em>
            </div>
          </div>
        </section>

        <aside class="tetris-rail tetris-rail--right tetris-pk-chat-rail">
          <div v-if="isPlayer" class="tetris-rail-card tetris-piece-card">
            <span class="tetris-rail-label">NEXT</span>
            <div class="tetris-piece-preview">
              <canvas ref="nextRef" width="96" height="96" class="tetris-piece-canvas" aria-label="下一个方块" />
            </div>
          </div>

          <section class="gobang-chat-box tetris-pk-chat-panel">
            <div class="gobang-chat-head">
              <el-icon><ChatDotRound /></el-icon>
              <strong>房间聊天</strong>
            </div>
            <div ref="chatListRef" class="gobang-chat-list">
              <div
                v-for="(msg, index) in chatMessages"
                :key="index"
                class="gobang-chat-msg"
                :class="{ 'is-me': msg.userId === room.thisUserId }"
              >
                <span>{{ msg.userId === room.thisUserId ? '我' : participantName(msg.userId) }}</span>
                <img v-if="msg.messageType === 'EMOJI'" :src="msg.emojiUrl || msg.content" alt="表情" />
                <p v-else>{{ msg.content }}</p>
              </div>
              <div v-if="!chatMessages.length" class="gobang-chat-empty">暂无消息</div>
            </div>
            <div v-if="isPlayer" class="gobang-chat-input">
              <el-input
                v-model="chatText"
                :disabled="!canChat"
                placeholder="说点什么…"
                maxlength="200"
                @keyup.enter="sendChat"
              >
                <template #suffix>
                  <PurchasedEmojiPackPopover :disabled="!canChat" @pick="sendChatEmoji" />
                </template>
              </el-input>
              <el-button class="game-chat-send" :disabled="!canChat || !chatText.trim()" @click="sendChat">
                发送
              </el-button>
            </div>
          </section>

          <div v-if="isPlayer" class="tetris-rail-card tetris-keys">
            <strong>操作说明</strong>
            <div class="tetris-keys-grid">
              <div class="tetris-key-row">
                <span class="tetris-key-badges"><kbd>←</kbd><kbd>→</kbd></span>
                <span class="tetris-key-label">改变位置</span>
              </div>
              <div class="tetris-key-row">
                <span class="tetris-key-badges"><kbd>↑</kbd></span>
                <span class="tetris-key-label">旋转方块</span>
              </div>
              <div class="tetris-key-row">
                <span class="tetris-key-badges"><kbd>↓</kbd></span>
                <span class="tetris-key-label">加速下移</span>
              </div>
              <div class="tetris-key-row">
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

    <el-dialog v-model="playerStatsVisible" :title="playerStatsTitle" width="360px" destroy-on-close>
      <div v-if="selectedPlayer" class="gobang-opponent-stats-dialog">
        <div class="gobang-dialog-user">
          <span class="gobang-avatar" :class="{ 'is-vip': selectedPlayer.vip }">
            <img v-if="selectedPlayer.avatarUrl" :src="selectedPlayer.avatarUrl" alt="" />
            <b v-else>{{ avatarText(selectedPlayer) }}</b>
          </span>
          <strong>{{ selectedPlayer.nickname || selectedPlayer.username || '选手' }}</strong>
        </div>
        <div class="gobang-opponent-stat-row">
          <span>对局场数</span>
          <strong>{{ selectedPlayer.totalCount ?? 0 }}</strong>
        </div>
        <div class="gobang-opponent-stat-row">
          <span>胜率</span>
          <strong>{{ selectedPlayer.winRate ?? 0 }}%</strong>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup src="@scripts/views/TetrisPkRoom.js"></script>

<style scoped src="@/assets/styles/tetris-game.css"></style>
<style scoped src="@/assets/styles/tetris-pk-room.css"></style>
<style scoped src="@/assets/styles/gobang-room.css"></style>
