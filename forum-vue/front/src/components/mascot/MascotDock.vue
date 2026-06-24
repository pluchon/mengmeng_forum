<template>
  <div
    class="mascot-root"
    :class="{ 'mascot-root--pass-through': mascotUi.pointerPassThrough }"
    :aria-label="uiLabels.ariaRoot"
    :style="rootStyle"
  >
    <div class="mascot-stack">
      <div
        class="mascot-stage-wrap"
        :style="stageWrapStyle"
        @pointerenter="stageHovered = true"
        @pointerleave="onStageLeave"
      >
        <div
          ref="stageHost"
          class="mascot-stage-host"
          :style="stageHostStyle"
          @pointerdown="onStagePointerDown"
        />
        <div
          v-if="stageTipText"
          class="mascot-cloud-tip"
          role="status"
          aria-live="polite"
        >
          <span class="mascot-cloud-tip__text">{{ stageTipText }}</span>
        </div>
        <img
          v-if="stageUseFallback"
          :src="companionAvatarSrc"
          class="mascot-stage-fallback"
          alt=""
          draggable="false"
          @pointerdown="onStagePointerDown"
        >
        <el-popover
          trigger="click"
          placement="top-end"
          :width="240"
          :teleported="true"
          popper-class="mascot-scale-popper"
          @show="scalePopoverOpen = true"
          @hide="scalePopoverOpen = false"
        >
          <template #reference>
            <button
              type="button"
              class="mascot-scale-btn"
              :class="{ 'mascot-scale-btn--dim': !(stageHovered || scalePopoverOpen) }"
              :title="uiLabels.scaleTitle"
              :aria-label="uiLabels.scaleTitle"
              @pointerdown.stop
              @click.stop
            >
              <el-icon><ZoomIn /></el-icon>
            </button>
          </template>
          <div class="mascot-scale-pop">
            <div class="mascot-scale-pop__hint">{{ uiLabels.scaleHint }}</div>
            <el-slider
              v-model="stageScale"
              :min="0.35"
              :max="1.45"
              :step="0.01"
              show-tooltip
              @change="onScaleSliderChange"
            />
          </div>
        </el-popover>
      </div>

      <el-dialog
        v-model="assistantOpen"
        append-to-body
        :destroy-on-close="false"
        class="mascot-fs-dialog mascot-fs-dialog--wide"
        width="min(1080px, 94vw)"
        top="4vh"
        :close-on-click-modal="true"
        @opened="onAssistantOpened"
      >
        <template #header>
          <div class="mascot-dlg-head">
            <div class="mascot-dlg-head__left">
              <img :src="companionAvatarSrc" alt="" class="mascot-dlg-companion-avatar">
              <div class="mascot-dlg-head__titles">
                <div class="mascot-dlg-head__brand">{{ uiLabels.brandTitle }}</div>
                <div class="mascot-dlg-head__status">
                  <span class="mascot-dlg-head__dot" aria-hidden="true" />
                  <span>{{ userStore.isLoggedIn ? uiLabels.statusOnline : uiLabels.statusOffline }}</span>
                </div>
              </div>
            </div>
            <div class="mascot-dlg-head__right">
              <template v-if="userStore.isLoggedIn">
                <UserAvatarVip
                  :size="36"
                  :src="userStore.avatarUrl || DEFAULT_AVATAR"
                  :vip-tier="ringVipTier"
                  :vip-expire-at="userStore.vipExpireAt"
                />
                <span class="mascot-dlg-head__nickname">{{ userStore.nickname || uiLabels.defaultNickname }}</span>
              </template>
              <template v-else>
                <span class="mascot-dlg-head__guest">{{ uiLabels.guest }}</span>
              </template>
            </div>
          </div>
        </template>

        <div class="mascot-fs-layout">
          <div class="mascot-mode-tabs-row">
            <div class="mascot-mode-tabs" role="tablist">
              <button
                v-for="tab in modeTabs"
                :key="tab.id"
                type="button"
                class="mascot-mode-tab"
                :class="{ 'mascot-mode-tab--on': activeNav === tab.id, 'mascot-mode-tab--ghost': tab.id === 'appearance' }"
                role="tab"
                :aria-selected="activeNav === tab.id"
                @click="selectNav(tab.id)"
              >
                <el-icon><component :is="tab.icon" /></el-icon>
                <span>{{ tab.label }}</span>
              </button>
            </div>
            <button
              v-if="activeNav !== 'appearance'"
              type="button"
              class="mascot-new-session-icon"
              :title="uiLabels.newSession"
              :aria-label="uiLabels.newSession"
              @click="startNewSession"
            >
              <el-icon><Plus /></el-icon>
            </button>
          </div>

          <div v-if="activeNav === 'appearance'" class="mascot-appearance">
            <p v-if="!catalog.length" class="mascot-picker-empty">{{ uiLabels.appearanceEmpty }}</p>
            <div v-else class="mascot-appearance__panel">
              <div class="mascot-appearance__grid">
                <button
                  v-for="m in catalog"
                  :key="m.code"
                  type="button"
                  class="mascot-appearance__item"
                  :class="{ 'is-active': pendingCode === m.code }"
                  @click="onPreviewPick(m.code)"
                >
                  {{ m.name || m.code }}
                </button>
              </div>
              <button
                type="button"
                class="mascot-appearance__apply"
                :disabled="!catalog.length || !pendingCode"
                @click="applyAppearance"
              >
                {{ uiLabels.applyAppearance }}
              </button>
            </div>
          </div>

          <template v-else>
            <div class="mascot-fs-body">
              <aside class="mascot-fs-sidebar">
                <div class="mascot-fs-sidebar__title">{{ uiLabels.sessionListTitle }}</div>
                <p v-if="!sessionListForNav.length" class="mascot-fs-sidebar__empty">
                  {{ uiLabels.sessionEmpty }}
                </p>
                <button
                  v-for="sess in sessionListForNav"
                  :key="sess.id"
                  type="button"
                  class="mascot-fs-session-item"
                  :class="{ 'is-active': String(sess.id) === String(sessionId) }"
                  @click="selectLocalSession(sess.id)"
                >
                  <span class="mascot-fs-session-item__title">{{ sess.title || uiLabels.untitledSession }}</span>
                  <span class="mascot-fs-session-item__time">{{ formatSessionTime(sess.updateTime) }}</span>
                </button>
              </aside>

              <div class="mascot-fs-main">
                <div class="mascot-fs-messages-pane">
                  <el-scrollbar
                    ref="scrollbarFs"
                    class="mascot-messages mascot-messages--fs"
                    always
                  >
                    <div class="mascot-messages-inner">
                      <p
                        v-if="!messages.length && !loading"
                        class="mascot-fs-chat-empty"
                      >
                        {{ uiLabels.chatEmptyHint }}
                      </p>
                      <div v-if="messages.length" class="mascot-divider-label">{{ uiLabels.today }}</div>
                      <div
                        v-for="(m, i) in messages"
                        :key="'fs-' + i + '-' + (m.at || 0)"
                        class="mascot-msg-row"
                        :class="m.role"
                      >
                      <img
                        v-if="m.role === 'assistant'"
                        :src="companionAvatarSrc"
                        alt=""
                        class="mascot-msg-avatar mascot-msg-avatar--ai"
                      >
                      <UserAvatarVip
                        v-if="m.role === 'user'"
                        :size="28"
                        :src="userStore.avatarUrl || DEFAULT_AVATAR"
                        :vip-tier="ringVipTier"
                        :vip-expire-at="userStore.vipExpireAt"
                        class="mascot-msg-avatar-wrap"
                      />
                      <div class="mascot-msg-col">
                        <template v-if="m.type === 'image' && m.url">
                          <div class="mascot-img-wrap">
                            <img :src="m.url" alt="AI image" class="mascot-img">
                            <a class="mascot-img-link" :href="m.url" target="_blank" rel="noreferrer">{{ uiLabels.openImageInNewTab }}</a>
                          </div>
                        </template>
                        <template v-else>
                          <div class="mascot-bubble-wrap">
                            <div
                              class="mascot-bubble"
                              :class="m.role === 'user' ? 'mascot-bubble--user' : 'mascot-bubble--ai'"
                            >
                              <span
                                v-if="m.streaming && !m.content && m.thinkingText"
                                class="mascot-thinking-text"
                              >{{ m.thinkingText }}</span>
                              <template v-else-if="m.role === 'assistant'">
                                <div
                                  class="mascot-md"
                                  v-html="renderMascotMarkdown(m.content, !!(m.searchImageUrl || m.stripInlineImages))"
                                />
                                <span v-if="m.streaming" class="mascot-stream-cursor">▍</span>
                              </template>
                              <template v-else>
                                {{ m.content }}<span v-if="m.streaming" class="mascot-stream-cursor">▍</span>
                              </template>
                            </div>
                          </div>
                          <div
                            v-if="m.searchImageUrl && !m.streaming"
                            class="mascot-search-image-card"
                          >
                            <img
                              :src="m.searchImageUrl"
                              alt="搜索配图"
                              class="mascot-search-image"
                              loading="lazy"
                              @error="hideMascotSearchImage(m)"
                            >
                          </div>
                          <div
                            v-if="m.role === 'assistant' && m.type !== 'image' && !m.streaming"
                            class="mascot-bubble-meta mascot-bubble-meta--assistant"
                          >
                            <span v-if="m.usageStats" class="mascot-bubble-stats">{{ formatAiUsageLine(m.usageStats) }}</span>
                            <span v-if="m.at" class="mascot-bubble-time">{{ formatMsgTime(m.at) }}</span>
                            <button
                              v-if="isLatestRegeneratableAssistant(i)"
                              type="button"
                              class="mascot-msg-regen"
                              :disabled="loading"
                              :title="uiLabels.regenerate"
                              :aria-label="uiLabels.regenerate"
                              @click="regenerateAssistant(i)"
                            >
                              <el-icon><Refresh /></el-icon>
                            </button>
                          </div>
                          <div v-else-if="m.at && !m.streaming" class="mascot-bubble-meta">
                            <span v-if="m.usageStats" class="mascot-bubble-stats">{{ formatAiUsageLine(m.usageStats) }}</span>
                            <span class="mascot-bubble-time">{{ formatMsgTime(m.at) }}</span>
                          </div>
                          <div
                            v-if="m.role === 'assistant' && m.relatedArticles?.length && !m.streaming"
                            class="mascot-related-block"
                          >
                            <div class="mascot-related-label">相关帖子</div>
                            <div class="mascot-related-links">
                              <router-link
                                v-for="art in m.relatedArticles"
                                :key="'rel-' + art.articleId"
                                :to="{ name: 'articleDetail', params: { id: art.articleId } }"
                                class="mascot-related-link"
                              >
                                {{ art.title }}
                              </router-link>
                            </div>
                          </div>
                        </template>
                      </div>
                    </div>
                      <div
                        v-if="loading && !(messages.length && messages[messages.length - 1]?.streaming)"
                        class="mascot-msg-row assistant"
                      >
                        <img :src="companionAvatarSrc" alt="" class="mascot-msg-avatar mascot-msg-avatar--ai">
                        <div class="mascot-typing" :aria-label="uiLabels.typing">
                          <span /><span /><span />
                        </div>
                      </div>
                    </div>
                  </el-scrollbar>
                </div>

                <MascotChatInput
                  v-model="draft"
                  v-model:llm="selectedLlm"
                  v-model:image-quality="imageQuality"
                  :options="llmOptions"
                  :image-options="imageModelOptions"
                  :mode="activeNav"
                  :loading="loading"
                  :vip="isVip"
                  :placeholder="inputPlaceholder"
                  :estimate-hint="estimateHintText"
                  :estimate-loading="estimateLoading"
                  :show-points-pay-button="showPointsPayButton"
                  :points-pay-active="usePointsBilling"
                  @send="send"
                  @toggle-points-pay="togglePointsPay"
                />
              </div>
            </div>
          </template>
        </div>
      </el-dialog>
    </div>
  </div>
</template>

<script setup>
import { EditPen, Picture, Plus, QuestionFilled, Refresh, UserFilled, ZoomIn } from '@element-plus/icons-vue'
import MascotChatInput from '@/components/mascot/MascotChatInput.vue'
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import { DEFAULT_AVATAR } from '@/utils/constants'
import { useMascotDock } from '@scripts/components/mascot/MascotDock'

const {
  activeNav,
  applyAppearance,
  assistantOpen,
  catalog,
  companionAvatarSrc,
  draft,
  estimateHintText,
  estimateLoading,
  showPointsPayButton,
  togglePointsPay,
  usePointsBilling,
  formatAiUsageLine,
  formatMsgTime,
  formatSessionTime,
  hideMascotSearchImage,
  isLatestRegeneratableAssistant,
  imageModelOptions,
  imageQuality,
  inputPlaceholder,
  isVip,
  llmOptions,
  loading,
  mascotUi,
  messages,
  modeTabs,
  onAssistantOpened,
  onPreviewPick,
  onScaleSliderChange,
  onStageLeave,
  onStagePointerDown,
  pendingCode,
  regenerateAssistant,
  renderMascotMarkdown,
  ringVipTier,
  rootStyle,
  scalePopoverOpen,
  scrollbarFs,
  selectLocalSession,
  selectNav,
  selectedLlm,
  send,
  sessionId,
  sessionListForNav,
  stageHost,
  stageHostStyle,
  stageHovered,
  stageScale,
  stageTipText,
  stageUseFallback,
  stageWrapStyle,
  startNewSession,
  uiLabels,
  userStore,
} = useMascotDock()
</script>

<style scoped src="@/assets/styles/mascot-dock.css"></style>
<style src="@/assets/styles/mascot-dock-global.css"></style>
