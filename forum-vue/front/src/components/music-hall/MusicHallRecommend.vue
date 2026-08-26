<template>
  <section class="music-hall-module-card music-hall-recommend">
    <header class="music-hall-module-card__head">
      <div class="music-hall-module-card__head-left">
        <span class="music-hall-module-card__icon" aria-hidden="true">
          <el-icon><MagicStick /></el-icon>
        </span>
        <h3 class="music-hall-module-card__title">推荐</h3>
      </div>
    </header>
    <div class="music-hall-module-card__body">
      <div class="music-hall-recommend__viewport">
        <div v-if="loading" class="music-hall-recommend__loading">加载中...</div>
        <MusicHallEmpty v-else-if="!tracks.length" text="暂无推荐歌曲" compact />
        <div v-else class="music-hall-recommend__grid">
          <article
            v-for="track in tracks"
            :key="track.musicKey"
            class="music-hall-recommend__card"
            :class="{ 'is-active': track.musicKey === activeMusicKey }"
          >
            <div class="music-hall-recommend__cover" :style="coverStyle(track)">
              <img v-if="track.coverUrl" :src="track.coverUrl" alt="">
            </div>
            <div class="music-hall-recommend__meta">
              <div class="music-hall-recommend__name">{{ track.title }}</div>
              <div class="music-hall-recommend__artist">{{ track.artist || '曲库配乐' }}</div>
            </div>
            <div class="music-hall-recommend__stats">
              <div class="music-hall-recommend__duration">{{ track.durationText || '--:--' }}</div>
              <div v-if="track.playCountText" class="music-hall-recommend__plays">{{ track.playCountText }} 播放</div>
            </div>
            <button
              type="button"
              class="music-hall-recommend__play"
              aria-label="播放"
              @click="onPlay(track)"
            >
              <el-icon><VideoPlay /></el-icon>
            </button>
          </article>
        </div>
      </div>
      <div
        class="music-hall-recommend__pager"
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

<script src="./MusicHallRecommend.js"></script>
<style lang="scss" src="./music-hall-module.scss"></style>
<style lang="scss" src="./MusicHallRecommend.scss"></style>
