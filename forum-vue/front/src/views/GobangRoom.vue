<template>
  <div v-loading="loading" class="gobang-room-page animate-fade-in">
    <div class="gobang-room-inner">
      <header class="gobang-room-head">
        <button type="button" class="game-icon-btn game-icon-btn--dark" aria-label="离开对局" title="离开对局" @click="backGame">
          <el-icon><HomeFilled /></el-icon>
        </button>
        <div class="gobang-room-title">
          <h1>五子棋对局</h1>
          <span>房间号：{{ room.roomId || roomId }}</span>
        </div>
        <div class="gobang-room-conn" :class="{ 'is-online': roomSocket.connected.value, 'is-warning': Boolean(peerStateText) }">
          <span />
          {{ peerStateText || (roomSocket.connected.value ? '已连接' : '连接中') }}
        </div>
      </header>

      <main class="gobang-room-grid">
        <aside class="gobang-player-panel">
          <div class="gobang-player-card is-mine" :class="{ 'is-turn': primaryPlayerCard.turn }">
            <span class="gobang-player-label">{{ primaryPlayerCard.label }}</span>
            <strong>{{ primaryPlayerCard.title }}</strong>
            <em>{{ primaryPlayerCard.time }}</em>
            <i :class="['gobang-side-piece', primaryPlayerCard.chess === 2 ? 'is-white' : 'is-black']" />
          </div>

          <div class="gobang-player-card is-rival" :class="{ 'is-turn': secondaryPlayerCard.turn }">
            <span class="gobang-player-label">{{ secondaryPlayerCard.label }}</span>
            <strong>{{ secondaryPlayerCard.title }}</strong>
            <em>{{ secondaryPlayerCard.time }}</em>
            <i :class="['gobang-side-piece', secondaryPlayerCard.chess === 2 ? 'is-white' : 'is-black']" />
          </div>

          <!-- 只报人数不报名单：名单会把观战者的昵称头像战绩推给房里所有人 -->
          <div class="gobang-spectator-count is-static">
            <el-icon><UserFilled /></el-icon>
            观战人数 {{ room.spectatorCount || 0 }}
          </div>

          <section v-if="isSpectator" class="gobang-observer-card">
            <strong>观战视角</strong>
            <span>不参与落子</span>
          </section>

          <section v-else class="gobang-opponent-card" :class="{ 'is-ai-thinking': isAiThinking }" role="button" tabindex="0" @click="openOpponentStats" @keyup.enter="openOpponentStats">
            <span class="gobang-avatar is-large" :class="{ 'is-vip': opponentProfile?.vip, 'is-ai': opponentProfile?.ai }">
              <img v-if="opponentProfile?.avatarUrl" :src="opponentProfile.avatarUrl" alt="" />
              <img v-else-if="opponentProfile?.ai" :src="aiModelIcon(opponentProfile)" alt="" class="gobang-ai-icon" />
              <b v-else>{{ avatarText(opponentProfile) }}</b>
            </span>
            <div>
              <strong>{{ opponentProfile?.nickname || opponentProfile?.username || '对手' }}</strong>
              <em>{{ opponentProfile?.ai ? opponentProfile.aiModelName : '对局玩家' }}</em>
            </div>
          </section>

          <el-button
            type="danger"
            plain
            :icon="Flag"
            :loading="surrendering"
            :disabled="isFinished || isSpectator"
            @click="surrender"
          >
            认输
          </el-button>
        </aside>

        <section class="gobang-board-shell">
          <div class="gobang-board-status" :class="{ 'is-my-turn': isMyTurn, 'is-finished': isFinished, 'is-ai-thinking': isAiThinking }">
            <span>{{ boardStatusText }}</span>
            <em>
              <el-icon><Timer /></el-icon>
              {{ moveTimeText }}
            </em>
          </div>
          <div class="gobang-board" role="grid" aria-label="五子棋棋盘">
            <template v-for="(row, rowIndex) in boardRows" :key="rowIndex">
              <button
                v-for="(cell, colIndex) in row"
                :key="`${rowIndex}-${colIndex}`"
                type="button"
                class="gobang-cell"
                :class="{
                  'can-play': isMyTurn && cell === 0 && !isFinished && !isSpectator,
                  'is-winning': isWinningCell(rowIndex, colIndex)
                }"
                :style="{ '--row': rowIndex, '--col': colIndex }"
                :aria-label="`${rowIndex + 1} 行 ${colIndex + 1} 列`"
                @click="play(rowIndex, colIndex)"
              >
                <span
                  v-if="cell"
                  :class="['gobang-piece', cell === 1 ? 'is-black' : 'is-white']"
                />
              </button>
            </template>
            <div v-if="isFinished" class="gobang-board-result">
              <strong>{{ winnerText }}</strong>
              <em>{{ finishCountdownText }}</em>
            </div>
          </div>
        </section>

        <aside class="gobang-chat-panel">
          <div class="gobang-room-side-card">
            <span>当前回合</span>
            <strong>{{ isFinished ? '本局结束' : '轮到落子' }}</strong>
            <i v-if="currentTurnChess" :class="['gobang-turn-piece', currentTurnChess === 1 ? 'is-black' : 'is-white']" />
          </div>

          <section class="gobang-chat-box">
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
            <div v-if="!isSpectator" class="gobang-chat-input gobang-chat-input--stacked">
              <div class="gobang-chat-field">
                <el-input
                  v-model="chatText"
                  type="textarea"
                  :autosize="{ minRows: 1, maxRows: 3 }"
                  resize="none"
                  :disabled="!canChat"
                  maxlength="200"
                  placeholder="说点什么…"
                  @keydown.enter.exact.prevent="sendChat"
                />
                <span class="gobang-chat-emoji">
                  <PurchasedEmojiPackPopover :disabled="!canChat" @pick="sendEmoji" />
                </span>
              </div>
              <el-button class="game-chat-send" :disabled="!canChat || !chatText.trim()" @click="sendChat">
                发送
              </el-button>
            </div>
          </section>
        </aside>
      </main>
    </div>

    <el-dialog v-model="opponentStatsVisible" title="对手五子棋资料" width="360px" destroy-on-close>
      <div class="gobang-opponent-stats-dialog">
        <div class="gobang-dialog-user">
          <span class="gobang-avatar" :class="{ 'is-vip': opponentProfile?.vip, 'is-ai': opponentProfile?.ai }">
            <img v-if="opponentProfile?.avatarUrl" :src="opponentProfile.avatarUrl" alt="" />
            <img v-else-if="opponentProfile?.ai" :src="aiModelIcon(opponentProfile)" alt="" class="gobang-ai-icon" />
            <b v-else>{{ avatarText(opponentProfile) }}</b>
          </span>
          <strong>{{ opponentProfile?.nickname || opponentProfile?.username || '对手' }}</strong>
        </div>
        <div class="gobang-opponent-stat-row">
          <span>对局场数</span>
          <strong>{{ opponentProfile?.totalCount ?? 0 }}</strong>
        </div>
        <div class="gobang-opponent-stat-row">
          <span>胜率</span>
          <strong>{{ opponentProfile?.winRate ?? 0 }}%</strong>
        </div>
      </div>
    </el-dialog>

  </div>
</template>

<script setup src="@scripts/views/GobangRoom.js"></script>

<style scoped src="@/assets/styles/gobang-room.css"></style>
