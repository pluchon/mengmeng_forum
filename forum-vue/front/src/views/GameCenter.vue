<template>
  <div v-loading="loading" class="game-center-page animate-fade-in">
    <div class="game-center-inner">
      <header class="game-center-topbar">
        <button type="button" class="game-icon-btn" aria-label="返回首页" title="返回首页" @click="backHome">
          <el-icon><ArrowLeft /></el-icon>
        </button>
        <div>
          <h1>游戏中心</h1>
        </div>
        <div class="game-points-balance">
          <span>积分余额</span>
          <strong>{{ pointsBalanceText }}</strong>
        </div>
      </header>

      <section class="game-center-status">
        <div>
          <span>大厅在线</span>
          <strong>{{ lobbyOnlineText }}</strong>
        </div>
        <div>
          <span>当前段位</span>
          <strong>{{ rankText }}</strong>
        </div>
        <div>
          <span>胜率</span>
          <strong>{{ winRateText }}</strong>
        </div>
      </section>

      <main class="game-center-layout">
        <article class="game-card game-card--gobang">
          <div class="game-card-board gobang-cover-board" aria-hidden="true">
            <span class="game-card-piece is-black card-piece-a" />
            <span class="game-card-piece is-white card-piece-b" />
            <span class="game-card-piece is-black card-piece-c" />
            <span class="game-card-piece is-white card-piece-d" />
            <span class="game-card-piece is-black card-piece-e" />
            <span class="game-card-piece is-white card-piece-f" />
          </div>
          <div class="game-card-content">
            <div class="game-card-tools">
              <el-button size="small" :icon="DataLine" @click="openStats">对局统计</el-button>
              <el-button size="small" :icon="Trophy" @click="openLeaderboard">天梯榜</el-button>
            </div>
            <div class="game-card-title-row">
              <h2>{{ gobangGame.gameName || '五子棋' }}</h2>
              <span>{{ gameOnlineText }}</span>
            </div>
            <div class="game-card-rules">
              <span>60秒/步时</span>
              <span>10分钟/局时</span>
              <span>长时间无人会有同水平AI</span>
            </div>
            <div class="game-card-actions">
              <el-button type="primary" size="large" :icon="Promotion" @click="enterGobang">
                进入匹配
              </el-button>
            </div>
          </div>
        </article>

        <section class="game-live-panel">
          <div class="game-section-head">
            <div>
              <h2>可观战房间</h2>
            </div>
          </div>
          <div v-if="activeRooms.length" class="game-room-list">
            <button
              v-for="roomRow in activeRooms"
              :key="roomRow.roomId"
              type="button"
              class="game-room-row"
              @click="watchRoom(roomRow)"
            >
              <span>
                <strong>{{ roomRow.aiRoom ? 'AI 对局' : '玩家对局' }}</strong>
                <em>{{ roomRow.roomId }}</em>
              </span>
              <i>观战</i>
            </button>
          </div>
          <div v-else class="game-room-empty">
            <span>等待第一盘棋开局</span>
          </div>
        </section>
      </main>
    </div>

    <el-drawer v-model="leaderboardVisible" title="五子棋天梯榜" size="420px" destroy-on-close>
      <ol v-if="leaderboard.length" class="game-rank-list">
        <li v-for="(row, index) in leaderboard" :key="row.userId">
          <span>{{ index + 1 }}</span>
          <div>
            <strong>{{ row.nickname || row.username || `用户 ${row.userId}` }}</strong>
            <em>{{ row.totalCount ?? 0 }} 局 · 胜率 {{ row.winRate ?? 0 }}%</em>
          </div>
          <b>{{ row.score ?? 0 }}</b>
        </li>
      </ol>
      <p v-else class="game-stats-empty">还没有玩家上榜。</p>
    </el-drawer>

    <el-drawer v-model="statsVisible" title="五子棋统计" size="420px" destroy-on-close>
      <div class="game-stats-summary">
        <div>
          <span>胜 / 负</span>
          <strong>{{ profile.winCount ?? 0 }} / {{ profile.loseCount ?? 0 }}</strong>
        </div>
        <div>
          <span>总局数</span>
          <strong>{{ totalCount }}</strong>
        </div>
        <div>
          <span>胜率</span>
          <strong>{{ winRateText }}</strong>
        </div>
      </div>

      <div class="game-stats-record-head">我的对战记录</div>
      <ul v-if="statRecords.length" class="game-stats-record-list">
        <li v-for="row in statRecords" :key="row.id" class="game-stats-record-item">
          <div>
            <strong>{{ recordResultText(row) }}</strong>
            <span>{{ endReasonText(row.endReason) }} · {{ formatRecordTime(row.endedAt) }}</span>
          </div>
          <em :class="{ 'is-win': row.winnerUserId === profile.userId }">
            {{ row.winnerUserId === profile.userId ? '+' : '-' }}{{ row.scoreDelta ?? scoreDelta }}
          </em>
        </li>
      </ul>
      <p v-else class="game-stats-empty">还没有对局记录。</p>
    </el-drawer>
  </div>
</template>

<script setup src="@scripts/views/GameCenter.js"></script>

<style scoped src="@/assets/styles/game-center.css"></style>
