<template>
  <div ref="playerRootRef" class="detail-video-player" @contextmenu.prevent>
    <video
      ref="videoEl"
      class="detail-video-player__media"
      :poster="poster || undefined"
      playsinline
      preload="metadata"
      @loadedmetadata="onLoadedMetadata"
      @timeupdate="onTimeUpdate"
      @ended="onEnded"
      @error="onMediaError"
      @click="togglePlay"
    />
    <div
      v-if="loadError"
      class="detail-video-player__error"
      role="alert"
    >
      <span class="detail-video-player__error-text">视频加载失败</span>
      <button
        type="button"
        class="detail-video-player__error-retry"
        @click.stop="retryLoad"
      >
        重试
      </button>
    </div>
    <div
      v-else-if="showProcessingHint"
      class="detail-video-player__processing-hint"
    >
      流处理中，正在播放原片
    </div>
    <div
      v-if="settings.enabled"
      class="detail-video-player__danmaku-layer"
      :style="danmakuLayerStyle"
      @mouseenter="onDanmakuLayerEnter"
      @mouseleave="onDanmakuLayerLeave"
    >
      <div
        v-for="item in danmakuVisibleItems"
        :key="item.key"
        class="detail-video-player__danmaku-item"
        :class="{
          'is-fixed-top': item.mode === 1,
          'is-fixed-bottom': item.mode === 2,
          'is-hovered': hoveredDanmakuKey === item.key,
        }"
        :style="item.style"
        @mouseenter="onDanmakuItemEnter(item)"
        @mouseleave="onDanmakuItemLeave"
      >
        <span class="detail-video-player__danmaku-text">{{ item.content }}</span>
        <span
          v-if="item.likeCount >= 3"
          class="detail-video-player__danmaku-like-count"
        >{{ item.likeCount }}</span>
        <span
          v-if="hoveredDanmakuKey === item.key && isLoggedIn"
          class="detail-video-player__danmaku-actions"
        >
          <button
            type="button"
            class="detail-video-player__danmaku-action-btn"
            aria-label="点赞弹幕"
            @click.stop="toggleDanmakuLike(item)"
          >
            <LikeCountIcon class="detail-video-player__danmaku-like-icon" :filled="item.liked" />
          </button>
          <button
            type="button"
            class="detail-video-player__danmaku-action-btn"
            aria-label="举报弹幕"
            @click.stop="reportDanmakuItem(item)"
          >
            <el-icon :size="12"><Flag /></el-icon>
          </button>
        </span>
      </div>
    </div>
    <div class="detail-video-player__controls">
      <button
        type="button"
        class="detail-video-player__btn detail-video-player__btn--icon detail-video-player__btn--play"
        aria-label="播放或暂停"
        @click="togglePlay"
      >
        <el-icon :size="22">
          <VideoPause v-if="playing" />
          <VideoPlay v-else />
        </el-icon>
      </button>
      <span class="detail-video-player__time">{{ formatTime(currentTime) }} / {{ formatTime(duration) }}</span>
      <div
        class="detail-video-player__progress"
        role="slider"
        tabindex="0"
        aria-label="播放进度"
        :aria-valuenow="Math.round(progressPercent)"
        aria-valuemin="0"
        aria-valuemax="100"
        @pointerdown="onProgressPointerDown"
        @pointermove="onProgressPointerMove"
        @pointerup="onProgressPointerUp"
        @pointercancel="onProgressPointerUp"
        @keydown="onProgressKeydown"
      >
        <div class="detail-video-player__progress-rail">
          <div class="detail-video-player__progress-fill" :style="{ width: progressPercent + '%' }" />
          <div class="detail-video-player__progress-thumb" :style="{ left: progressPercent + '%' }" />
        </div>
      </div>
      <div class="detail-video-player__danmu-send">
        <div class="detail-video-player__danmu-settings">
          <button
            type="button"
            class="detail-video-player__danmu-settings-btn"
            aria-label="弹幕设置"
            @click.stop="toggleSettings"
          >
            弹幕
          </button>
          <div v-if="settingsOpen" class="detail-video-player__danmu-settings-panel" @click.stop>
            <div class="detail-video-player__danmu-settings-row">
              <span>显示弹幕</span>
              <el-switch :model-value="settings.enabled" @change="setEnabled" />
            </div>
            <div class="detail-video-player__danmu-settings-row">
              <span>不透明度</span>
              <el-slider
                :model-value="settings.opacity"
                :min="0.2"
                :max="1"
                :step="0.05"
                :show-tooltip="false"
                @update:model-value="setOpacity"
              />
            </div>
            <div class="detail-video-player__danmu-settings-row detail-video-player__danmu-settings-row--area">
              <span>显示区域</span>
              <div class="detail-video-player__danmu-area-options">
                <button
                  v-for="opt in DANMAKU_AREA_OPTIONS"
                  :key="opt.value"
                  type="button"
                  class="detail-video-player__danmu-area-btn"
                  :class="{ 'is-active': settings.areaPercent === opt.value }"
                  @click="setAreaPercent(opt.value)"
                >
                  {{ opt.label }}
                </button>
              </div>
            </div>
            <div class="detail-video-player__danmu-settings-row detail-video-player__danmu-settings-row--stack">
              <span>类型显示</span>
              <div class="detail-video-player__danmu-chip-group">
                <button
                  v-for="opt in DANMAKU_TYPE_FILTER_OPTIONS"
                  :key="opt.key"
                  type="button"
                  class="detail-video-player__danmu-chip"
                  :class="{ 'is-active': settings[opt.key] !== false }"
                  @click="setTypeFilter(opt.key, !settings[opt.key])"
                >
                  {{ opt.label }}
                </button>
              </div>
            </div>
            <div class="detail-video-player__danmu-settings-row detail-video-player__danmu-settings-row--stack">
              <span>弹幕密度</span>
              <div class="detail-video-player__danmu-chip-group">
                <button
                  v-for="opt in DANMAKU_DENSITY_OPTIONS"
                  :key="opt.value"
                  type="button"
                  class="detail-video-player__danmu-chip"
                  :class="{ 'is-active': settings.density === opt.value }"
                  @click="setDensity(opt.value)"
                >
                  {{ opt.label }}
                </button>
              </div>
            </div>
            <div class="detail-video-player__danmu-settings-row">
              <span>只看彩色</span>
              <el-switch :model-value="settings.coloredOnly" @change="setColoredOnly" />
            </div>
          </div>
        </div>
        <div class="detail-video-player__danmu-input-wrap">
          <div class="detail-video-player__danmu-format">
            <button
              type="button"
              class="detail-video-player__danmu-format-btn"
              :class="{ 'is-format-set': danmuFormatCustomized }"
              aria-label="弹幕样式"
              @click.stop="toggleColorPicker"
            >
              <span
                class="detail-video-player__danmu-format-a"
                :style="{ color: danmuFormatIconColor }"
              >A</span>
              <span
                class="detail-video-player__danmu-format-line"
                :style="{ backgroundColor: danmuFormatCustomized ? danmuColorHex : danmuFormatIconColor }"
              />
            </button>
            <div v-if="colorPickerOpen" class="detail-video-player__danmu-style-panel" @click.stop>
              <div class="detail-video-player__danmu-style-section">
                <span class="detail-video-player__danmu-style-label">颜色</span>
                <div class="detail-video-player__danmu-colors">
                  <button
                    v-for="item in DANMAKU_COLOR_PRESETS"
                    :key="item.code"
                    type="button"
                    class="detail-video-player__danmu-color-item"
                    :class="{ 'is-active': danmuColorCode === item.code }"
                    :aria-label="item.label"
                    @click="selectDanmuColor(item.code)"
                  >
                    <span class="detail-video-player__danmu-color-dot" :style="{ backgroundColor: item.hex }" />
                  </button>
                </div>
              </div>
              <div class="detail-video-player__danmu-style-section">
                <span class="detail-video-player__danmu-style-label">模式</span>
                <div class="detail-video-player__danmu-chip-group">
                  <button
                    v-for="item in DANMAKU_MODE_OPTIONS"
                    :key="item.value"
                    type="button"
                    class="detail-video-player__danmu-chip"
                    :class="{ 'is-active': danmuMode === item.value }"
                    @click="selectDanmuMode(item.value)"
                  >
                    {{ item.label }}
                  </button>
                </div>
              </div>
              <div class="detail-video-player__danmu-style-section">
                <span class="detail-video-player__danmu-style-label">字号</span>
                <div class="detail-video-player__danmu-chip-group">
                  <button
                    v-for="item in DANMAKU_FONT_SIZE_OPTIONS"
                    :key="item.value"
                    type="button"
                    class="detail-video-player__danmu-chip"
                    :class="{ 'is-active': danmuFontSize === item.value }"
                    @click="selectDanmuFontSize(item.value)"
                  >
                    {{ item.label }}
                  </button>
                </div>
              </div>
            </div>
          </div>
          <textarea
            v-model="danmuText"
            class="detail-video-player__danmu-input"
            rows="1"
            :maxlength="DANMAKU_MAX_CONTENT_LENGTH"
            placeholder="发个弹幕冒个泡~"
            @focus="onDanmuInputFocus"
            @blur="onDanmuInputBlur"
            @keydown.enter.exact.prevent="sendDanmu"
          />
          <button
            type="button"
            class="detail-video-player__danmu-send-btn"
            :disabled="danmuSendDisabled"
            @click="sendDanmu"
          >
            发送
          </button>
        </div>
      </div>
      <div class="detail-video-player__volume">
        <button
          type="button"
          class="detail-video-player__btn detail-video-player__btn--icon"
          :aria-label="muted || volume <= 0 ? '取消静音' : '音量'"
          @click="toggleMute"
        >
          <VideoVolumeIcon
            class="detail-video-player__speaker-icon"
            :muted="muted || volume <= 0"
            :size="18"
          />
        </button>
        <div class="detail-video-player__volume-popup">
          <input
            class="detail-video-player__volume-range detail-video-player__volume-range--vertical"
            type="range"
            min="0"
            max="1"
            step="0.01"
            :value="volume"
            orient="vertical"
            @input="onVolumeInput($event.target.value)"
          >
        </div>
      </div>
      <div class="detail-video-player__speed">
        <button type="button" class="detail-video-player__btn" @click="speedMenuOpen = !speedMenuOpen">
          {{ playbackRate }}x
        </button>
        <div v-if="speedMenuOpen" class="detail-video-player__speed-menu">
          <button
            v-for="s in SPEEDS"
            :key="s"
            type="button"
            class="detail-video-player__speed-item"
            :class="{ 'is-active': playbackRate === s }"
            @click="setSpeed(s)"
          >
            {{ s }}x
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup src="@scripts/components/article/ArticleDetailVideo.js"></script>
<style scoped src="@/assets/styles/article-detail-video.css"></style>
