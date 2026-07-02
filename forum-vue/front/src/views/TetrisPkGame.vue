<template>
  <div v-loading="loading" class="gobang-game-page shell-page-scroll animate-fade-in">
    <div class="gobang-game-inner">
      <header class="gobang-game-head">
        <button type="button" class="game-icon-btn" aria-label="返回游戏中心" title="返回游戏中心" @click="backCenter">
          <el-icon><Back /></el-icon>
        </button>
        <div class="gobang-game-title-wrap">
          <h1>俄罗斯方块 PK</h1>
        </div>
        <div class="gobang-points-balance">
          <span>积分余额</span>
          <strong>{{ pointsBalance }}</strong>
        </div>
      </header>

      <section class="gobang-match-panel">
        <div class="gobang-match-main">
          <TetrisCoverBoard wrapper-class="gobang-match-board" :matching="matching" />
          <div class="gobang-match-copy">
            <div class="gobang-live-stats">
              <div>
                <span>游戏在线</span>
                <strong>{{ gameOnlineText }}</strong>
              </div>
              <div>
                <span>可观战房间</span>
                <strong>{{ activeRoomText }}</strong>
              </div>
              <div>
                <span>当前段位</span>
                <strong>{{ profile.rankName || '青铜堆叠者 III' }}</strong>
              </div>
              <div>
                <span>排位分</span>
                <strong>{{ profile.score ?? 1000 }}</strong>
              </div>
            </div>
            <div class="gobang-match-line">
              <h2>{{ matching ? '匹配中，请稍候' : '进入快速匹配' }}</h2>
              <p>匹配成功后自动进入 PK 房间，胜负会同步到论坛积分。</p>
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

      <section v-if="activeRooms.length" class="gobang-record-panel">
        <div class="game-section-head">
          <h2>可观战房间</h2>
        </div>
        <div class="game-room-list">
          <button
            v-for="roomRow in activeRooms"
            :key="roomRow.roomId"
            type="button"
            class="game-room-chip"
            @click="watchRoom(roomRow)"
          >
            <span>{{ roomRow.roomId.slice(0, 8) }}</span>
            <em>{{ roomRow.redScore }} : {{ roomRow.blueScore }}</em>
          </button>
        </div>
      </section>

      <section class="gobang-record-panel">
        <div class="game-section-head">
          <div>
            <h2>最近对局</h2>
          </div>
        </div>
        <el-table class="gobang-record-table tetris-pk-record-table" :data="records" size="small" stripe empty-text="暂无对局记录">
          <el-table-column label="房间" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.roomId || '-' }}</template>
          </el-table-column>
          <el-table-column label="对手" min-width="100" show-overflow-tooltip>
            <template #default="{ row }">{{ row.opponentNickname || '-' }}</template>
          </el-table-column>
          <el-table-column label="比分" width="96" align="center">
            <template #default="{ row }">{{ row.myScore ?? 0 }} : {{ row.opponentScore ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="结果" width="100" align="center" class-name="tetris-pk-result-col" label-class-name="tetris-pk-result-col">
            <template #default="{ row }">
              <span :class="['gobang-result-tag', { 'is-win': row.winnerUserId === profile.userId }]">
                {{ row.winnerUserId === profile.userId ? '胜利' : '失败' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="积分" width="72" align="center">
            <template #default="{ row }">
              <span :class="['gobang-score-delta', { 'is-plus': Number(row.scoreDelta) > 0 }]">
                {{ formatScoreDelta(row.scoreDelta) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="结束原因" min-width="96" show-overflow-tooltip>
            <template #default="{ row }">{{ endReasonText(row.endReason) }}</template>
          </el-table-column>
          <el-table-column label="结束时间" min-width="156" show-overflow-tooltip>
            <template #default="{ row }">{{ formatDateTime(row.endedAt) }}</template>
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
  </div>
</template>

<script setup src="@scripts/views/TetrisPkGame.js"></script>
<style scoped src="@/assets/styles/gobang-game.css"></style>
