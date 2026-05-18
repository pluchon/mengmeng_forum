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
          @pointerdown="onStagePointerDown"
        />
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
              :min="0.55"
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
        class="mascot-fs-dialog"
        width="min(720px, 96vw)"
        top="6vh"
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
                  <span>{{ uiLabels.statusOnline }}</span>
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
                <div class="mascot-dlg-head__user-block">
                  <span class="mascot-dlg-head__nickname">{{ userStore.nickname || uiLabels.defaultNickname }}</span>
                  <button v-if="activeNav !== 'appearance'" type="button" class="mascot-history-btn" @click="openHistoryDrawer">{{ uiLabels.historyBtn }}</button>
                </div>
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
              <el-button
                v-if="activeNav !== 'appearance'"
                type="primary"
                size="small"
                plain
                class="mascot-new-session-btn"
                @click="startNewSession"
              >
                新建会话
              </el-button>
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
                  {{ uiLabels.applyAppearance }}</button>
              </div>
            </div>

            <template v-else>
              <div class="mascot-fs-chat">
              <div class="mascot-fs-blank">
                <el-scrollbar
                  ref="scrollbarFs"
                  class="mascot-messages mascot-messages--fs"
                >
                  <div v-if="messages.length" class="mascot-divider-label">{{ uiLabels.today }}</div>
                  <div
                    v-for="(m, i) in messages"
                    :key="'fs-' + i"
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
                        <div class="mascot-bubble" :class="m.role === 'user' ? 'mascot-bubble--user' : 'mascot-bubble--ai'">
                          {{ m.content }}
                        </div>
                        <div v-if="m.at" class="mascot-bubble-time">{{ formatMsgTime(m.at) }}</div>
                      </template>
                    </div>
                  </div>
                  <div v-if="loading" class="mascot-msg-row assistant">
                    <img :src="companionAvatarSrc" alt="" class="mascot-msg-avatar mascot-msg-avatar--ai">
                    <div class="mascot-typing" :aria-label="uiLabels.typing">
                      <span /><span /><span />
                    </div>
                  </div>
                </el-scrollbar>
              </div>

              <div v-if="quickChips.length" class="mascot-quick-chips" role="group" :aria-label="uiLabels.quickChipsGroup">
                <button
                  v-for="chip in quickChips"
                  :key="chip.label"
                  type="button"
                  class="mascot-chip"
                  @click="applyChip(chip)"
                >
                  <el-icon v-if="chip.icon" class="mascot-chip__icon"><component :is="chip.icon" /></el-icon>
                  <span>{{ chip.label }}</span>
                </button>
              </div>

              <MascotChatInput
                v-model="draft"
                v-model:llm="selectedLlm"
                v-model:image-quality="imageQuality"
                :options="llmOptions"
                :mode="activeNav"
                :loading="loading"
                :disabled="activeNav === 'reading'"
                :vip="isVip"
                :show-model-picker="activeNav !== 'reading'"
                :placeholder="inputPlaceholder"
                :estimate-points="estimatePoints"
                :estimate-loading="estimateLoading"
                @send="send"
                @clear="clearMessages"
              />
              </div>
            </template>
        </div>
      </el-dialog>

      <el-drawer
        v-model="historyDrawerOpen"
        :title="historyDrawerTitle"
        direction="rtl"
        size="min(360px, 92vw)"
        append-to-body
      >
        <div v-loading="historyLoading" class="mascot-history-drawer">
          <p v-if="!historySessions.length" class="mascot-history-empty">{{ uiLabels.historyEmpty }}</p>
          <button
            v-for="sess in historySessions"
            :key="sess.id"
            type="button"
            class="mascot-history-item"
            :class="{ 'is-active': String(sess.id) === String(sessionId) }"
            @click="loadHistorySession(sess.id)"
          >
            <span class="mascot-history-item__title">{{ sess.title || uiLabels.untitledSession }}</span>
            <span class="mascot-history-item__time">{{ formatSessionTime(sess.updateTime) }}</span>
          </button>
        </div>
      </el-drawer>
    </div>
  </div>
</template>

<script setup>
import {
  Avatar,
  Brush,
  CopyDocument,
  EditPen,
  List,
  MagicStick,
  Picture,
  QuestionFilled,
  Reading,
  Refresh,
  Sunny,
  UserFilled,
  ZoomIn,
} from '@element-plus/icons-vue'
import MascotChatInput from '@/components/mascot/MascotChatInput.vue'
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import { DEFAULT_AVATAR } from '@/utils/constants'
import { useMascotDock } from '@scripts/components/mascot/MascotDock'

const {
  ALL_LLM_OPTIONS,
  FLASH_LLM,
  GUEST_MASCOT_CODE_KEY,
  IMAGE_QUALITY_KEY,
  LLM_HELP_KEY,
  LLM_WRITING_KEY,
  OFFSET_KEY,
  SCALE_KEY,
  STAGE_BASE_H,
  STAGE_BASE_W,
  activeCode,
  activeNav,
  activeSkill,
  applyAppearance,
  applyChip,
  applyStageScaleToLib,
  assistantOpen,
  buildModelsPayload,
  catalog,
  clearMessages,
  startNewSession,
  companionAvatarSrc,
  currentLlmStorageKey,
  draft,
  dragOffset,
  ensureSessionId,
  estimateLoading,
  estimatePoints,
  fetchCatalog,
  formatMsgTime,
  formatSessionTime,
  getSessionForNav,
  historyDrawerOpen,
  historyDrawerTitle,
  historyLoading,
  historySessions,
  imageQuality,
  initOml2dStage,
  inputPlaceholder,
  isVip,
  live2dAssetUrl,
  llmOptions,
  llmStorageKey,
  loadHistorySession,
  loadMessagesForNav,
  loadSavedOffset,
  loading,
  mapVoToMessages,
  mascotUi,
  messages,
  modeTabs,
  navToSkill,
  oml2d,
  onAssistantOpened,
  onPreviewPick,
  onScaleSliderChange,
  onSkillForSend,
  onStageLeave,
  onStagePointerDown,
  onStagePointerMove,
  onStagePointerUp,
  openHistoryDrawer,
  pendingCode,
  pointsWallet,
  quickChips,
  refreshEstimate,
  resolveInitialCode,
  ringVipTier,
  rootStyle,
  saveLlmPrefs,
  saveOffset,
  saveScale,
  scalePopoverOpen,
  scrollFsToBottom,
  scrollbarFs,
  selectNav,
  selectedLlm,
  selectedLlmHelp,
  selectedLlmWriting,
  send,
  sessionId,
  sessionKeyForNav,
  setSessionForNav,
  skillSessionIds,
  stageHost,
  stageHovered,
  stageScale,
  stageUseFallback,
  stageWrapStyle,
  uiLabels,
  userStore,
} = useMascotDock()
</script>

<style scoped src="@/assets/styles/mascot-dock.css"></style>
<style src="@/assets/styles/mascot-dock-global.css"></style>
