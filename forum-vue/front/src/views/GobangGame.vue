<template>
  <div v-loading="loading" class="gobang-game-page shell-page-scroll animate-fade-in">
    <div class="gobang-game-inner">
      <header class="gobang-game-head">
        <button type="button" class="game-icon-btn" aria-label="返回游戏中心" title="返回游戏中心" @click="backCenter">
          <el-icon><Back /></el-icon>
        </button>
        <div class="gobang-game-title-wrap">
          <h1>五子棋</h1>
        </div>
        <div class="gobang-points-balance">
          <span>积分余额</span>
          <strong>{{ pointsBalance }}</strong>
        </div>
      </header>

      <section class="gobang-match-panel">
        <div class="gobang-match-main">
          <div class="gobang-match-board" :class="{ 'is-matching': matching }" aria-hidden="true">
            <span class="gobang-match-piece is-black match-piece-a" />
            <span class="gobang-match-piece is-white match-piece-b" />
            <span class="gobang-match-piece is-black match-piece-c" />
            <span class="gobang-match-piece is-white match-piece-d" />
            <span class="gobang-match-piece is-black match-piece-e" />
            <span class="gobang-match-piece is-white match-piece-f" />
          </div>
          <div class="gobang-match-copy">
            <div class="gobang-live-stats">
              <div>
                <span>游戏在线</span>
                <strong>{{ gameOnlineText }}</strong>
              </div>
              <div>
                <span>房间总数</span>
                <strong>{{ activeRoomText }}</strong>
              </div>
            </div>
            <div class="gobang-match-line">
              <h2>{{ matching ? '棋局正在排队' : '进入快速匹配' }}</h2>
              <p>匹配成功后自动进入房间，胜负会同步到论坛积分流水。</p>
            </div>
            <div class="gobang-match-actions">
              <el-button v-if="!matching" type="primary" size="large" :icon="VideoPlay" @click="startMatch">
                开始匹配
              </el-button>
              <el-button v-else type="danger" size="large" :icon="CircleClose" @click="stopMatch">
                正在匹配中........
              </el-button>
              <el-button v-if="canResumeRoom" size="large" :icon="Timer" @click="resumeRoom">
                回到对局
              </el-button>
            </div>
          </div>
        </div>
      </section>

      <section class="gobang-record-panel">
        <div class="game-section-head">
          <div>
            <h2>最近对局</h2>
          </div>
        </div>
        <el-table class="gobang-record-table" :data="records" size="small" stripe empty-text="暂无对局记录">
          <el-table-column label="房间" prop="roomId" min-width="170" align="center" header-align="center" show-overflow-tooltip />
          <el-table-column label="结果" width="96" align="center" header-align="center">
            <template #default="{ row }">
              <span :class="['gobang-result-tag', { 'is-win': row.winnerUserId === profile.userId }]">
                {{ row.winnerUserId === profile.userId ? '胜利' : '失败' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="积分" width="88" align="center" header-align="center">
            <template #default="{ row }">
              <span :class="['gobang-score-delta', { 'is-plus': recordScoreDelta(row) > 0 }]">
                {{ scoreDeltaText(row) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="结束原因" width="124" align="center" header-align="center">
            <template #default="{ row }">{{ endReasonText(row.endReason) }}</template>
          </el-table-column>
          <el-table-column label="结束时间" min-width="160" align="center" header-align="center">
            <template #default="{ row }">{{ formatDateTime(row.endedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="96" align="center" header-align="center">
            <template #default="{ row }">
              <el-button link type="primary" @click="openReplay(row)">回放</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="recordTotal > recordPageSize" class="gobang-record-pager">
          <el-pagination
            v-model:current-page="recordPage"
            layout="prev, pager, next"
            :total="recordTotal"
            :page-size="recordPageSize"
            size="small"
            @current-change="onRecordPageChange"
          />
        </div>
      </section>
    </div>

    <el-drawer v-model="replayVisible" title="棋谱回放" size="520px" destroy-on-close @closed="stopReplayAuto">
      <div class="gobang-replay-board" aria-label="五子棋回放棋盘">
        <template v-for="(row, rowIndex) in replayBoard" :key="rowIndex">
          <span
            v-for="(cell, colIndex) in row"
            :key="`${rowIndex}-${colIndex}`"
            class="gobang-replay-cell"
            :style="{ '--row': rowIndex, '--col': colIndex }"
          >
            <i v-if="cell" :class="['gobang-replay-piece', cell === 1 ? 'is-black' : 'is-white']" />
          </span>
        </template>
      </div>
      <div class="gobang-replay-actions">
        <el-button :disabled="replayIndex <= 0" @click="replayPrev">上一步</el-button>
        <span>{{ replayCurrentText }}</span>
        <el-button :disabled="replayIndex >= replayMoves.length" @click="replayNext">下一步</el-button>
        <el-button class="gobang-replay-auto" :disabled="!replayMoves.length" @click="toggleReplayAuto">
          {{ replayPlaying ? '暂停自动播放' : '自动播放' }}
        </el-button>
      </div>
    </el-drawer>
  </div>
</template>

<script setup src="@scripts/views/GobangGame.js"></script>

<style scoped src="@/assets/styles/gobang-game.css"></style>
