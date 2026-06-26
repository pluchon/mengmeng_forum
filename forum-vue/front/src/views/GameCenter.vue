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
          <strong>{{ pointsBalance }}</strong>
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
              <el-button size="small" :icon="DataLine" @click="openStats('gobang')">对局统计</el-button>
              <el-button size="small" :icon="Trophy" @click="openLeaderboard('gobang')">天梯榜</el-button>
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

        <section class="game-live-panel game-gobang-watch-panel">
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

        <article class="game-card game-card--jinzi">
          <div class="game-card-board jinzi-cover-board" aria-hidden="true">
            <span class="jinzi-cover-mark is-x jinzi-piece-a">×</span>
            <span class="jinzi-cover-mark is-o jinzi-piece-b">○</span>
            <span class="jinzi-cover-mark is-x jinzi-piece-c">×</span>
          </div>
          <div class="game-card-content">
            <div class="game-card-tools">
              <el-button size="small" :icon="DataLine" @click="openStats('jinzi')">对局统计</el-button>
              <el-button size="small" :icon="Trophy" @click="openLeaderboard('jinzi')">天梯榜</el-button>
            </div>
            <div class="game-card-title-row">
              <h2>{{ jinziGame.gameName || '井字' }}</h2>
              <span>{{ jinziOnlineText }}</span>
            </div>
            <div class="game-card-rules">
              <span>20秒/步时</span>
              <span>2分钟/局时</span>
              <span>短局快速匹配</span>
            </div>
            <div class="game-card-actions">
              <el-button type="primary" size="large" :icon="Promotion" @click="enterJinzi">
                进入匹配
              </el-button>
            </div>
          </div>
        </article>

        <article class="game-card game-card--tetris">
          <TetrisCoverBoard wrapper-class="game-card-board" />
          <div class="game-card-content">
            <div class="game-card-tools">
              <el-button size="small" :icon="List" @click="openRecentMatches">最近对局</el-button>
              <el-button size="small" :icon="DataLine" @click="openStats('tetris')">历史记录</el-button>
              <el-button size="small" :icon="Trophy" @click="openLeaderboard('tetris')">天梯榜</el-button>
            </div>
            <div class="game-card-title-row">
              <h2>{{ tetrisGame.gameName || '俄罗斯方块' }}</h2>
              <span>{{ tetrisOnlineText }}</span>
            </div>
            <div class="game-card-rules">
              <span>经典 10×20</span>
              <span>单人即时结算</span>
              <span>挑战最高分</span>
            </div>
            <div class="game-card-actions game-card-actions--dual">
              <el-button type="primary" size="large" :icon="Promotion" @click="enterTetris">
                进入游戏
              </el-button>
              <el-button class="game-card-btn--pk" size="large" :icon="Aim" @click="enterTetrisPk">
                在线 PK
              </el-button>
            </div>
          </div>
        </article>

        <section class="game-live-panel game-tetris-pk-panel">
          <div class="game-section-head">
            <div>
              <h2>可观战 PK</h2>
            </div>
          </div>
          <div v-if="tetrisPkRooms.length" class="game-room-list">
            <button
              v-for="roomRow in tetrisPkRooms"
              :key="roomRow.roomId"
              type="button"
              class="game-room-row"
              @click="watchTetrisPkRoom(roomRow)"
            >
              <span>
                <strong>{{ roomRow.title || '玩家对局' }}</strong>
                <em>{{ roomRow.roomId }}</em>
              </span>
              <i>观战</i>
            </button>
          </div>
          <div v-else class="game-room-empty">
            <span>暂无 PK 房间开播，敬请期待</span>
          </div>
        </section>
      </main>
    </div>

    <el-drawer v-model="leaderboardVisible" :title="`${activeGameName}天梯榜`" size="420px" destroy-on-close>
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

    <el-drawer v-model="statsVisible" :title="`${activeGameName}统计`" size="420px" destroy-on-close>
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

    <el-drawer v-model="recentVisible" title="俄罗斯方块 · 最近对局" size="720px" destroy-on-close>
      <el-table class="gobang-record-table tetris-record-table" :data="recentRecords" size="small" stripe empty-text="暂无对局记录">
        <el-table-column label="局号" prop="id" min-width="88" show-overflow-tooltip />
        <el-table-column label="得分" width="108">
          <template #default="{ row }">
            <span class="gobang-result-tag is-win">{{ row.score ?? 0 }} 分</span>
          </template>
        </el-table-column>
        <el-table-column label="论坛积分" width="96">
          <template #default="{ row }">
            <span :class="['gobang-score-delta', { 'is-plus': (row.forumPointsAwarded ?? 0) > 0 }]">
              +{{ row.forumPointsAwarded ?? 0 }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="消行" width="108">
          <template #default="{ row }">{{ row.linesCleared ?? 0 }} 行 · Lv{{ row.level ?? 1 }}</template>
        </el-table-column>
        <el-table-column label="结束时间" min-width="150">
          <template #default="{ row }">{{ formatDateTime(row.endedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="86" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openTetrisReplay(row)">回放</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="recentTotal > recentPageSize" class="gobang-record-pager">
        <el-pagination
          v-model:current-page="recentPage"
          layout="prev, pager, next"
          :total="recentTotal"
          :page-size="recentPageSize"
          size="small"
          @current-change="onRecentPageChange"
        />
      </div>
    </el-drawer>

    <el-drawer
      v-model="tetrisReplayVisible"
      title="俄罗斯方块回放"
      size="520px"
      destroy-on-close
      @closed="stopTetrisReplay"
    >
      <div v-if="tetrisReplayRecord" class="tetris-replay-meta">
        <strong>{{ tetrisReplayRecord.score ?? 0 }} 分</strong>
        <span>{{ tetrisReplayRecord.linesCleared ?? 0 }} 行 · Lv{{ tetrisReplayRecord.level ?? 1 }}</span>
      </div>
      <div class="tetris-replay-stage">
        <canvas
          ref="tetrisReplayBoardRef"
          class="tetris-replay-canvas"
          :width="10 * 20"
          :height="20 * 20"
          aria-label="俄罗斯方块回放棋盘"
        />
      </div>
      <el-progress :percentage="tetrisReplayProgress" :stroke-width="8" />
      <div class="gobang-replay-actions tetris-replay-actions">
        <el-button class="gobang-replay-auto" :disabled="!tetrisReplayRecord" @click="toggleTetrisReplayAuto">
          <el-icon><VideoPlay /></el-icon>
          {{ tetrisReplayPlaying ? '暂停自动播放' : '自动播放' }}
        </el-button>
      </div>
    </el-drawer>
  </div>
</template>

<script setup src="@scripts/views/GameCenter.js"></script>

<style scoped src="@/assets/styles/game-center.css"></style>
