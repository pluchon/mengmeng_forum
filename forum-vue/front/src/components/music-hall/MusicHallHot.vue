<template>
  <section class="music-hall-module-card music-hall-hot">
    <header class="music-hall-module-card__head">
      <div class="music-hall-module-card__head-left">
        <span class="music-hall-module-card__icon" aria-hidden="true">
          <el-icon><TrendCharts /></el-icon>
        </span>
        <h3 class="music-hall-module-card__title">本周热榜</h3>
      </div>
      <span class="music-hall-module-card__hint">每周一更新</span>
    </header>
    <div class="music-hall-module-card__body">
      <div class="music-hall-hot__viewport">
        <div v-if="loading" class="music-hall-hot__loading">加载中...</div>
        <MusicHallEmpty v-else-if="!tracks.length" text="暂无热榜数据" compact />
        <div v-else class="music-hall-hot__grid">
          <article
            v-for="track in tracks"
            :key="track.musicKey"
            class="music-hall-hot__item"
            :class="{
              'is-top': track.rank === 1,
              'is-active': track.musicKey === activeMusicKey,
            }"
          >
            <span class="music-hall-hot__rank">{{ track.rank }}</span>
            <div class="music-hall-hot__cover" :style="coverStyle(track)">
              <img v-if="track.coverUrl" :src="track.coverUrl" alt="">
            </div>
            <div class="music-hall-hot__meta">
              <div class="music-hall-hot__name">{{ track.title }}</div>
              <div class="music-hall-hot__sub">
                <span>{{ track.artist || '曲库配乐' }}</span>
                <span class="music-hall-hot__dot">·</span>
                <span>{{ track.durationText || '--:--' }}</span>
              </div>
            </div>
            <div v-if="track.playCountText" class="music-hall-hot__stats">{{ track.playCountText }} 播放</div>
            <button
              type="button"
              class="music-hall-hot__play"
              aria-label="播放"
              @click="onPlay(track)"
            >
              <el-icon><VideoPlay /></el-icon>
            </button>
          </article>
        </div>
      </div>
      <div
        class="music-hall-hot__pager"
        :aria-hidden="!(!loading && tracks.length && pageTotal > 1)"
      >
        <AppPagination
          v-if="!loading && tracks.length && pageTotal > 1"
          :current-page="pageNum"
          :total="pageTotal"
          :page-size="1"
          @current-change="onPageChange"
        />
      </div>
    </div>
  </section>
</template>

<script src="./MusicHallHot.js"></script>
<style lang="scss" src="./music-hall-module.scss"></style>
<style lang="scss" src="./MusicHallHot.scss"></style>
