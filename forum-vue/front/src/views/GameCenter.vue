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
        <button type="button" class="game-points-balance" title="前往萌币中心" @click="openPointsCenter">
          <PawCoinIcon />
          <span>萌币余额</span>
          <strong>{{ pointsBalance }}</strong>
        </button>
      </header>

      <section class="game-center-dashboard">
        <article class="game-rank-hero">
          <div class="game-rank-main">
            <div class="game-rank-mark"><el-icon><Medal /></el-icon></div>
            <div class="game-rank-copy">
              <h2>{{ rankText }}</h2>
              <div class="game-rank-progress-row">
                <span>段位进度</span>
                <strong>{{ rankProgressText }}</strong>
              </div>
              <div class="game-rank-track">
                <i :style="{ width: `${rankProgressPercent}%` }" />
              </div>
            </div>
          </div>
          <div class="game-rank-metrics">
            <div>
              <span>胜率</span>
              <strong class="is-rate">{{ homeWinRateText }}</strong>
            </div>
            <div>
              <span>大厅在线</span>
              <strong class="is-online">{{ lobbyOnlineText }}</strong>
            </div>
            <div>
              <span>已玩对局</span>
              <strong>{{ statisticsSummary.totalCount || 0 }} 局</strong>
            </div>
          </div>
          <img class="game-rank-illustration" :src="gameCardImg" alt="" aria-hidden="true">
        </article>
      </section>

      <main class="game-center-main">
        <section class="game-play-section">
          <div class="game-section-bar">
            <h2>推荐对局</h2>
            <div class="game-category-switch" aria-label="游戏分类">
              <button
                v-for="item in gameCategories"
                :key="item.code"
                type="button"
                :class="{ 'is-active': gameCategory === item.code }"
                @click="setGameCategory(item.code)"
              >{{ item.label }}</button>
            </div>
          </div>

          <div class="game-card-grid" v-loading="gameListLoading">
            <article
              v-for="game in pagedGames"
              :key="game.gameCode"
              class="game-card"
              :class="gameCardModifierClass(game.gameCode)"
            >
              <div class="game-cover-wrap">
                <img :src="gameCoverUrl(game)" :alt="gameDisplayName(game)" class="game-cover-img" />
              </div>
              <div class="game-card-content">
                <div class="game-card-head">
                  <h3 class="game-card-name">{{ gameDisplayName(game) }}</h3>
                  <span
                    class="game-card-online-badge"
                    :class="{ 'is-solo': gameOnlineBadgeSolo(game) }"
                  ><i />{{ gameOnlineBadgeText(game) }}</span>
                </div>
                <div class="game-card-meta">
                  <span
                    :class="game.gameCode === 'tetris' ? 'game-card-score-pill' : 'game-card-sub'"
                  >{{ gameMetaText(game) }}</span>
                </div>
                <div class="game-card-foot">
                  <el-button
                    type="primary"
                    :icon="Promotion"
                    :disabled="game.gameCode === 'tetris' ? Boolean(matchingGameCode) : matchButtonDisabled(game.gameCode)"
                    class="game-play-btn"
                    :class="[
                      gamePlayButtonClass(game),
                      { 'is-matching': matchingGameCode === game.gameCode },
                    ]"
                    @click="enterGame(game)"
                  >{{
                    game.gameCode === 'tetris'
                      ? (matchingGameCode ? '匹配中' : gamePlayIdleText(game))
                      : matchButtonText(game.gameCode, gamePlayIdleText(game))
                  }}</el-button>
                </div>
              </div>
            </article>
            <div v-if="!gameListLoading && !pagedGames.length" class="game-category-empty">
              <img src="@/assets/images/game_category_not_found.png" alt="该分类暂无游戏">
              <p>该分类下暂时没有可玩的游戏……</p>
            </div>
          </div>

          <!-- 底部分页器 就算只有一页也展示 -->
          <div class="game-card-pagination">
            <AppPagination
              :current-page="gamePageNum"
              size="small"
              :page-size="gamePageSize"
              :total="gameTotal"
              :hide-on-single-page="false"
              :pager-count="5"
              :show-jumper="false"
              @current-change="onGamePageChange"
            />
          </div>
        </section>

        <aside class="game-watch-panel">
          <div class="game-section-head">
            <h2>观战对局</h2>
            <span>{{ watchCountText }}</span>
          </div>

          <div class="game-watch-search">
            <el-input
              v-model="watchKeyword"
              clearable
              placeholder="搜索房间号"
              @clear="searchWatchRooms"
              @keyup="handleWatchSearchKeyup"
            />
            <el-button class="game-watch-search-button" @click="searchWatchRooms">搜索</el-button>
          </div>

          <div class="game-watch-group">
            <div class="game-watch-switch">
              <button type="button" :class="{ 'is-active': watchGameCode === 'gobang' }" @click="setWatchGame('gobang')">
                五子棋
              </button>
              <button type="button" :class="{ 'is-active': watchGameCode === 'tetris_pk' }" @click="setWatchGame('tetris_pk')">
                俄罗斯方块 PK
              </button>
            </div>
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
                    <small>
                      <b>#{{ roomRow.roomId }}</b>
                      <el-icon><View /></el-icon>
                      {{ watchViewerCount(roomRow) }} 人观战
                    </small>
                  </span>
                  <i class="game-room-row__watch">观战</i>
                </button>
              </div>
              <div class="game-watch-pager">
                <AppPagination
                  v-model:current-page="watchPage"
                  size="small"
                  :page-size="watchPageSize"
                  :total="watchTotal"
                  :pager-count="5"
                  :show-jumper="false"
                  @current-change="onWatchPageChange"
                />
              </div>
            </div>
            <div v-else class="game-room-empty">
              <img src="@/assets/images/no_game_room.png" alt="暂无对局中的房间">
              <p>暂无对局中的房间......</p>
            </div>
          </div>

          <footer class="game-watch-footer">
            <button type="button" @click="openStats('gobang')"><el-icon><DataLine /></el-icon>对局统计</button>
            <button type="button" @click="openLeaderboard('tetris')"><el-icon><Trophy /></el-icon>天梯榜</button>
          </footer>
        </aside>
      </main>
    </div>

    <!-- 天梯榜弹窗 参照 UI/游戏中心/天梯榜.html 760px 还原 -->
    <el-dialog v-model="leaderboardVisible" class="game-pencil-dialog" width="760px" destroy-on-close :show-close="true">
      <template #header>
        <div class="pencil-dialog-header">
          <h2 class="pencil-dialog-title">天梯榜</h2>
        </div>
      </template>

      <!-- 游戏 Tab 切换 -->
      <div class="pencil-dialog-tabs">
        <button
          type="button"
          class="pencil-tab-btn"
          :class="{ active: leaderboardGameCode === 'tetris' }"
          @click="onLeaderboardGameChange('tetris')"
        >
          俄罗斯方块
        </button>
        <button
          type="button"
          class="pencil-tab-btn"
          :class="{ active: leaderboardGameCode === 'tetris_pk' }"
          @click="onLeaderboardGameChange('tetris_pk')"
        >
          俄罗斯方块 PK
        </button>
      </div>

      <!-- 排行榜列表 -->
      <div class="pencil-rank-body">
        <div v-if="!leaderboardRows.length" class="pencil-empty-box">
          <div class="pencil-empty-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-4.5a2.5 2.5 0 01-2.5 2.5h-2a2.5 2.5 0 01-2.5-2.5H4" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
          <p class="pencil-empty-text">还没有玩家上榜</p>
        </div>

        <ol v-else class="pencil-rank-list">
          <li
            v-for="(row, index) in leaderboardRows"
            :key="row.userId"
            class="pencil-rank-item"
          >
            <span
              class="pencil-rank-badge"
              :class="`rank-badge--${(leaderboardPage - 1) * leaderboardPageSize + index + 1 <= 3 ? (leaderboardPage - 1) * leaderboardPageSize + index + 1 : 'other'}`"
            >
              {{ (leaderboardPage - 1) * leaderboardPageSize + index + 1 }}
            </span>
            <div class="pencil-rank-user">
              <img
                v-if="row.avatarUrl"
                :src="row.avatarUrl"
                alt=""
                class="pencil-rank-avatar"
                @error="onLeaderboardAvatarError"
              />
              <span v-else class="pencil-rank-avatar-fallback">
                {{ avatarText(row) }}
              </span>
              <strong class="pencil-rank-name">{{ row.nickname || row.username || `用户 ${row.userId}` }}</strong>
            </div>
            <div class="pencil-rank-score-wrap">
              <span v-if="leaderboardGameCode === 'tetris_pk'" class="pencil-rank-pk-rate">
                胜率 {{ row.winRate ?? 0 }}%
              </span>
              <b class="pencil-rank-score">
                {{ leaderboardGameCode === 'tetris' ? '最高得分：' : '' }}{{ formatNumber(row.bestScore ?? row.score ?? 0) }} 分
              </b>
            </div>
          </li>
        </ol>
      </div>

      <!-- 分页器 -->
      <div class="pencil-dialog-pager">
        <AppPagination
          v-model:current-page="leaderboardPage"
          size="small"
          :total="leaderboardTotal"
          :page-size="leaderboardPageSize"
          :pager-count="5"
          :show-jumper="false"
          @current-change="onLeaderboardPageChange"
        />
      </div>
    </el-dialog>

    <!-- 对局统计弹窗 参照 UI/游戏中心/对局统计.html 760px 还原 -->
    <el-dialog v-model="statsVisible" class="game-pencil-dialog" width="760px" destroy-on-close :show-close="true">
      <template #header>
        <div class="pencil-dialog-header">
          <h2 class="pencil-dialog-title">对局统计</h2>
        </div>
      </template>

      <div class="pencil-stats-shell">
      <!-- 游戏 Tab 切换 保留五子棋、井字棋、俄罗斯方块 PK 3个 -->
      <div class="pencil-dialog-tabs">
        <button
          type="button"
          class="pencil-tab-btn"
          :class="{ active: statsGameCode === 'gobang' }"
          @click="onStatsGameChange('gobang')"
        >
          五子棋
        </button>
        <button
          type="button"
          class="pencil-tab-btn"
          :class="{ active: statsGameCode === 'jinzi' }"
          @click="onStatsGameChange('jinzi')"
        >
          井字棋
        </button>
        <button
          type="button"
          class="pencil-tab-btn"
          :class="{ active: statsGameCode === 'tetris_pk' }"
          @click="onStatsGameChange('tetris_pk')"
        >
          俄罗斯方块 PK
        </button>
      </div>

      <!-- 3 个指标卡片 -->
      <div class="pencil-stats-cards">
        <div class="pencil-stat-card">
          <span class="pencil-stat-card__label">胜 / 负</span>
          <strong class="pencil-stat-card__val val--purple">
            {{ `${statsProfile.winCount ?? 0} / ${statsProfile.loseCount ?? 0}` }}
          </strong>
        </div>
        <div class="pencil-stat-card">
          <span class="pencil-stat-card__label">总局数</span>
          <strong class="pencil-stat-card__val val--green">{{ statsTotalCount }}</strong>
        </div>
        <div class="pencil-stat-card">
          <span class="pencil-stat-card__label">胜率</span>
          <strong class="pencil-stat-card__val val--pink">
            {{ statsWinRateText }}
          </strong>
        </div>
      </div>

      <!-- 对局记录区域 -->
      <div class="pencil-stats-body" v-loading="statsLoading">
        <div v-if="!statsLoading && !statRecords.length" class="pencil-empty-box">
          <div class="pencil-empty-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-4.5a2.5 2.5 0 01-2.5 2.5h-2a2.5 2.5 0 01-2.5-2.5H4" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
          <p class="pencil-empty-text">暂时没有对局记录</p>
        </div>

        <div v-else-if="statRecords.length" class="pencil-record-container" :class="`grid--${statsGameCode}`">
          <!-- 五子棋专属列头 -->
          <div v-if="statsGameCode === 'gobang'" class="pencil-record-head record-head--gobang">
            <span class="col-result">对局结果</span>
            <span class="col-role">执子</span>
            <span class="col-opponent">对手</span>
            <span class="col-reason">结束原因</span>
            <span class="col-time">对局时间</span>
            <span class="col-delta">段位分变动</span>
            <span class="col-action">操作</span>
          </div>

          <!-- 井字棋专属列头 仅保留 5 列：对局结果、比分、对手、对局时间、段位分变化 -->
          <div v-else-if="statsGameCode === 'jinzi'" class="pencil-record-head record-head--jinzi">
            <span class="col-result">对局结果</span>
            <span class="col-score">比分</span>
            <span class="col-opponent">对手</span>
            <span class="col-time">对局时间</span>
            <span class="col-delta">段位分变化</span>
          </div>

          <!-- 俄罗斯方块 PK 专属列头 无回放操作 -->
          <div v-else class="pencil-record-head record-head--tetris-pk">
            <span class="col-result">对局结果</span>
            <span class="col-opponent">对手</span>
            <span class="col-reason">结束原因</span>
            <span class="col-time">对局时间</span>
            <span class="col-delta">段位分变化</span>
          </div>

          <!-- 记录列表 -->
          <ul class="pencil-record-list">
            <li
              v-for="row in statRecords"
              :key="`${row.gameCode || statsGameCode}-${row.sourceRecordId || row.id}`"
              class="pencil-record-item"
              :class="[`record-item--${statsGameCode}`, `record-item--${statsGameCode.replace('_', '-')}`]"
            >
              <!-- 五子棋行 -->
              <template v-if="(row.gameCode || statsGameCode) === 'gobang'">
                <span class="record-badge col-result" :class="`badge--${(row.resultCode || 'DRAW').toLowerCase()}`">
                  {{ recordResultText(row) }}
                </span>
                <span class="col-role">{{ recordRoleText(row) }}</span>
                <div class="col-opponent record-user-cell">
                  <span class="record-mini-avatar">
                    <img v-if="row.opponentAvatarUrl" :src="row.opponentAvatarUrl" alt="" @error="onLeaderboardAvatarError" />
                    <b v-else>{{ avatarText(row) }}</b>
                  </span>
                  <span class="record-user-name" :title="recordOpponentText(row)">{{ recordOpponentText(row) }}</span>
                </div>
                <span class="record-reason col-reason">{{ recordEndReasonText(row) }}</span>
                <span class="record-time col-time">{{ formatRecordTime(row.endedAt) }}</span>
                <span class="record-delta col-delta" :class="{ 'is-positive': recordScoreDelta(row) > 0, 'is-negative': recordScoreDelta(row) < 0 }">
                  {{ formatScoreDelta(row) }}
                </span>
                <span class="record-action col-action">
                  <button type="button" class="pencil-replay-btn" @click="openGobangReplay(row)">
                    复盘
                  </button>
                </span>
              </template>

              <!-- 井字棋行 5 列：对局结果、比分、对手头像昵称、对局时间、段位分变化 -->
              <template v-else-if="(row.gameCode || statsGameCode) === 'jinzi'">
                <span class="record-badge col-result" :class="`badge--${(row.resultCode || 'DRAW').toLowerCase()}`">
                  {{ recordResultText(row) }}
                </span>
                <span class="col-score font-bold">{{ recordScoreRatioText(row) }}</span>
                <div class="col-opponent record-user-cell">
                  <span class="record-mini-avatar">
                    <img v-if="row.opponentAvatarUrl" :src="row.opponentAvatarUrl" alt="" @error="onLeaderboardAvatarError" />
                    <b v-else>{{ avatarText(row) }}</b>
                  </span>
                  <span class="record-user-name" :title="recordOpponentText(row)">{{ recordOpponentText(row) }}</span>
                </div>
                <span class="record-time col-time">{{ formatRecordTime(row.endedAt) }}</span>
                <span class="record-delta col-delta" :class="{ 'is-positive': recordScoreDelta(row) > 0, 'is-negative': recordScoreDelta(row) < 0 }">
                  {{ formatScoreDelta(row) }}
                </span>
              </template>

              <!-- 俄罗斯方块 PK 行 5 列：对局结果、对手头像昵称、结束原因、对局时间、段位分变化，无回放 -->
              <template v-else>
                <span class="record-badge col-result" :class="`badge--${(row.resultCode || 'DRAW').toLowerCase()}`">
                  {{ recordResultText(row) }}
                </span>
                <div class="col-opponent record-user-cell">
                  <span class="record-mini-avatar">
                    <img v-if="row.opponentAvatarUrl" :src="row.opponentAvatarUrl" alt="" @error="onLeaderboardAvatarError" />
                    <b v-else>{{ avatarText(row) }}</b>
                  </span>
                  <span class="record-user-name" :title="recordOpponentText(row)">{{ recordOpponentText(row) }}</span>
                </div>
                <span class="record-reason col-reason">{{ recordEndReasonText(row) }}</span>
                <span class="record-time col-time">{{ formatRecordTime(row.endedAt) }}</span>
                <span class="record-delta col-delta" :class="{ 'is-positive': recordScoreDelta(row) > 0, 'is-negative': recordScoreDelta(row) < 0 }">
                  {{ formatScoreDelta(row) }}
                </span>
              </template>
            </li>
          </ul>
        </div>
      </div>

      <!-- 分页器：固定在底部，少数据时不上移 -->
      <div class="pencil-dialog-pager">
        <AppPagination
          v-model:current-page="statPage"
          size="small"
          :total="statTotal"
          :page-size="statPageSize"
          :pager-count="5"
          :show-jumper="false"
          @current-change="onStatPageChange"
        />
      </div>
      </div>
    </el-dialog>

    <!-- 五子棋回放 Drawer -->
    <el-drawer
      v-model="gobangReplayVisible"
      title="五子棋对局复盘"
      size="540px"
      destroy-on-close
      @closed="stopGobangReplay"
    >
      <div class="gobang-replay-wrap">
        <div class="gobang-replay-meta">
          <span>进度：第 <strong>{{ gobangReplayStep }}</strong> / {{ gobangReplayMoves.length }} 步</span>
        </div>
        <div class="gobang-replay-board">
          <div v-for="(rowArr, r) in gobangReplayBoard" :key="r" class="gobang-replay-row">
            <div
              v-for="(cell, c) in rowArr"
              :key="`${r}-${c}`"
              class="gobang-replay-cell"
            >
              <div
                v-if="cell"
                class="gobang-replay-piece"
                :class="cell.chess === 1 ? 'is-black' : 'is-white'"
              >
                <span>{{ cell.step }}</span>
              </div>
            </div>
          </div>
        </div>
        <div class="gobang-replay-controls">
          <button
            type="button"
            class="gobang-replay-nav-btn"
            :disabled="gobangReplayStep <= 0"
            @click="stepGobangReplay(-1)"
          >
            上一步
          </button>
          <button
            type="button"
            class="gobang-replay-play-btn"
            @click="toggleGobangReplayAuto"
          >
            {{ gobangReplayPlaying ? '暂停' : '自动播放' }}
          </button>
          <button
            type="button"
            class="gobang-replay-nav-btn"
            :disabled="gobangReplayStep >= gobangReplayMoves.length"
            @click="stepGobangReplay(1)"
          >
            下一步
          </button>
        </div>
        <div class="gobang-replay-roles">
          <div class="gobang-replay-role is-mine">{{ gobangReplayRoleLines.mine }}</div>
          <div class="gobang-replay-role is-opponent">{{ gobangReplayRoleLines.opponent }}</div>
        </div>
      </div>
    </el-drawer>

    <!-- 俄罗斯方块回放 Drawer -->
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
