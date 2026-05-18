<template>
  <div class="shell-main-stack shell-page-scroll">
    <div v-if="showBoardPillsRow" class="home-xhs-board-row">
      <template v-if="boardsInCategory.length">
        <button
          v-for="b in boardsInCategory"
          :key="b.id"
          type="button"
          class="home-board-pill"
          :class="{ 'is-active': currentBoardId === b.id }"
          @click="selectBoardPill(b.id)"
        >
          {{ b.name }}
        </button>
      </template>
      <span v-else class="home-board-empty">该分类下暂无板块</span>
    </div>

    <main class="home-xhs-main">
      <div v-if="showCheckinHomeStrip && !isHotFeed" class="checkin-home-strip animate-fade-up">
        <el-card
          class="checkin-home-card"
          :class="{ 'checkin-home-done': checkinSummary.todaySigned }"
          shadow="hover"
          :body-style="{ padding: '14px 18px', cursor: 'pointer', position: 'relative' }"
          @click="$router.push('/checkin')"
        >
          <el-button
            v-if="checkinSummary.todaySigned"
            class="checkin-home-close"
            type="info"
            text
            circle
            size="small"
            :icon="Close"
            aria-label="收起签到提示"
            @click.stop="dismissCheckinHomeStrip"
          />
          <div class="checkin-home-inner">
            <PawCoinIcon class="checkin-home-paw" />
            <div class="checkin-home-text">
              <div class="checkin-home-title">每日签到 · 萌币</div>
              <div class="checkin-home-meta">
                已攒 <strong>{{ checkinSummary.totalPoints ?? 0 }}</strong> 萌币
                · 连续 <strong>{{ checkinSummary.streakDays ?? 0 }}</strong> 天
                <el-tag v-if="checkinSummary.todaySigned" size="small" type="success" round class="checkin-home-tag">
                  今日已签
                </el-tag>
                <el-tag v-else size="small" type="warning" effect="plain" round class="checkin-home-tag">待签到</el-tag>
              </div>
            </div>
            <el-button
              v-if="!checkinSummary.todaySigned"
              type="primary"
              round
              size="small"
              @click.stop="$router.push('/checkin')"
            >
              去签到
            </el-button>
            <el-button v-else type="success" round size="small" disabled>
              <el-icon class="checkin-home-check"><CircleCheck /></el-icon>
              今日已签
            </el-button>
          </div>
        </el-card>
      </div>

      <div v-if="loading" class="home-masonry home-masonry--loading">
        <div v-for="i in 8" :key="i" class="home-masonry-item">
          <el-skeleton animated :rows="6" class="skeleton-card" />
        </div>
      </div>

      <div v-else-if="isHotFeed" class="home-masonry">
        <div v-for="row in hotFeedList" :key="row.article?.id" class="home-masonry-item">
          <el-card
            class="note-card note-card--masonry animate-fade-up"
            :body-style="{ padding: '0px' }"
            shadow="hover"
            @click="$router.push(`/article/${row.article.id}`)"
          >
            <div class="note-cover note-cover--fluid">
              <img
                v-if="coverImageUrl(row)"
                class="note-cover-img"
                :src="coverImageUrl(row)"
                :alt="row.article?.title || ''"
                loading="lazy"
              />
              <div
                v-else
                class="note-cover-placeholder"
                :style="{
                  background: getRandomPastel(),
                  minHeight: placeholderMinHeight(row.article?.id),
                }"
              >
                <span class="cover-title">{{ (row.article?.title || '').substring(0, 12) }}</span>
              </div>
            </div>
            <div class="note-info">
              <h3 class="note-title">{{ row.article?.title }}</h3>
              <div class="note-footer">
                <div class="author">
                  <UserAvatarVip
                    :size="22"
                    :src="row.user?.avatarUrl || defaultAvatar"
                    :vip-tier="Number(row.user?.vipTier) || 0"
                    :vip-expire-at="row.user?.vipExpireAt"
                  />
                  <span class="nickname">{{ row.user?.nickname }}</span>
                </div>
                <div class="likes">
                  <LikeCountIcon />
                  <span>{{ row.article?.likeCount }}</span>
                </div>
              </div>
            </div>
          </el-card>
        </div>
      </div>

      <div v-else class="home-masonry">
        <div v-for="item in articleList" :key="item.article.id" class="home-masonry-item">
          <el-card
            class="note-card note-card--masonry animate-fade-up"
            :body-style="{ padding: '0px' }"
            shadow="hover"
            @click="$router.push(`/article/${item.article.id}`)"
          >
            <div class="note-cover note-cover--fluid">
              <img
                v-if="coverImageUrl(item)"
                class="note-cover-img"
                :src="coverImageUrl(item)"
                :alt="item.article.title"
                loading="lazy"
              />
              <div
                v-else
                class="note-cover-placeholder"
                :style="{
                  background: getRandomPastel(),
                  minHeight: placeholderMinHeight(item.article?.id),
                }"
              >
                <span class="cover-title">{{ item.article.title.substring(0, 12) }}</span>
              </div>
            </div>
            <div class="note-info">
              <h3 class="note-title">{{ item.article.title }}</h3>
              <div class="note-footer">
                <div class="author">
                  <UserAvatarVip
                    :size="22"
                    :src="item.user?.avatarUrl || defaultAvatar"
                    :vip-tier="Number(item.user?.vipTier) || 0"
                    :vip-expire-at="item.user?.vipExpireAt"
                  />
                  <span class="nickname">{{ item.user.nickname }}</span>
                </div>
                <div class="likes">
                  <LikeCountIcon />
                  <span>{{ item.article.likeCount }}</span>
                </div>
              </div>
            </div>
          </el-card>
        </div>
      </div>

      <div v-if="!isHotFeed && total > pageSize" class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          :total="total"
          :page-size="pageSize"
          layout="prev, pager, next, jumper"
          background
          @current-change="fetchArticles"
        />
      </div>

      <el-empty
        v-if="!loading && ((isHotFeed && hotFeedList.length === 0) || (!isHotFeed && articleList.length === 0))"
        :description="isHotFeed ? '暂无热帖' : '这里还没有笔记哦'"
      />
    </main>
  </div>
</template>

<script setup>
defineOptions({ name: 'HomeFeed' })

import PawCoinIcon from '@/components/common/PawCoinIcon.vue'
import LikeCountIcon from '@/components/common/LikeCountIcon.vue'
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import { useHomeShellContext } from '@/composables/useHomeShell'

const {
  CircleCheck,
  Close,
  articleList,
  boardsInCategory,
  checkinSummary,
  coverImageUrl,
  currentBoardId,
  defaultAvatar,
  dismissCheckinHomeStrip,
  fetchArticles,
  getRandomPastel,
  hotFeedList,
  isHotFeed,
  loading,
  pageNum,
  pageSize,
  placeholderMinHeight,
  selectBoardPill,
  showBoardPillsRow,
  showCheckinHomeStrip,
  total,
} = useHomeShellContext()
</script>

<style scoped>
.shell-main-stack {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
</style>
