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

      <section class="game-center-dashboard">
        <article class="game-rank-hero">
          <div class="game-rank-mark">★</div>
          <div class="game-rank-copy">
            <h2>{{ rankText }}</h2>
          </div>
          <div class="game-rank-progress-row">
            <span>段位进度</span>
            <strong>{{ rankProgressText }}</strong>
          </div>
          <div class="game-rank-track">
            <i :style="{ width: `${rankProgressPercent}%` }" />
          </div>
          <em class="game-rank-streak">{{ winRateTrendText }}</em>
        </article>

        <div class="game-dashboard-stack">
          <article class="game-metric-card">
            <span>胜率</span>
            <strong>{{ winRateText }}</strong>
          </article>
          <article class="game-metric-card">
            <span>大厅在线</span>
            <strong>{{ lobbyOnlineText }}</strong>
          </article>
        </div>
      </section>

      <main class="game-center-main">
        <section class="game-play-section">
          <div class="game-section-bar">
            <h2>推荐对局</h2>
            <div>
              <el-button :icon="DataLine" @click="openStats('gobang')">对局统计</el-button>
              <el-button :icon="Trophy" @click="openLeaderboard('gobang')">天梯榜</el-button>
            </div>
          </div>

          <div class="game-card-grid">
            <article class="game-card game-card--gobang">
              <div class="game-cover-board gobang-cover-board" aria-hidden="true">
                <span class="game-card-piece is-black card-piece-a" />
                <span class="game-card-piece is-white card-piece-b" />
                <span class="game-card-piece is-black card-piece-c" />
                <span class="game-card-piece is-white card-piece-d" />
                <span class="game-card-piece is-black card-piece-e" />
                <span class="game-card-piece is-white card-piece-f" />
              </div>
              <div class="game-card-content">
                <div class="game-card-title-row">
                  <h3>{{ gobangGame.gameName || '五子棋' }}</h3>
                  <span>{{ gobangTotalText }}</span>
                </div>
                <div class="game-card-foot">
                  <em>博弈 · {{ gameOnlineText }}</em>
                  <el-button type="primary" :icon="Promotion" @click="enterGobang">进入匹配</el-button>
                </div>
              </div>
            </article>

            <article class="game-card game-card--jinzi">
              <div class="game-cover-board jinzi-cover-board" aria-hidden="true">
                <span class="jinzi-cover-mark is-x jinzi-piece-a">×</span>
                <span class="jinzi-cover-mark is-o jinzi-piece-b">○</span>
                <span class="jinzi-cover-mark is-x jinzi-piece-c">×</span>
              </div>
              <div class="game-card-content">
                <div class="game-card-title-row">
                  <h3>{{ jinziGame.gameName || '井字棋' }}</h3>
                  <span>{{ jinziTotalText }}</span>
                </div>
                <div class="game-card-foot">
                  <em>速战 · {{ jinziOnlineText }}</em>
                  <el-button type="primary" :icon="Promotion" @click="enterJinzi">进入匹配</el-button>
                </div>
              </div>
            </article>

            <article class="game-card game-card--tetris">
              <TetrisCoverBoard wrapper-class="game-cover-board game-cover-board--tetris" />
              <div class="game-card-content">
                <div class="game-card-title-row">
                  <h3>俄罗斯方块单人版</h3>
                  <span>{{ tetrisTotalText }}</span>
                </div>
                <div class="game-card-foot">
                  <em>消除 · 单人模式</em>
                  <el-button type="primary" :icon="Promotion" @click="enterTetris">进入游戏</el-button>
                </div>
              </div>
            </article>

            <article class="game-card game-card--tetris-pk">
              <div class="game-cover-board tetris-pk-cover-board" aria-hidden="true">
                <TetrisCoverBoard wrapper-class="pk-tetris-board" />
                <b>PK</b>
                <TetrisCoverBoard wrapper-class="pk-tetris-board" />
              </div>
              <div class="game-card-content">
                <div class="game-card-title-row">
                  <h3>俄罗斯方块PK版</h3>
                  <span>{{ tetrisPkTotalText }}</span>
                </div>
                <div class="game-card-foot">
                  <em>排位 · {{ tetrisPkOnlineText }}</em>
                  <el-button type="primary" :icon="Promotion" @click="enterTetrisPk">在线 PK</el-button>
                </div>
              </div>
            </article>
          </div>
        </section>

        <aside class="game-watch-panel">
          <div class="game-section-head">
            <h2>观战对局</h2>
            <span>{{ watchCountText }}</span>
          </div>

          <div class="game-watch-switch">
            <button type="button" :class="{ 'is-active': watchGameCode === 'gobang' }" @click="setWatchGame('gobang')">
              五子棋
            </button>
            <button type="button" :class="{ 'is-active': watchGameCode === 'tetris_pk' }" @click="setWatchGame('tetris_pk')">
              俄罗斯方块
            </button>
          </div>

          <div class="game-watch-search">
            <el-input
              v-model="watchKeyword"
              clearable
              placeholder="搜索昵称或房间"
              @clear="searchWatchRooms"
              @keyup="handleWatchSearchKeyup"
            />
            <el-button type="primary" @click="searchWatchRooms">搜索</el-button>
          </div>

          <div class="game-watch-group">
            <div v-if="pagedWatchRooms.length" class="game-room-list">
              <div class="game-room-list-body">
                <button
                  v-for="roomRow in pagedWatchRooms"
                  :key="roomRow.roomId"
                  type="button"
                  class="game-room-row"
                  @click="watchGameCode === 'gobang' ? watchRoom(roomRow) : watchTetrisPkRoom(roomRow)"
                >
                  <span>
                    <strong>{{ watchRoomTitle(roomRow) }}</strong>
                    <em>{{ watchRoomMeta(roomRow) }}</em>
                  </span>
                  <i>观战</i>
                </button>
              </div>
              <div v-if="watchTotal > watchPageSize" class="game-watch-pager">
                <el-pagination
                  v-model:current-page="watchPage"
                  small
                  background
                  layout="prev, pager, next"
                  :page-size="watchPageSize"
                  :total="watchTotal"
                  @current-change="onWatchPageChange"
                />
              </div>
            </div>
            <div v-else class="game-room-empty">
              <el-empty :image-size="54" description="" />
            </div>
          </div>
        </aside>
      </main>
    </div>

    <el-dialog v-model="leaderboardVisible" class="game-center-dialog" width="680px" destroy-on-close>
      <template #header>
        <span>天梯榜</span>
      </template>
      <el-tabs v-model="activeGameCode" @tab-change="onLeaderboardGameChange">
        <el-tab-pane label="五子棋" name="gobang" />
        <el-tab-pane label="井字棋" name="jinzi" />
        <el-tab-pane label="俄罗斯方块" name="tetris" />
        <el-tab-pane label="方块 PK" name="tetris_pk" />
      </el-tabs>
      <ol v-if="leaderboardRows.length" class="game-rank-list">
        <li v-for="(row, index) in leaderboardRows" :key="row.userId">
          <span>{{ (leaderboardPage - 1) * leaderboardPageSize + index + 1 }}</span>
          <div>
            <strong>{{ row.nickname || row.username || `用户 ${row.userId}` }}</strong>
            <em>{{ row.rankName || '青铜 III' }} · {{ row.totalCount ?? 0 }} 局 · 胜率 {{ row.winRate ?? 0 }}%</em>
          </div>
          <b>{{ row.score ?? row.bestScore ?? 0 }}</b>
        </li>
      </ol>
      <p v-else class="game-stats-empty">还没有玩家上榜。</p>
      <div v-if="leaderboardTotal > leaderboardPageSize" class="game-dialog-pager">
        <el-pagination
          v-model:current-page="leaderboardPage"
          layout="prev, pager, next"
          :total="leaderboardTotal"
          :page-size="leaderboardPageSize"
          size="small"
          @current-change="onLeaderboardPageChange"
        />
      </div>
    </el-dialog>

    <el-dialog v-model="statsVisible" class="game-center-dialog" width="680px" destroy-on-close>
      <template #header>
        <span>对局统计</span>
      </template>
      <el-tabs v-model="activeGameCode" @tab-change="onStatsGameChange">
        <el-tab-pane label="五子棋" name="gobang" />
        <el-tab-pane label="井字棋" name="jinzi" />
        <el-tab-pane label="俄罗斯方块" name="tetris" />
        <el-tab-pane label="方块 PK" name="tetris_pk" />
      </el-tabs>
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
          <strong>{{ activeGameCode === 'tetris' ? tetrisWinRateText : winRateText }}</strong>
        </div>
      </div>

      <div class="game-stats-record-head">我的对战记录</div>
      <ul v-if="statRecords.length" class="game-stats-record-list">
        <li v-for="row in statRecords" :key="row.id" class="game-stats-record-item">
          <div>
            <strong>{{ recordResultText(row) }}</strong>
            <span>{{ endReasonText(row.endReason) }} · {{ formatRecordTime(row.endedAt) }}</span>
          </div>
          <em :class="{ 'is-win': recordScoreDelta(row) > 0 }">
            {{ formatScoreDelta(row) }}
          </em>
        </li>
      </ul>
      <p v-else class="game-stats-empty">还没有对局记录。</p>
      <div v-if="statTotal > statPageSize" class="game-dialog-pager">
        <el-pagination
          v-model:current-page="statPage"
          layout="prev, pager, next"
          :total="statTotal"
          :page-size="statPageSize"
          size="small"
          @current-change="onStatPageChange"
        />
      </div>
    </el-dialog>

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
