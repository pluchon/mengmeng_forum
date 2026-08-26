<template>
  <el-drawer
    v-model="visible"
    direction="rtl"
    size="460px"
    append-to-body
    destroy-on-close
    :with-header="false"
    class="hot-ranking-drawer"
    modal-class="hot-ranking-drawer-modal"
  >
    <div class="hot-ranking-drawer__body">
      <button type="button" class="hot-ranking-drawer__close" aria-label="关闭" @click="visible = false">
        <el-icon :size="18"><Close /></el-icon>
      </button>

      <div class="hot-ranking-drawer__filter">
        <div class="hot-ranking-drawer__period is-active">
          <span class="hot-ranking-drawer__period-label">
            <el-icon class="hot-ranking-drawer__flame"><TrendCharts /></el-icon>
            今日热帖
          </span>
          <span class="hot-ranking-drawer__period-line" />
        </div>
      </div>

      <div class="hot-ranking-drawer__divider" />

      <div v-loading="loading" class="hot-ranking-drawer__content">
        <div class="hot-ranking-drawer__scroll">
          <div v-if="error" class="hot-ranking-drawer__state hot-ranking-drawer__state--error">
            <span>{{ error }}</span>
            <button type="button" class="hot-ranking-drawer__retry" @click="loadList(pageNum)">重试</button>
          </div>
          <div v-else-if="!loading && !list.length" class="hot-ranking-drawer__state">暂无热帖</div>
          <div v-else class="hot-ranking-drawer__list">
            <button
              v-for="(item, index) in list"
              :key="item.article?.id || item.rank"
              type="button"
              class="hot-ranking-row"
              :class="rowClass(item.rank)"
              @click="openArticle(item)"
            >
              <span class="hot-ranking-row__rank" :class="rankClass(item.rank)">{{ item.rank }}</span>
              <span class="hot-ranking-row__thumb" :style="{ background: thumbBg(index) }">
                <img
                  v-if="item.article?.coverImg"
                  :src="item.article.coverImg"
                  alt=""
                  class="hot-ranking-row__thumb-img"
                />
                <span v-else class="hot-ranking-row__thumb-fallback">
                  {{ (item.article?.title || '?').slice(0, 1) }}
                </span>
              </span>
              <span class="hot-ranking-row__info">
                <span class="hot-ranking-row__title" :title="item.article?.title">
                  {{ item.article?.title || '未命名帖子' }}
                </span>
                <span class="hot-ranking-row__meta">
                  <el-icon class="hot-ranking-row__meta-icon"><User /></el-icon>
                  <span class="hot-ranking-row__nick">{{ item.user?.nickname || '匿名用户' }}</span>
                  <LikeCountIcon class="hot-ranking-row__heart" />
                  <span>{{ formatLikeCount(item.article?.likeCount) }} 赞</span>
                </span>
              </span>
              <span class="hot-ranking-row__heat">
                <el-icon
                  v-if="trendOf(item) === 'UP'"
                  class="hot-ranking-row__trend hot-ranking-row__trend--up"
                >
                  <TopRight />
                </el-icon>
                <el-icon
                  v-else-if="trendOf(item) === 'DOWN'"
                  class="hot-ranking-row__trend hot-ranking-row__trend--down"
                >
                  <BottomRight />
                </el-icon>
                <span
                  v-else
                  class="hot-ranking-row__trend hot-ranking-row__trend--stable"
                  aria-hidden="true"
                >—</span>
                <span class="hot-ranking-row__heat-text">{{ formatHotScore(item.hotScore) }}</span>
              </span>
            </button>
          </div>
        </div>
        <div class="hot-ranking-drawer__pager">
          <AppPagination
            v-model:current-page="pageNum"
            size="small"
            :total="total"
            :page-size="HOT_RANK_PAGE_SIZE"
            :pager-count="5"
            :show-jumper="false"
            :disabled="loading"
            @current-change="onPageChange"
          />
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup src="./HotRankingDialog.js"></script>
<style src="./HotRankingDialog.css"></style>
