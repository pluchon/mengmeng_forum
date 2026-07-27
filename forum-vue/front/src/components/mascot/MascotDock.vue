<template>
  <div
    class="mascot-root"
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
                <div class="mascot-dlg-head__brand">
                  <span>{{ uiLabels.brandTitle }}</span>
                  <el-dropdown
                    v-if="catalog.length > 1"
                    trigger="click"
                    @command="switchMascot"
                  >
                    <button
                      type="button"
                      class="mascot-dlg-head__switch"
                      title="切换形象"
                      aria-label="切换形象"
                    >
                      <el-icon><Refresh /></el-icon>
                    </button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item
                          v-for="mascot in catalog"
                          :key="mascot.code"
                          :command="mascot.code"
                          :disabled="mascot.code === activeCode"
                        >
                          {{ mascot.name || mascot.code }}
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
                <div class="mascot-dlg-head__status">
                  <span class="mascot-dlg-head__dot" aria-hidden="true" />
                  <span>{{ userStore.isLoggedIn ? uiLabels.statusOnline : uiLabels.statusOffline }}</span>
                </div>
              </div>
            </div>
          </div>
        </template>

        <div class="mascot-fs-layout">
          <div class="mascot-fs-body">
              <aside class="mascot-fs-sidebar">
                <div class="mascot-fs-sidebar__header">
                  <div class="mascot-fs-sidebar__title">{{ uiLabels.sessionListTitle }}</div>
                  <button
                    type="button"
                    class="mascot-new-session-icon"
                    :title="uiLabels.newSession"
                    :aria-label="uiLabels.newSession"
                    @click="startNewSession"
                  >
                    <el-icon><Plus /></el-icon>
                  </button>
                </div>
                <p v-if="!sessionListForNav.length" class="mascot-fs-sidebar__empty">
                  {{ uiLabels.sessionEmpty }}
                </p>
                <div
                  v-for="sess in sessionListForNav"
                  :key="sess.id"
                  class="mascot-fs-session-item"
                  :class="{ 'is-active': String(sess.id) === String(sessionId) }"
                  role="button"
                  tabindex="0"
                  @click="selectLocalSession(sess.id)"
                  @keydown.enter.prevent="selectLocalSession(sess.id)"
                  @keydown.space.prevent="selectLocalSession(sess.id)"
                >
                  <span class="mascot-fs-session-item__copy">
                    <span class="mascot-fs-session-item__title">{{ sess.title || uiLabels.untitledSession }}</span>
                    <span class="mascot-fs-session-item__time">{{ formatSessionTime(sess.updateTime) }}</span>
                  </span>
                  <button
                    type="button"
                    class="mascot-fs-session-item__delete"
                    :aria-label="`${uiLabels.deleteSession}：${sess.title || uiLabels.untitledSession}`"
                    :title="uiLabels.deleteSession"
                    :disabled="Boolean(deletingSessionId)"
                    @click.stop="deleteSession(sess)"
                  >
                    <el-icon><Delete /></el-icon>
                  </button>
                </div>
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
                        <template v-else-if="m.type === 'related-result'">
                          <button
                            type="button"
                            class="mascot-related-result-bubble"
                            @click="openRelatedRecommendation(m.relatedItems)"
                          >
                            {{ m.content }}
                          </button>
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
                            v-if="m.role === 'assistant' && m.relatedSearchOffer && !m.streaming"
                            class="mascot-related-offer"
                          >
                            <span>要不要我帮你看看部落里有没有人聊过？</span>
                            <div class="mascot-related-offer__actions">
                              <button
                                type="button"
                                :disabled="m.relatedSearchOffer.loading"
                                @click="acceptRelatedSearchOffer(m)"
                              >
                                看看
                              </button>
                              <button
                                type="button"
                                :disabled="m.relatedSearchOffer.loading"
                                @click="dismissRelatedSearchOffer(m)"
                              >
                                不用
                              </button>
                            </div>
                          </div>
                        </template>
                      </div>
                    </div>
                      <div v-if="imageGenerating" class="mascot-msg-row assistant">
                        <img :src="companionAvatarSrc" alt="" class="mascot-msg-avatar mascot-msg-avatar--ai">
                        <div class="mascot-image-generating" role="status">
                          <span class="mascot-image-generating__spark" />
                          <span>正在绘制画面</span>
                        </div>
                      </div>
                      <div
                        v-else-if="loading && !(messages.length && messages[messages.length - 1]?.streaming)"
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
                  v-model:image-quality="imageQuality"
                  :image-options="imageModelOptions"
                  :loading="loading"
                  :image-generating="imageGenerating"
                  :vip="isVip"
                  :placeholder="inputPlaceholder"
                  generation-hint="AI 生成内容仅供参考，请结合自己的想法修改"
                  :show-points-pay-button="showPointsPayButton"
                  :points-pay-active="usePointsBilling"
                  @send="send"
                  @toggle-points-pay="togglePointsPay"
                />
              </div>
          </div>
        </div>
      </el-dialog>
      <MascotRelatedArticlesDialog
        v-model:visible="relatedDialogVisible"
        :items="relatedDialogItems"
        @open-article="openRelatedArticle"
      />
    </div>
  </div>
</template>

<script setup>
import { Delete, Plus, Refresh, ZoomIn } from '@element-plus/icons-vue'
import MascotChatInput from '@/components/mascot/MascotChatInput.vue'
import MascotRelatedArticlesDialog from '@/components/mascot/MascotRelatedArticlesDialog.vue'
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import { DEFAULT_AVATAR } from '@/utils/constants'
import { useMascotDock } from '@scripts/components/mascot/MascotDock'

const {
  activeCode,
  acceptRelatedSearchOffer,
  assistantOpen,
  catalog,
  companionAvatarSrc,
      draft,
      deleteSession,
      deletingSessionId,
  dismissRelatedSearchOffer,
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
  imageGenerating,
  inputPlaceholder,
  isVip,
  loading,
  messages,
  onAssistantOpened,
  onScaleSliderChange,
  onStageLeave,
  onStagePointerDown,
  openRelatedArticle,
  openRelatedRecommendation,
  regenerateAssistant,
  renderMascotMarkdown,
  ringVipTier,
  rootStyle,
  relatedDialogItems,
  relatedDialogVisible,
  scalePopoverOpen,
  scrollbarFs,
  selectLocalSession,
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
  switchMascot,
  uiLabels,
  userStore,
} = useMascotDock()
</script>

<style scoped src="@/assets/styles/mascot-dock.css"></style>
<style src="@/assets/styles/mascot-dock-global.css"></style>
