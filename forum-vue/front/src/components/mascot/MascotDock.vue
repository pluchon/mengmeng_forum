<template>
  <div
    class="mascot-root"
    :aria-label="uiLabels.ariaRoot"
  >
    <MascotSprite
      :state="spriteState"
      :x="spriteX"
      :paused="spritePaused"
      :tip-text="stageTipText"
      @activate="openAssistantFromSprite"
      @animation-complete="onSpriteAnimationComplete"
      @hover-change="onSpriteHoverChange"
      @ready="onSpriteReady"
    />

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
                <div class="mascot-fs-sessions-card">
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
                  <div class="mascot-fs-session-list">
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
                        <input
                          v-if="String(renamingSessionId) === String(sess.id)"
                          ref="renameInputRef"
                          v-model="renameDraft"
                          class="mascot-fs-session-item__rename-input"
                          maxlength="48"
                          aria-label="会话名称"
                          @click.stop
                          @keydown.enter.stop.prevent="commitRenameSession(sess)"
                          @keydown.esc.stop.prevent="cancelRenameSession"
                          @blur="commitRenameSession(sess)"
                        >
                        <span v-else class="mascot-fs-session-item__title">{{ sess.title || uiLabels.untitledSession }}</span>
                        <span class="mascot-fs-session-item__time">{{ formatSessionTime(sess.updateTime) }}</span>
                      </span>
                      <button
                        type="button"
                        class="mascot-fs-session-item__edit"
                        :aria-label="`${uiLabels.renameSession}：${sess.title || uiLabels.untitledSession}`"
                        :title="uiLabels.renameSession"
                        :disabled="Boolean(deletingSessionId) || Boolean(renamingSessionId)"
                        @click.stop="startRenameSession(sess)"
                      >
                        <el-icon><Edit /></el-icon>
                      </button>
                      <button
                        type="button"
                        class="mascot-fs-session-item__delete"
                        :aria-label="`${uiLabels.deleteSession}：${sess.title || uiLabels.untitledSession}`"
                        :title="uiLabels.deleteSession"
                        :disabled="Boolean(deletingSessionId) || Boolean(renamingSessionId)"
                        @click.stop="deleteSession(sess)"
                      >
                        <el-icon><Delete /></el-icon>
                      </button>
                    </div>
                  </div>
                </div>
                <section class="mascot-quota-card" aria-label="AI 配额">
                  <div v-for="row in quotaRows" :key="row.key" class="mascot-quota-card__row" :class="`is-${row.tone}`">
                    <div class="mascot-quota-card__line">
                      <span>{{ row.label }}</span>
                      <small>{{ row.text }}</small>
                    </div>
                    <div class="mascot-quota-card__track" aria-hidden="true">
                      <span :class="{ 'is-exhausted': row.exhausted }" :style="{ width: `${row.remainingPercent}%` }" />
                    </div>
                  </div>
                  <button
                    v-if="quotaExhausted"
                    type="button"
                    class="mascot-quota-card__points"
                    :class="{ 'is-active': usePointsBilling }"
                    @click="togglePointsPay"
                  >{{ usePointsBilling ? '已启用萌币支付' : '使用萌币继续' }}</button>
                </section>
              </aside>

              <div class="mascot-fs-main">
                <div class="mascot-fs-messages-pane">
                  <div v-if="!messages.length && !loading" class="mascot-fs-chat-empty">
                    <img :src="aiMessageEmptyUrl" alt="">
                    <p>{{ uiLabels.chatEmptyHint }}</p>
                  </div>
                  <el-scrollbar
                    v-else
                    ref="scrollbarFs"
                    class="mascot-messages mascot-messages--fs"
                  >
                    <div class="mascot-messages-inner">
                      <template
                        v-for="(m, i) in messages"
                        :key="'fs-' + i + '-' + (m.at || 0)"
                      >
                      <div v-if="shouldShowDateDivider(messages, i)" class="mascot-divider-label">{{ formatMessageDay(m.at) }}</div>
                      <div
                        class="mascot-msg-row"
                        :class="[m.role, { 'mascot-msg-row--context': m.type === 'context_summary' }]"
                      >
                      <template v-if="m.type === 'context_summary'">
                        <div class="mascot-context-marker"><span>———————</span><span>上下文已压缩</span><span>———————</span></div>
                      </template>
                      <template v-else>
                      <img
                        v-if="m.role === 'assistant'"
                        :src="companionAvatarSrc"
                        alt=""
                        class="mascot-msg-avatar mascot-msg-avatar--ai"
                      >
                      <UserAvatarVip
                        v-if="m.role === 'user'"
                        :size="28"
                        :src="userStore.avatarUrl || DEFAULT_AVATAR"                        class="mascot-msg-avatar-wrap"
                      />
                      <div class="mascot-msg-col">
                        <template v-if="m.type === 'image' && m.url">
                          <div class="mascot-img-wrap">
                            <img :src="m.url" alt="AI image" class="mascot-img">
                            <a class="mascot-img-link" :href="m.url" target="_blank" rel="noreferrer">{{ uiLabels.openImageInNewTab }}</a>
                          </div>
                          <div v-if="m.usageStats" class="mascot-bubble-meta mascot-bubble-meta--assistant">
                            <span class="mascot-bubble-stats">{{ formatAiUsageLine(m.usageStats) }}</span>
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
                                  v-html="renderMascotMarkdown(m.content, !!m.stripInlineImages)"
                                />
                                <span v-if="m.streaming" class="mascot-stream-cursor">▍</span>
                                <div v-if="m.role === 'assistant' && !m.streaming" class="mascot-bubble-actions mascot-bubble-actions--bottom-right">
                                  <button
                                    v-if="isLatestRegeneratableAssistant(i)"
                                    type="button"
                                    class="mascot-msg-regen"
                                    :disabled="loading"
                                    :title="uiLabels.regenerate"
                                    :aria-label="uiLabels.regenerate"
                                    @click="regenerateAssistant(i)"
                                  ><el-icon><Refresh /></el-icon></button>
                                  <button
                                    v-if="m.imageGallery?.length"
                                    type="button"
                                    class="mascot-msg-regen"
                                    title="查看图集"
                                    aria-label="查看图集"
                                    @click="openSearchGallery(m.imageGallery)"
                                  ><el-icon><Picture /></el-icon></button>
                                </div>
                              </template>
                              <template v-else>
                                {{ m.content }}<span v-if="m.streaming" class="mascot-stream-cursor">▍</span>
                              </template>
                            </div>
                          </div>
                          <div
                            v-if="m.role === 'assistant' && m.type !== 'image' && !m.streaming"
                            class="mascot-bubble-meta mascot-bubble-meta--assistant"
                          >
                            <span v-if="m.usageStats" class="mascot-bubble-stats">{{ formatAiUsageLine(m.usageStats) }}</span>
                          </div>
                          <div v-else-if="m.at && !m.streaming" class="mascot-bubble-meta">
                            <span v-if="m.usageStats" class="mascot-bubble-stats">{{ formatAiUsageLine(m.usageStats) }}</span>
                          </div>
                          <div
                            v-if="m.role === 'assistant' && activeAsk && activeAsk.message === m && !m.streaming"
                            class="mascot-ask-hint"
                          >
                            请在下方选择…
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
                      </template>
                      </div>
                      </template>
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

                <div v-if="activeAsk" class="mascot-ask-panel" role="group" aria-label="意图确认">
                  <div class="mascot-ask-panel__head">
                    <span class="mascot-ask-panel__badge">确认 {{ activeAsk.step }}/{{ activeAsk.total }}</span>
                    <div class="mascot-ask-panel__head-actions">
                      <button
                        v-if="!activeAsk.isFirst"
                        type="button"
                        class="mascot-ask-panel__back"
                        :disabled="activeAsk.submitting || loading"
                        @click="askGoBack"
                      >
                        上一个
                      </button>
                      <button
                        type="button"
                        class="mascot-ask-panel__close"
                        :disabled="activeAsk.submitting || loading"
                        title="关闭"
                        aria-label="关闭"
                        @click="dismissActiveAsk"
                      >×</button>
                    </div>
                  </div>
                  <div class="mascot-ask-panel__q">Q：{{ activeAsk.current.question }}</div>
                  <div class="mascot-ask-panel__opts">
                    <button
                      v-for="opt in activeAsk.current.options"
                      :key="opt.letter"
                      type="button"
                      class="mascot-ask-panel__opt"
                      :disabled="activeAsk.submitting || loading"
                      @click="pickAskOption(opt)"
                    >
                      <span class="mascot-ask-panel__letter">{{ opt.letter }}</span>
                      <span class="mascot-ask-panel__opt-text">{{ opt.label }}</span>
                    </button>
                  </div>
                  <div class="mascot-ask-panel__other">
                    <span class="mascot-ask-panel__other-label">都不是：</span>
                    <input
                      v-model="askWizard.customText"
                      class="mascot-ask-panel__other-input"
                      type="text"
                      maxlength="500"
                      placeholder="补充想法…"
                      :disabled="activeAsk.submitting || loading"
                      @keydown.enter.exact.prevent="submitAskCustom"
                    >
                    <button
                      type="button"
                      class="mascot-ask-panel__other-submit"
                      :disabled="activeAsk.submitting || loading || !askWizard.customText.trim()"
                      @click="submitAskCustom"
                    >
                      {{ activeAsk.isLast ? '完成' : '下一个' }}
                    </button>
                  </div>
                </div>

                <MascotChatInput
                  v-model="draft"
                  v-model:image-quality="imageQuality"
                  :image-options="imageModelOptions"
                  :loading="loading"
                  :image-generating="imageGenerating"
                  :disabled="contextCompressing"
                  :vip="isVip"
                  :placeholder="inputPlaceholder"
                  generation-hint="AI 也有可能出错，请自行甄别"
                  :show-points-pay-button="showPointsPayButton"
                  :points-pay-active="usePointsBilling"
                  :context-used-tokens="contextWindow.usedTokens"
                  :context-max-tokens="contextWindow.maxTokens"
                  :context-available="contextWindow.canCompress"
                  :context-compressing="contextCompressing"
                  @send="send"
                  @toggle-points-pay="togglePointsPay"
                  @compress-context="compressContext"
                  @open-memory="openMemoryDialog"
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
      <MascotSearchGalleryDialog
        v-model:visible="searchGalleryVisible"
        :items="searchGalleryItems"
      />
      <TopTitleDialog
        v-model="memoryDialogVisible"
        title="长期记忆"
        width="min(520px, 94vw)"
        confirm-text="更新记忆"
        :confirm-disabled="!memoryEditDraft.trim() || memoryEditDraft.trim().length > MEMORY_EDIT_MAX"
        :loading="memorySaving"
        @confirm="submitMemoryEdit"
      >
        <div class="mascot-memory-dialog">
          <div class="mascot-memory-dialog__section">
            <div class="mascot-memory-dialog__label">
              <span>摘要</span>
              <em>{{ displayMemorySummary.length }}/{{ MEMORY_SUMMARY_MAX }}</em>
            </div>
            <p class="mascot-memory-dialog__summary">{{ displayMemorySummary || '还没有记下什么' }}</p>
          </div>
          <div class="mascot-memory-dialog__section">
            <div class="mascot-memory-dialog__label">
              <span>事实</span>
              <em>{{ displayMemoryFacts.length }}/{{ MEMORY_FACTS_MAX }}</em>
            </div>
            <ul v-if="displayMemoryFacts.length" class="mascot-memory-dialog__facts">
              <li v-for="(fact, idx) in displayMemoryFacts" :key="idx">{{ fact }}</li>
            </ul>
            <p v-else class="mascot-memory-dialog__empty">还没有记下具体事实，稳定偏好会在对话中慢慢积累。</p>
          </div>
          <div class="mascot-memory-dialog__section">
            <div class="mascot-memory-dialog__label">
              <span>修改</span>
              <em>{{ memoryEditDraft.length }}/{{ MEMORY_EDIT_MAX }}</em>
            </div>
            <textarea
              v-model="memoryEditDraft"
              class="mascot-memory-dialog__input"
              :maxlength="MEMORY_EDIT_MAX"
              placeholder="比如：记住我平时更喜欢轻松一点的语气，也常看音乐和动画相关帖子。"
            />
          </div>
        </div>
      </TopTitleDialog>
  </div>
</template>

<script setup>
import { Delete, Edit, Picture, Plus, Refresh } from '@element-plus/icons-vue'
import aiMessageEmptyUrl from '@/assets/images/ai_message_empty.png'
import MascotChatInput from '@/components/mascot/MascotChatInput.vue'
import TopTitleDialog from '@/components/dialog/TopTitleDialog.vue'
import MascotRelatedArticlesDialog from '@/components/mascot/MascotRelatedArticlesDialog.vue'
import MascotSearchGalleryDialog from '@/components/mascot/MascotSearchGalleryDialog.vue'
import MascotSprite from '@/components/mascot/MascotSprite.vue'
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import { DEFAULT_AVATAR } from '@/utils/constants'
import { useMascotDock } from '@scripts/components/mascot/MascotDock'

const {
  acceptRelatedSearchOffer,
  activeAsk,
  askGoBack,
  askWizard,
  assistantOpen,
  companionAvatarSrc,
      draft,
      deleteSession,
      deletingSessionId,
  cancelRenameSession,
  commitRenameSession,
  quotaRows,
  quotaExhausted,
  renameDraft,
  renameInputRef,
  renameSubmitting,
  renamingSessionId,
  dismissActiveAsk,
  dismissRelatedSearchOffer,
  pickAskOption,
  submitAskCustom,
  showPointsPayButton,
  togglePointsPay,
  usePointsBilling,
  formatAiUsageLine,
  formatMessageDay,
  formatSessionTime,
  shouldShowDateDivider,
  isLatestRegeneratableAssistant,
  imageModelOptions,
  imageQuality,
  imageGenerating,
  inputPlaceholder,
  isVip,
  loading,
  memoryDialogVisible,
  memoryEditDraft,
  memoryFacts,
  memorySaving,
  memorySummary,
  MEMORY_EDIT_MAX,
  MEMORY_FACTS_MAX,
  MEMORY_SUMMARY_MAX,
  displayMemoryFacts,
  displayMemorySummary,
  messages,
  onAssistantOpened,
  onSpriteAnimationComplete,
  onSpriteHoverChange,
  onSpriteReady,
  openAssistantFromSprite,
  openMemoryDialog,
  openRelatedArticle,
  openRelatedRecommendation,
  openSearchGallery,
  regenerateAssistant,
  renderMascotMarkdown,
  relatedDialogItems,
  relatedDialogVisible,
  searchGalleryItems,
  searchGalleryVisible,
  scrollbarFs,
  selectLocalSession,
  send,
  sessionId,
  sessionListForNav,
  stageTipText,
  spritePaused,
  spriteState,
  spriteX,
  startRenameSession,
  startNewSession,
  submitMemoryEdit,
  uiLabels,
  userStore,
  contextCompressing,
  contextWindow,
  compressContext,
} = useMascotDock()
</script>

<style scoped src="@/assets/styles/mascot-dock.css"></style>
<style src="@/assets/styles/mascot-dock-global.css"></style>
