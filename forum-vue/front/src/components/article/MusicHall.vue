<template>
  <div
    v-if="isActive"
    class="music-hall"
    :class="{ 'music-hall--embedded': embedded }"
    :role="isPickerMode ? 'dialog' : undefined"
    :aria-modal="isPickerMode ? 'true' : undefined"
    :aria-label="isPickerMode ? '选择帖子音乐' : undefined"
  >
    <header v-if="isPickerMode" class="music-hall__top">
      <div class="music-hall__top-left">
        <button type="button" class="music-hall__back" aria-label="返回" @click="close">
          <el-icon><ArrowLeft /></el-icon>
        </button>
        <div class="music-hall__title">选择帖子音乐</div>
      </div>
      <div class="music-hall__top-right">
        <div v-if="canConfirmSelection" class="music-hall__picked-chip">
          <el-icon><Headset /></el-icon>
          <span>已选择 · {{ draftSelected.title }}</span>
        </div>
        <button
          v-if="canConfirmSelection"
          type="button"
          class="music-hall__confirm"
          @click="confirm"
        >
          <el-icon><Check /></el-icon>
          确认并返回
        </button>
      </div>
    </header>

    <div class="music-hall__body">
      <section class="music-hall__library">
        <template v-if="showDiscoverPanel">
        <div class="music-hall__search-row">
          <select
            v-model="catalogScope"
            class="music-hall__scope-select"
            aria-label="搜索范围"
            @change="onCatalogScopeChange"
          >
            <option v-for="item in catalogScopeOptions" :key="item.id" :value="item.id">
              {{ item.label }}
            </option>
          </select>
          <div class="music-hall__search" :class="{ 'is-ai': aiSearchEnabled, 'is-pending': keyword.trim() !== appliedKeyword }">
            <button
              type="button"
              class="music-hall__search-ai-toggle"
              :class="{ 'is-active': aiSearchEnabled }"
              :aria-pressed="aiSearchEnabled"
              @click="toggleAiSearchMode"
            >
              AI
            </button>
            <el-icon class="music-hall__search-icon" aria-hidden="true"><Search /></el-icon>
            <input
              v-model="keyword"
              type="search"
              class="music-hall__search-input"
              :placeholder="aiSearchEnabled ? '向 AI 描述你想找的歌曲' : '搜你想听......'"
              @keydown.enter.prevent="onCatalogSearch"
              @input="onSearchKeywordInput"
              @search="onSearchKeywordInput"
            >
            <button
              type="button"
              class="music-hall__search-confirm"
              :disabled="loading || aiLoading"
              @click="onCatalogSearch"
            >
              确认
            </button>
          </div>
          <button
            v-if="!embedded"
            type="button"
            class="music-hall__ai-btn"
            :disabled="aiLoading"
            @click="onAiRecommend"
          >
            <el-icon><MagicStick /></el-icon>
            AI推荐
          </button>
        </div>

        <MusicHallDiscover
          v-if="showEmbeddedDiscoverFeed"
          :active-music-key="previewTrack?.musicKey || ''"
          @play="selectTrack"
        />

        <template v-else>
        <BorderGlow
          class="music-hall__track-glow"
          :animated="aiLoading"
          :edge-sensitivity="30"
          glow-color="320 84 72"
          background-color="#ffffff"
          :border-radius="16"
          :glow-radius="42"
          :glow-intensity="1.1"
          :cone-spread="28"
          :sweep-speed="100"
          :colors="['#f8b5d6', '#d8bcff', '#a3d7ff']"
        >
          <div class="music-hall__track-card">
            <div v-if="aiLoading" class="music-hall__ai-mask">
              <strong>AI 正在为你选曲...</strong>
            </div>
            <div class="music-hall__section-panel music-hall__section-panel--tracks">
              <div v-if="loading" class="music-hall__empty">曲库加载中...</div>
              <div v-else-if="loadError" class="music-hall__empty">{{ loadError }}</div>
              <div v-else-if="!filteredTracks.length" class="music-hall__empty-state">
                <img :src="emptyMusicUrl" alt="">
                <p>{{ catalogEmptyText }}</p>
              </div>
              <div v-else class="music-hall__track-grid">
                <button
                  v-for="track in filteredTracks"
                  :key="track.musicKey"
                  type="button"
                  class="music-hall__track"
                  :class="{
                    'is-selected': isPickerMode
                      ? draftSelected?.musicKey === track.musicKey
                      : previewTrack?.musicKey === track.musicKey,
                    'is-ai-match': track.aiMatched,
                  }"
                  @click="selectTrack(track)"
                >
                  <div class="music-hall__cover" :style="coverStyle(track)">
                    <img v-if="track.coverUrl" :src="track.coverUrl" alt="">
                    <el-icon v-else><Headset /></el-icon>
                  </div>
                  <div class="music-hall__meta">
                    <div class="music-hall__name">{{ track.title }}</div>
                    <div class="music-hall__sub">{{ artistText(track.artist) || '曲库配乐' }}</div>
                  </div>
                  <span class="music-hall__track-duration">
                    {{ track.durationText || '--:--' }}
                  </span>
                  <span
                    v-if="!isPickerMode"
                    class="music-hall__mine-play"
                    role="button"
                    aria-label="播放"
                  >
                    <el-icon><VideoPlay /></el-icon>
                  </span>
                  <span v-if="isPickerMode" class="music-hall__pick">
                    {{ draftSelected?.musicKey === track.musicKey ? '已选择' : '选择' }}
                  </span>
                </button>
              </div>
              <div v-if="catalogSearchMode && !aiSearchEnabled" class="music-hall__catalog-pager">
                <AppPagination
                  :current-page="catalogPageNum"
                  :total="catalogPageTotal"
                  :page-size="1"
                  :pager-count="5"
                  :show-jumper="false"
                  :hide-on-single-page="false"
                  @current-change="onCatalogPageChange"
                />
              </div>
            </div>
          </div>
        </BorderGlow>
        </template>
        </template>

        <!-- 创作页与公众大厅统一成竖排卡片：创作页原来是四个 tab 挤在一个卡片里，
             上传歌曲/我的上传属于公众大厅的创作界面，不该出现在选配乐的场景 -->
        <div v-if="showMinePanel" class="music-hall__mine-stack">
          <section class="music-hall__module-card music-hall__module-card--favorite">
            <header class="music-hall__module-card__head">
              <div class="music-hall__module-card__head-left">
                <span class="music-hall__module-card__icon" aria-hidden="true">
                  <el-icon><Star /></el-icon>
                </span>
                <h3 class="music-hall__module-card__title">我的收藏</h3>
              </div>
            </header>
            <div class="music-hall__module-card__body">
            <div v-if="!favoriteTracks.length" class="music-hall__empty-state music-hall__empty-state--fixed">
              <img :src="emptyMusicUrl" alt="">
              <p>暂无收藏</p>
            </div>
            <div v-else class="music-hall__mine-grid music-hall__mine-grid--favorite">
              <button
                v-for="track in favoriteTracks"
                :key="`fav-${track.musicKey}`"
                type="button"
                class="music-hall__mine-item"
                :class="{
                  'is-selected': isPickerMode
                    ? draftSelected?.musicKey === track.musicKey
                    : previewTrack?.musicKey === track.musicKey,
                }"
                @click="selectTrack(track)"
              >
                <div class="music-hall__cover music-hall__cover--mine" :style="coverStyle(track)">
                  <img v-if="track.coverUrl" :src="track.coverUrl" alt="">
                  <el-icon v-else><Headset /></el-icon>
                </div>
                <div class="music-hall__meta">
                  <div class="music-hall__name">{{ track.title }}</div>
                  <div class="music-hall__sub">{{ artistText(track.artist) || '未知歌手' }}</div>
                </div>
                <span class="music-hall__mine-duration">
                  {{ track.durationText || '--:--' }}
                </span>
                <button
                  v-if="!isPickerMode"
                  type="button"
                  class="music-hall__mine-play"
                  aria-label="播放"
                  @click.stop="selectTrack(track)"
                >
                  <el-icon><VideoPlay /></el-icon>
                </button>
                <span v-if="isPickerMode" class="music-hall__pick">
                  {{ draftSelected?.musicKey === track.musicKey ? '已选择' : '选择' }}
                </span>
              </button>
            </div>
            <div class="music-hall__mine-pager">
              <AppPagination
                :current-page="favoritePage"
                :total="favoritePageTotal"
                :page-size="1"
                size="small"
                :pager-count="5"
                :show-jumper="false"
                :hide-on-single-page="false"
                @current-change="onFavoritePageChange"
              />
            </div>
            </div>
          </section>

          <component
            :is="embedded ? 'section' : 'div'"
            v-if="embedded"
            :class="embedded ? 'music-hall__module-card' : undefined"
          >
            <header v-if="embedded" class="music-hall__module-card__head">
              <div class="music-hall__module-card__head-left">
                <span class="music-hall__module-card__icon" aria-hidden="true">
                  <el-icon><Upload /></el-icon>
                </span>
                <h3 class="music-hall__module-card__title">上传歌曲</h3>
              </div>
              <button
                type="button"
                class="music-hall__parse-btn"
                :disabled="parsing || composeLocked"
                @click="onOneClickParse"
              >
                <el-icon><MagicStick /></el-icon>
                一键上传解析
              </button>
            </header>
          <div v-if="embedded || mineTab === 'compose'" class="music-hall__upload-panel">
            <div class="music-hall__drop-row">
              <button
                type="button"
                class="music-hall__drop music-hall__drop--audio"
                :class="{ 'is-filled': hasComposeAudio }"
                @click="pickUploadFile('audio')"
              >
                <template v-if="hasComposeAudio">
                  <div class="music-hall__drop-wave" aria-hidden="true">
                    <span
                      v-for="(h, idx) in composeWaveHeights"
                      :key="`cw-${idx}`"
                      class="music-hall__drop-wave-bar"
                      :style="{ height: `${h}px` }"
                    />
                  </div>
                  <div class="music-hall__drop-file">{{ songForm.title || songForm.audioName }}</div>
                  <div class="music-hall__drop-meta">{{ songForm.durationText || '--:--' }}</div>
                </template>
                <template v-else>
                  <div class="music-hall__drop-top">
                    <el-icon><Headset /></el-icon>
                    <span class="music-hall__drop-title">歌曲本体</span>
                    <span class="music-hall__drop-badge music-hall__drop-badge--req">必填</span>
                  </div>
                  <div class="music-hall__drop-desc">{{ AUDIO_EXT_HINT }} · ≤50MB</div>
                </template>
              </button>
              <button
                type="button"
                class="music-hall__drop music-hall__drop--cover"
                :class="{ 'is-filled': hasComposeCover }"
                @click="pickUploadFile('cover')"
              >
                <template v-if="hasComposeCover">
                  <div class="music-hall__drop-cover">
                    <img v-if="composeCoverPreviewUrl" :src="composeCoverPreviewUrl" alt="">
                    <div v-else class="music-hall__drop-cover-fallback">
                      <el-icon><Picture /></el-icon>
                    </div>
                  </div>
                  <div class="music-hall__drop-file">{{ songForm.coverName }}</div>
                </template>
                <template v-else>
                  <div class="music-hall__drop-top">
                    <el-icon><Picture /></el-icon>
                    <span class="music-hall__drop-title">歌曲封面</span>
                    <span class="music-hall__drop-badge">可选</span>
                  </div>
                  <div class="music-hall__drop-desc">建议 1:1 · jpg / png</div>
                </template>
              </button>
              <button
                type="button"
                class="music-hall__drop music-hall__drop--lrc"
                :class="{ 'is-filled': hasComposeLrc }"
                @click="pickUploadFile('lrc')"
              >
                <template v-if="hasComposeLrc">
                  <div class="music-hall__drop-lrc">
                    <p v-for="(line, idx) in composeLrcPreview" :key="`lp-${idx}`" class="music-hall__drop-lrc-line">
                      <span v-if="line.time && line.time !== '--:--'">{{ line.time }}</span>
                      {{ line.text }}
                    </p>
                  </div>
                  <div v-if="lrcFile?.name" class="music-hall__drop-file">{{ lrcFile.name }}</div>
                </template>
                <template v-else>
                  <div class="music-hall__drop-top">
                    <el-icon><Document /></el-icon>
                    <span class="music-hall__drop-title">歌曲歌词</span>
                    <span class="music-hall__drop-badge">可选</span>
                  </div>
                  <div class="music-hall__drop-desc">lrc / txt</div>
                </template>
              </button>
            </div>

            <div class="music-hall__meta-card">
              <div class="music-hall__meta-head">
                <div class="music-hall__meta-title">歌曲信息</div>
                <span v-if="songForm.parsed" class="music-hall__parsed-chip">
                  <el-icon><MagicStick /></el-icon>
                  已自动解析
                </span>
              </div>
              <div class="music-hall__meta-grid">
                <div class="music-hall__meta-row music-hall__meta-row--2">
                  <label class="music-hall__field">
                    <span>歌名</span>
                    <span class="music-hall__field-box">
                      <input v-model="songForm.title" type="text" placeholder="请填写歌名">
                      <el-icon><EditPen /></el-icon>
                    </span>
                  </label>
                  <label class="music-hall__field">
                    <span>歌手</span>
                    <span class="music-hall__field-box">
                      <input v-model="songForm.artist" type="text" placeholder="请填写歌手">
                      <el-icon><EditPen /></el-icon>
                    </span>
                  </label>
                </div>
                <div class="music-hall__meta-row music-hall__meta-row--2">
                  <label class="music-hall__field">
                    <span>专辑</span>
                    <span class="music-hall__field-box">
                      <input v-model="songForm.album" type="text" placeholder="可选">
                      <el-icon><EditPen /></el-icon>
                    </span>
                  </label>
                  <label class="music-hall__field">
                    <span>时长</span>
                    <span class="music-hall__field-box music-hall__field-box--readonly">
                      <input
                        :value="songForm.durationText"
                        type="text"
                        placeholder="00:00"
                        readonly
                        tabindex="-1"
                      >
                      <button
                        type="button"
                        class="music-hall__trim-btn"
                        :disabled="composeLocked || !audioFile"
                        aria-label="裁剪音频"
                        @click.stop="openTrimDialog"
                      >
                        <el-icon><Scissor /></el-icon>
                      </button>
                    </span>
                  </label>
                </div>
                <div class="music-hall__meta-row music-hall__meta-row--2">
                  <div class="music-hall__field">
                    <span>歌词</span>
                    <button
                      type="button"
                      class="music-hall__lrc-entry"
                      :class="{ 'is-invalid': composeLrcUnsupported }"
                      @click="openComposeLrc"
                    >
                      <span>
                        <el-icon><Document /></el-icon>
                        {{ songForm.lrcText ? '查看歌词' : '暂无歌词' }}
                      </span>
                      <span class="music-hall__lrc-entry-right">
                        <span v-if="composeLrcEntryHint" class="music-hall__lrc-entry-hint">
                          {{ composeLrcEntryHint }}
                        </span>
                        <el-icon><ArrowRight /></el-icon>
                      </span>
                    </button>
                  </div>
                  <div class="music-hall__field">
                    <span>标签</span>
                    <button type="button" class="music-hall__tag-entry" @click="openTagDialog">
                      <span v-if="!songForm.tags?.length" class="music-hall__tag-entry-empty">
                        暂无标签，AI 会自动选择
                      </span>
                      <span v-else class="music-hall__tag-entry-list">
                        <span v-for="tag in songForm.tags" :key="`ct-${tag}`">{{ tag }}</span>
                      </span>
                      <el-icon><ArrowRight /></el-icon>
                    </button>
                  </div>
                </div>
              </div>
              <div class="music-hall__meta-actions">
                <button type="button" class="music-hall__draft-btn" :disabled="composeSubmitting || composeLocked" @click="saveComposeDraft">存为草稿</button>
                <button type="button" class="music-hall__publish-btn" :disabled="composeSubmitting || composeLocked" @click="submitCompose">
                  <el-icon><Check /></el-icon>
                  确认发布
                </button>
              </div>
            </div>
          </div>
          </component>

          <component
            :is="embedded ? 'section' : 'div'"
            v-if="embedded || mineTab === 'upload'"
            :class="embedded ? 'music-hall__module-card' : undefined"
          >
            <header v-if="embedded" class="music-hall__module-card__head">
              <div class="music-hall__module-card__head-left">
                <span class="music-hall__module-card__icon" aria-hidden="true">
                  <el-icon><Folder /></el-icon>
                </span>
                <h3 class="music-hall__module-card__title">我的上传</h3>
              </div>
            </header>
          <div v-if="embedded" class="music-hall__my-upload">
            <div class="music-hall__upload-toolbar">
              <label class="music-hall__upload-search">
                <el-icon><Search /></el-icon>
                <input v-model="uploadKeyword" type="search" placeholder="搜索歌名 / 歌手" @input="onUploadSearchInput">
              </label>
              <div class="music-hall__status-track">
                <button
                  v-for="item in uploadStatusFilters"
                  :key="item.id"
                  type="button"
                  class="music-hall__status-chip"
                  :class="{ 'is-active': uploadStatus === item.id }"
                  @click="onUploadStatusChange(item.id)"
                >
                  {{ item.label }}
                </button>
              </div>
            </div>
            <div class="music-hall__section-panel music-hall__section-panel--mine">
              <div v-if="mineLoading" class="music-hall__empty">加载中...</div>
              <div v-else-if="!uploadTracks.length" class="music-hall__empty-state">
                <img :src="emptyMusicUrl" alt="">
                <p>没有歌曲</p>
              </div>
              <div v-else class="music-hall__mine-grid">
                <div
                  v-for="track in uploadTracks"
                  :key="`up-${track.musicKey}`"
                  class="music-hall__upload-row"
                >
                  <div class="music-hall__cover music-hall__cover--upload" :style="coverStyle(track)">
                    <img v-if="track.coverUrl" :src="track.coverUrl" alt="">
                    <el-icon v-else><Headset /></el-icon>
                  </div>
                  <div class="music-hall__meta">
                    <div class="music-hall__name">{{ track.title }}</div>
                    <div class="music-hall__sub">
                      {{ track.durationText || '--:--' }}
                      <span class="music-hall__status-tag" :class="`is-${track.status}`">{{ statusLabel(track.status) }}</span>
                    </div>
                    <p
                      v-if="track.reviewReason && (track.status === 'rejected' || isServiceReviewError(track))"
                      class="music-hall__reject-reason"
                      :title="track.reviewReason"
                    >
                      {{ track.reviewReason }}
                    </p>
                  </div>
                  <div class="music-hall__upload-mid" :title="artistFullText(track.artist)">
                    <span>{{ artistText(track.artist) }}</span>
                  </div>
                  <button type="button" class="music-hall__pick" @click="onUploadAction(track)">
                    {{ uploadActionLabel(track) }}
                  </button>
                </div>
              </div>
            </div>
            <div class="music-hall__mine-pager">
              <AppPagination
                :current-page="uploadPage"
                :total="uploadPageTotal"
                :page-size="1"
                size="small"
                :pager-count="5"
                :show-jumper="false"
                :hide-on-single-page="false"
                @current-change="onUploadPageChange"
              />
            </div>
          </div>
          </component>

          <section class="music-hall__module-card music-hall__module-card--publish">
            <header class="music-hall__module-card__head">
              <div class="music-hall__module-card__head-left">
                <span class="music-hall__module-card__icon" aria-hidden="true">
                  <el-icon><Promotion /></el-icon>
                </span>
                <h3 class="music-hall__module-card__title">我的发布</h3>
              </div>
            </header>
            <div class="music-hall__module-card__body">
            <div v-if="mineLoading" class="music-hall__empty">加载中...</div>
            <div v-else-if="!publishTracks.length" class="music-hall__empty-state music-hall__empty-state--fixed">
              <img :src="emptyMusicUrl" alt="">
              <p>暂无发布的歌曲</p>
            </div>
            <div v-else :class="embedded ? 'music-hall__mine-grid music-hall__mine-grid--publish' : 'music-hall__mine-grid'">
              <button
                v-for="track in publishTracks"
                :key="`pub-${track.musicKey}`"
                type="button"
                class="music-hall__mine-item"
                :class="{
                  'is-selected': isPickerMode
                    ? draftSelected?.musicKey === track.musicKey
                    : previewTrack?.musicKey === track.musicKey,
                  'music-hall__mine-item--publish': true,
                }"
                @click="selectTrack(track)"
              >
                <div class="music-hall__cover music-hall__cover--mine" :style="coverStyle(track)">
                  <img v-if="track.coverUrl" :src="track.coverUrl" alt="">
                  <el-icon v-else><Headset /></el-icon>
                </div>
                <div class="music-hall__meta">
                  <div class="music-hall__name">{{ track.title }}</div>
                  <div class="music-hall__sub">{{ artistText(track.artist) || '未知歌手' }}</div>
                </div>
                <span class="music-hall__mine-duration">
                  {{ track.durationText || '--:--' }}
                </span>
                <button
                  v-if="!isPickerMode"
                  type="button"
                  class="music-hall__mine-play"
                  aria-label="播放"
                  @click.stop="selectTrack(track)"
                >
                  <el-icon><VideoPlay /></el-icon>
                </button>
                <span v-if="isPickerMode" class="music-hall__pick">
                  {{ draftSelected?.musicKey === track.musicKey ? '已选择' : '选择' }}
                </span>
              </button>
            </div>
            <div class="music-hall__mine-pager">
              <AppPagination
                :current-page="publishPage"
                :total="publishPageTotal"
                :page-size="1"
                size="small"
                :pager-count="5"
                :show-jumper="false"
                :hide-on-single-page="false"
                @current-change="onPublishPageChange"
              />
            </div>
            </div>
          </section>

          <input ref="audioInputRef" class="music-hall__file-input" type="file" :accept="AUDIO_ACCEPT" @change="onUploadFileChange('audio', $event)">
          <input ref="coverInputRef" class="music-hall__file-input" type="file" accept=".jpg,.jpeg,.png,image/jpeg,image/png" @change="onUploadFileChange('cover', $event)">
          <input ref="lrcInputRef" class="music-hall__file-input" type="file" accept=".lrc,.txt,text/plain" @change="onUploadFileChange('lrc', $event)">
          <input ref="parseInputRef" class="music-hall__file-input" type="file" multiple :accept="`${AUDIO_ACCEPT},.jpg,.jpeg,.png,.gif,.lrc,.txt,image/jpeg,image/png,image/gif,text/plain`" @change="onParseFilesChange">
        </div>

      </section>

      <aside class="music-hall__preview">
        <div class="music-hall__player-card">
          <div class="music-hall__album-block">
            <div class="music-hall__album" :style="coverStyle(previewTrack)">
              <img v-if="previewTrack?.coverUrl" :src="previewTrack.coverUrl" alt="">
              <el-icon v-else :size="40"><Headset /></el-icon>
            </div>
            <div class="music-hall__player-title" :title="previewTrack?.title || ''">
              {{ previewTrack?.title || '选择一首歌开始听' }}
            </div>
            <div class="music-hall__player-meta">
              <div class="music-hall__player-sub">歌手：{{ previewArtistText }}</div>
              <div class="music-hall__player-sub">专辑：{{ previewAlbumText }}</div>
            </div>
            <div class="music-hall__tags">
              <span v-for="tag in previewTagsVisible" :key="`pt-${tag}`">{{ tag }}</span>
              <span
                v-if="previewTagsOverflow > 0"
                class="music-hall__tags-more"
                :title="previewTags.join('、')"
              >+{{ previewTagsOverflow }}</span>
              <span v-if="!previewTags.length" class="music-hall__tags-empty">暂无标签</span>
            </div>
          </div>

          <div
            class="music-hall__wave"
            :class="{ 'is-playing': playing, 'is-analyzing': waveAnalyzing }"
            aria-hidden="true"
          >
            <span
              v-for="(h, idx) in waveHeights"
              :key="`w-${idx}`"
              class="music-hall__wave-bar"
              :class="{ 'is-played': idx < wavePlayedCount }"
              :style="{ height: `${h}px`, animationDelay: `${(idx % 12) * 0.08}s` }"
            />
          </div>

          <div v-if="audioLoading || audioError" class="music-hall__audio-state">
            <span v-if="audioError" class="music-hall__audio-state--error">{{ audioError }}</span>
            <span v-else>音频加载中...</span>
          </div>

          <div class="music-hall__progress">
            <span>{{ formatTime(currentTime) }}</span>
            <button
              type="button"
              class="music-hall__progress-track"
              :class="{ 'is-dragging': dragging }"
              :disabled="!previewTrack?.audioUrl"
              @click="seekByClick"
              @pointerdown="onProgressPointerDown"
            >
              <span class="music-hall__progress-fill" :style="{ width: `${progressPercent}%` }" />
              <span
                class="music-hall__progress-thumb"
                :style="{ left: `${progressPercent}%` }"
                aria-hidden="true"
              />
            </button>
            <span>{{ formatTime(duration) }}</span>
          </div>

          <div class="music-hall__controls">
            <button
              v-if="!hidePreviewFavorite"
              type="button"
              class="music-hall__ctrl music-hall__ctrl--fav"
              :class="{ 'is-active': isPreviewFavorited }"
              aria-label="收藏"
              @click="onFavoriteToggle"
            >
              <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
                <path
                  fill="currentColor"
                  d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"
                />
              </svg>
            </button>
            <div v-else class="music-hall__ctrl-spacer" aria-hidden="true" />
            <div class="music-hall__controls-main">
              <button type="button" class="music-hall__ctrl music-hall__ctrl--skip" :disabled="!previewTrack" @click="playPrev">
                <el-icon><DArrowLeft /></el-icon>
              </button>
              <button type="button" class="music-hall__ctrl music-hall__ctrl--main" :disabled="!previewTrack?.audioUrl" @click="togglePlay">
                <el-icon v-if="playing" :size="22"><VideoPause /></el-icon>
                <el-icon v-else :size="22"><VideoPlay /></el-icon>
              </button>
              <button type="button" class="music-hall__ctrl music-hall__ctrl--skip" :disabled="!previewTrack" @click="playNext">
                <el-icon><DArrowRight /></el-icon>
              </button>
            </div>
            <button
              type="button"
              class="music-hall__ctrl music-hall__ctrl--lrc"
              :class="{ 'is-active': showLrc }"
              @click="toggleLrc"
            >
              词
            </button>
          </div>

          <div
            v-if="showLrc && hasPlayerLyrics"
            class="music-hall__lrc-viewport"
            @scroll.passive="onLrcScroll"
          >
            <div
              v-if="timedLrcLines.length"
              class="music-hall__lrc-strip"
              :style="karaokeStripStyle"
            >
              <p
                v-for="(line, idx) in timedLrcLines"
                :key="`tl-${idx}`"
                class="music-hall__lrc-row"
                :class="{ 'is-active': idx === karaokeActiveIndex }"
              >
                {{ line.text }}
              </p>
            </div>
            <div
              v-else-if="plainLrcLines.length"
              class="music-hall__lrc-strip"
              :style="{ transform: `translateY(-${plainLrcOffset}px)` }"
            >
              <p
                v-for="(line, idx) in plainLrcLines"
                :key="`pl-${idx}-${line}`"
                class="music-hall__lrc-row"
                :class="{ 'is-active': idx === plainLrcActiveIndex }"
              >
                {{ line }}
              </p>
            </div>
          </div>
          <div v-else-if="showLrc && lrcUnsupported" class="music-hall__lrc music-hall__lrc--empty">
            歌词结构不支持（逐字歌词）
          </div>
          <div v-else-if="showLrc && lrcError" class="music-hall__lrc music-hall__lrc--empty">
            {{ lrcError }}
            <button type="button" class="music-hall__lrc-retry" @click="retryLrc">重试</button>
          </div>
          <div v-else-if="showLrc" class="music-hall__lrc music-hall__lrc--empty">暂无歌词</div>
        </div>

        <div class="music-hall__recent-card">
          <div class="music-hall__recent-title">最近播放</div>
          <div class="music-hall__section-panel music-hall__section-panel--recent">
            <div v-if="recentLoading" class="music-hall__recent-loading">加载中...</div>
            <div v-else-if="!recentTracks.length" class="music-hall__empty-state">
              <img :src="emptyRecentUrl" alt="">
              <p>暂无播放记录</p>
            </div>
            <div v-else class="music-hall__recent-list">
              <button
                v-for="track in recentTracks"
                :key="`r-${track.musicKey}`"
                type="button"
                class="music-hall__recent-item"
                :class="{ 'is-playing': isRecentPlaying(track) }"
                @click="selectTrack(track)"
              >
                <div class="music-hall__cover music-hall__cover--recent" :style="coverStyle(track)">
                  <img v-if="track.coverUrl" :src="track.coverUrl" alt="">
                  <el-icon v-else><Headset /></el-icon>
                </div>
                <span class="music-hall__recent-meta">
                  <span class="music-hall__recent-name">{{ track.title }}</span>
                  <span class="music-hall__recent-artist">{{ track.artist || '未知歌手' }}</span>
                </span>
                <span class="music-hall__recent-duration">{{ track.durationText || '--:--' }}</span>
                <span
                  v-if="isRecentPlaying(track)"
                  class="music-hall__recent-eq"
                  aria-hidden="true"
                >
                  <span
                    v-for="bar in RECENT_EQ_BARS"
                    :key="`eq-${track.musicKey}-${bar}`"
                    class="music-hall__recent-eq-bar"
                    :style="{ animationDelay: `${bar * 0.12}s` }"
                  />
                </span>
              </button>
            </div>
            <div class="music-hall__recent-pager">
              <AppPagination
                :current-page="recentPageNum"
                :total="recentPageTotal"
                :page-size="1"
                @current-change="onRecentPageChange"
              />
            </div>
          </div>
        </div>
      </aside>
    </div>
    <audio
      ref="audioRef"
      preload="metadata"
      @timeupdate="onTimeUpdate"
      @loadedmetadata="onMeta"
      @ended="onEnded"
      @error="onAudioError"
      @waiting="onAudioWaiting"
      @canplay="onAudioCanPlay"
    />

    <div v-if="showTagDialog" class="music-hall__lrc-mask" @click.self="closeTagDialog">
      <div class="music-hall__tag-dialog" role="dialog" aria-modal="true" aria-label="选择氛围标签">
        <div class="music-hall__tag-dialog-head">
          <span aria-hidden="true" />
          <div class="music-hall__lrc-dialog-title">选择标签</div>
          <button type="button" class="music-hall__tag-dialog-close" aria-label="关闭" @click="closeTagDialog">
            <el-icon><Close /></el-icon>
          </button>
        </div>
        <div class="music-hall__tag-dialog-search">
          <el-input
            v-model="tagDialogKeyword"
            placeholder="搜索标签，没有就创建一个"
            clearable
            maxlength="8"
            @input="onTagKeywordChange"
            @clear="onTagKeywordChange"
          />
          <button
            type="button"
            class="music-hall__tag-create"
            :disabled="tagCreating || !tagDialogKeyword.trim()"
            @click="onCreateTag"
          >
            {{ tagCreating ? '审核中...' : '创建' }}
          </button>
        </div>
        <div class="music-hall__tag-dialog-body">
          <div v-if="tagDialogLoading" class="music-hall__tag-dialog-state">加载中...</div>
          <div v-else-if="!tagDialogOptions.length" class="music-hall__tag-dialog-state">
            没有匹配的标签，可以直接创建
          </div>
          <div v-else class="music-hall__tag-dialog-grid">
            <button
              v-for="item in tagDialogOptions"
              :key="`mt-${item.name}`"
              type="button"
              class="music-hall__tag-chip"
              :class="{ 'is-active': songForm.tags?.includes(item.name) }"
              @click="toggleComposeTag(item.name)"
            >
              {{ item.name }}
              <i v-if="item.source === 'AI'" class="music-hall__tag-chip-badge">AI</i>
            </button>
          </div>
        </div>
        <div class="music-hall__tag-dialog-pager">
          <AppPagination
            :current-page="tagDialogPage"
            :total="tagDialogPageTotal"
            :page-size="1"
            size="small"
            :pager-count="5"
            :show-jumper="false"
            :hide-on-single-page="false"
            @current-change="onTagDialogPageChange"
          />
        </div>
        <div class="music-hall__tag-dialog-foot">
          <span>已选 {{ songForm.tags?.length || 0 }} / {{ MOOD_TAG_MAX_COUNT }}</span>
          <button type="button" class="music-hall__publish-btn" @click="closeTagDialog">完成</button>
        </div>
      </div>
    </div>

    <div v-if="showComposeLrc" class="music-hall__lrc-mask" @click.self="closeComposeLrc">
      <div class="music-hall__lrc-dialog" role="dialog" aria-modal="true" aria-label="歌词预览">
        <div class="music-hall__lrc-dialog-head">
          <div class="music-hall__lrc-dialog-title">歌词预览</div>
          <div v-if="composeLrcUnsupported" class="music-hall__lrc-dialog-warn">
            歌词结构不支持，请修改
          </div>
          <button type="button" class="music-hall__lrc-close" aria-label="关闭" @click="closeComposeLrc">
            <el-icon><Close /></el-icon>
          </button>
        </div>
        <div class="music-hall__lrc-dialog-body" :class="{ 'is-disabled': composeLrcUnsupported }">
          <textarea
            v-if="editingComposeLrc"
            v-model="songForm.lrcText"
            class="music-hall__lrc-editor"
            placeholder="可粘贴 LRC 或纯文本歌词"
          />
          <template v-else-if="composeLrcLines.length">
            <div
              v-for="(line, idx) in composeLrcLines"
              :key="`lrc-${idx}`"
              class="music-hall__lrc-line"
              :class="{ 'is-active': idx === 2 }"
            >
              <span>{{ line.time }}</span>
              <span>{{ line.text }}</span>
            </div>
          </template>
          <div v-else class="music-hall__empty-state music-hall__empty-state--lrc">
            <img :src="emptyLrcUrl" alt="">
            <p>暂无歌词</p>
            <span>可上传 lrc / txt 文件，或直接粘贴歌词文本</span>
          </div>
        </div>
        <div class="music-hall__lrc-dialog-foot">
          <button type="button" class="music-hall__draft-btn" @click="editingComposeLrc = !editingComposeLrc">
            {{ editingComposeLrc ? '预览歌词' : '编辑歌词' }}
          </button>
          <button type="button" class="music-hall__publish-btn" @click="closeComposeLrc">完成</button>
        </div>
      </div>
    </div>

    <div v-if="showTrimDialog" class="music-hall__trim-mask">
      <div class="music-hall__trim-dialog" role="dialog" aria-modal="true" aria-label="音频裁剪">
        <div class="music-hall__trim-head">
          <div class="music-hall__trim-title">裁剪音频</div>
          <div class="music-hall__trim-song" :title="trimSongTitle">{{ trimSongTitle }}</div>
        </div>
        <div v-if="trimLoading" class="music-hall__trim-loading">正在加载波形...</div>
        <template v-else>
          <div class="music-hall__trim-track" @click="onTrimTrackClick">
            <div class="music-hall__trim-wave-inner" aria-hidden="true">
              <span
                v-for="(h, idx) in trimWaveHeights"
                :key="`tw-${idx}`"
                class="music-hall__trim-bar"
                :style="{ height: `${Math.max(6, h * 0.85)}px` }"
              />
            </div>
            <span class="music-hall__trim-selection" :style="trimSelectionStyle" />
            <span class="music-hall__trim-playhead" :style="trimPlayheadStyle" />
            <button
              type="button"
              class="music-hall__trim-handle music-hall__trim-handle--start"
              :style="{ left: trimSelectionStyle.left }"
              aria-label="起点"
              @pointerdown="onTrimHandleDown('start', $event)"
            />
            <button
              type="button"
              class="music-hall__trim-handle music-hall__trim-handle--end"
              :style="{ left: `calc(${trimSelectionStyle.left} + ${trimSelectionStyle.width})` }"
              aria-label="终点"
              @pointerdown="onTrimHandleDown('end', $event)"
            />
          </div>
          <div class="music-hall__trim-times">
            <span>{{ formatTime(trimStartSec) }}</span>
            <span class="music-hall__trim-duration">选中 {{ formatTime(trimDurationSec) }}</span>
            <span>{{ formatTime(trimEndSec) }}</span>
          </div>
          <div class="music-hall__trim-controls">
            <button type="button" class="music-hall__ctrl music-hall__ctrl--main" @click="toggleTrimPlay">
              <el-icon v-if="trimPlaying" :size="22"><VideoPause /></el-icon>
              <el-icon v-else :size="22"><VideoPlay /></el-icon>
            </button>
          </div>
        </template>
        <div class="music-hall__trim-foot">
          <button type="button" class="music-hall__draft-btn" @click="closeTrimDialog">取消</button>
          <button type="button" class="music-hall__publish-btn" :disabled="trimLoading || trimApplying" @click="applyTrim">
            应用裁剪
          </button>
        </div>
        <audio ref="trimAudioRef" preload="auto" @timeupdate="onTrimTimeUpdate" @ended="trimPlaying = false" />
      </div>
    </div>
  </div>
</template>

<script setup src="./MusicHall.js"></script>
<style scoped lang="scss" src="./MusicHall.scss"></style>
