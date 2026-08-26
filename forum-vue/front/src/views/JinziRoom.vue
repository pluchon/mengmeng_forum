<template>
  <div v-loading="loading" class="jinzi-room-page animate-fade-in">
    <div class="jinzi-room-inner">
      <header class="jinzi-room-head">
        <button type="button" class="jinzi-back-btn" aria-label="离开对局" title="离开对局" @click="backGame">
          <el-icon><HomeFilled /></el-icon>
        </button>
        <div class="jinzi-room-title">
          <h1>井字对局 · BO5</h1>
          <div class="jinzi-room-meta">
            <span>房间号：{{ room.roomId || roomId }}</span>
            <span class="jinzi-meta-divider">/</span>
            <span class="jinzi-bo5-tag">五局三胜制 · 第 {{ room.currentRound }} 局</span>
            <span v-if="room.drawRounds > 0" class="jinzi-draw-tag">平局 {{ room.drawRounds }}</span>
          </div>
        </div>
        <div class="jinzi-room-conn" :class="{ 'is-online': roomSocket.connected.value, 'is-warning': Boolean(peerStateText) }">
          <span />
          {{ peerStateText || (roomSocket.connected.value ? '已连接' : '连接中') }}
        </div>
      </header>

      <main class="jinzi-room-grid">
        <!-- 左侧玩家卡片区 -->
        <aside class="jinzi-player-panel">
          <!-- 我方 -->
          <div class="jinzi-player-card is-mine" :class="{ 'is-turn': primaryPlayerCard.turn }">
            <div class="jinzi-player-info">
              <span class="jinzi-player-label">{{ primaryPlayerCard.label }}</span>
              <div class="jinzi-wins-dots" title="胜局指示灯">
                <span
                  v-for="i in 3"
                  :key="i"
                  class="win-dot"
                  :class="{ 'is-active': i <= (primaryPlayerCard.wins || 0) }"
                />
              </div>
            </div>
            <em class="jinzi-player-time">{{ primaryPlayerCard.time }}</em>
            <i class="jinzi-side-mark" :class="primaryPlayerCard.chess === 1 ? 'is-x' : 'is-o'">
              {{ primaryPlayerCard.chess === 1 ? '×' : '○' }}
            </i>
          </div>

          <!-- 对手 -->
          <div class="jinzi-player-card is-rival" :class="{ 'is-turn': secondaryPlayerCard.turn }">
            <div class="jinzi-player-info">
              <span class="jinzi-player-label">{{ secondaryPlayerCard.label }}</span>
              <div class="jinzi-wins-dots" title="胜局指示灯">
                <span
                  v-for="i in 3"
                  :key="i"
                  class="win-dot"
                  :class="{ 'is-active': i <= (secondaryPlayerCard.wins || 0) }"
                />
              </div>
            </div>
            <em class="jinzi-player-time">{{ secondaryPlayerCard.time }}</em>
            <i class="jinzi-side-mark" :class="secondaryPlayerCard.chess === 1 ? 'is-x' : 'is-o'">
              {{ secondaryPlayerCard.chess === 1 ? '×' : '○' }}
            </i>
          </div>

          <!-- 对手资料卡 -->
          <section class="jinzi-opponent-card" role="button" tabindex="0" @click="openOpponentStats" @keyup.enter="openOpponentStats">
            <span class="jinzi-avatar" :class="{ 'is-vip': opponentProfile?.vip }">
              <img v-if="opponentProfile?.avatarUrl" :src="opponentProfile.avatarUrl" alt="" />
              <b v-else>{{ avatarText(opponentProfile) }}</b>
            </span>
            <div class="jinzi-opponent-meta">
              <strong>{{ opponentProfile?.nickname || opponentProfile?.username || '对手' }}</strong>
              <em>点击查看战绩</em>
            </div>
          </section>

          <!-- 认输 -->
          <el-button
            type="danger"
            plain
            :icon="Flag"
            :loading="surrendering"
            :disabled="isFinished"
            class="jinzi-surrender-btn"
            @click="surrender"
          >
            整场认输
          </el-button>
        </aside>

        <!-- 中间 3x3 棋盘区 -->
        <section class="jinzi-board-shell">
          <!-- 当前对局/回合状态卡片 移动到中间，整体协调 -->
          <div class="jinzi-board-turn-card">
            <div class="jinzi-turn-left">
              <span>当前对局</span>
              <strong>{{ isFinished ? '对局已结束' : (room.roundFinished ? '小局结算中' : (isMyTurn ? '轮到你落子' : '等待对手落子')) }}</strong>
            </div>
            <em v-if="!isFinished && !room.roundFinished" class="jinzi-turn-timer">
              <el-icon><Timer /></el-icon>
              {{ moveTimeText }}
            </em>
            <i v-if="currentTurnChess && !isFinished" :class="['jinzi-turn-mark', currentTurnChess === 1 ? 'is-x' : 'is-o']">
              {{ currentTurnChess === 1 ? '×' : '○' }}
            </i>
          </div>

          <!-- 专属 3x3 极简现代九宫格棋盘 -->
          <div class="jinzi-grid-board" role="grid" aria-label="3x3 井字棋盘">
            <template v-for="(row, rowIndex) in boardRows" :key="rowIndex">
              <button
                v-for="(cell, colIndex) in row"
                :key="`${rowIndex}-${colIndex}`"
                type="button"
                class="jinzi-grid-cell"
                :class="{
                  'can-play': isMyTurn && cell === 0 && !isFinished && !room.roundFinished,
                  'is-winning': isWinningCell(rowIndex, colIndex)
                }"
                :aria-label="`${rowIndex + 1} 行 ${colIndex + 1} 列`"
                @click="play(rowIndex, colIndex)"
              >
                <span
                  v-if="cell"
                  class="jinzi-cell-mark animate-pop"
                  :class="cell === 1 ? 'is-x' : 'is-o'"
                >
                  {{ cell === 1 ? '×' : '○' }}
                </span>
              </button>
            </template>

            <!-- 小局切换提示层 5秒自动进入下一小局 -->
            <div v-if="room.roundFinished && !isFinished" class="jinzi-round-overlay">
              <div class="jinzi-round-card">
                <div class="jinzi-round-icon">{{ room.roundWinnerUserId === room.thisUserId ? '🏆' : (room.roundWinnerUserId ? '💔' : '🤝') }}</div>
                <h3>{{ room.roundWinnerUserId === room.thisUserId ? '本小局获胜！' : (room.roundWinnerUserId ? '本小局失利！' : '本小局平局！') }}</h3>
                <div class="jinzi-round-score-pill">当前比分 {{ myWins }} : {{ opponentWins }}</div>
                <p>{{ roundNextCountdown }} 秒后进入第 {{ room.currentRound + 1 }} 局</p>
              </div>
            </div>

            <!-- 整场比赛终局结果 -->
            <div v-if="isFinished" class="jinzi-match-result">
              <strong>{{ winnerText }}</strong>
              <div class="jinzi-match-score">
                <span>比分 {{ myWins }} : {{ opponentWins }}</span>
              </div>
              <em>{{ finishCountdownText }}</em>
            </div>
          </div>
        </section>

        <!-- 右侧聊天区 -->
        <aside class="jinzi-chat-panel">
          <section class="jinzi-chat-box">
            <div class="jinzi-chat-head">
              <el-icon><ChatDotRound /></el-icon>
              <strong>房间聊天</strong>
            </div>
            <div ref="chatListRef" class="jinzi-chat-list">
              <div
                v-for="(msg, index) in chatMessages"
                :key="index"
                class="jinzi-chat-msg"
                :class="{ 'is-me': msg.userId === room.thisUserId }"
              >
                <span>{{ msg.userId === room.thisUserId ? '我' : participantName(msg.userId) }}</span>
                <img v-if="msg.messageType === 'EMOJI'" :src="msg.emojiUrl || msg.content" alt="表情" />
                <p v-else>{{ msg.content }}</p>
              </div>
              <div v-if="!chatMessages.length" class="jinzi-chat-empty">暂无消息</div>
            </div>
            <div class="jinzi-chat-input">
              <el-input
                v-model="chatText"
                :disabled="!canChat"
                maxlength="200"
                placeholder="发送消息"
                @keyup.enter="sendChat"
              >
                <template #suffix>
                  <PurchasedEmojiPackPopover :disabled="!canChat" @pick="sendEmoji" />
                </template>
              </el-input>
              <el-button class="jinzi-chat-send" :disabled="!canChat || !chatText.trim()" @click="sendChat">
                发送
              </el-button>
            </div>
          </section>
        </aside>
      </main>
    </div>

    <!-- 对手资料弹窗 -->
    <el-dialog v-model="opponentStatsVisible" title="对手井字资料" width="360px" destroy-on-close>
      <div class="jinzi-opponent-dialog">
        <div class="jinzi-dialog-user">
          <span class="jinzi-avatar" :class="{ 'is-vip': opponentProfile?.vip }">
            <img v-if="opponentProfile?.avatarUrl" :src="opponentProfile.avatarUrl" alt="" />
            <b v-else>{{ avatarText(opponentProfile) }}</b>
          </span>
          <strong>{{ opponentProfile?.nickname || opponentProfile?.username || '对手' }}</strong>
        </div>
        <div class="jinzi-opponent-stat-row">
          <span>对局场数</span>
          <strong>{{ opponentProfile?.totalCount ?? 0 }}</strong>
        </div>
        <div class="jinzi-opponent-stat-row">
          <span>胜率</span>
          <strong>{{ opponentProfile?.winRate ?? 0 }}%</strong>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup src="@scripts/views/JinziRoom.js"></script>

<style scoped src="@/assets/styles/jinzi-room.css"></style>
