<template>
  <el-dialog
    v-model="dialogVisible"
    class="mc-dialog"
    :class="{ 'mc-dialog--vip': viewerIsVip }"
    width="75vw"
    align-center
    append-to-body
    :show-close="false"
    destroy-on-close
    @opened="onDialogOpened"
    @closed="handleClose"
  >
    <button type="button" class="mc-dialog-close" aria-label="关闭" @click="handleClose">
      <el-icon><Close /></el-icon>
    </button>

    <div class="mc" @focusout="onDialogBlurRoot">
      <aside class="mc-left">
        <div class="mc-left-top">
          <div class="mc-left-title">消息中心</div>
          <div class="mc-tabs" role="tablist">
            <button
              type="button"
              class="mc-tab"
              :class="{ 'is-on': activeTab === 'all' }"
              @click="activeTab = 'all'"
            >
              全部
              <span v-if="tabBadges.all > 0" class="mc-tab-badge">{{ tabBadges.all > 99 ? '99+' : tabBadges.all }}</span>
            </button>
            <button
              type="button"
              class="mc-tab"
              :class="{ 'is-on': activeTab === 'pm' }"
              @click="activeTab = 'pm'"
            >
              私信
              <span v-if="tabBadges.pm > 0" class="mc-tab-badge">{{ tabBadges.pm > 99 ? '99+' : tabBadges.pm }}</span>
            </button>
            <button
              type="button"
              class="mc-tab"
              :class="{ 'is-on': activeTab === 'notif' }"
              @click="activeTab = 'notif'"
            >
              通知
              <span v-if="tabBadges.notif > 0" class="mc-tab-badge">{{ tabBadges.notif > 99 ? '99+' : tabBadges.notif }}</span>
            </button>
          </div>
          <div class="mc-search">
            <el-icon class="mc-search-icon"><Search /></el-icon>
            <input
              v-model="searchQuery"
              type="search"
              class="mc-search-input"
              placeholder="搜索消息…"
            >
          </div>
        </div>

        <el-scrollbar class="mc-list-scroll">
          <button
            v-for="item in listItems"
            :key="item.key"
            type="button"
            class="mc-conv"
            :class="{
              'is-on': isActiveItem(item),
              'is-focus-ring': focusedConvKey === item.key,
            }"
            @click="selectListItem(item)"
            @focus="onConvFocus(item)"
            @blur="onConvBlur(item)"
          >
            <div class="mc-conv-ava">
              <template v-if="item.kind === 'pm'">
                <UserAvatarVip
                  :size="38"
                  :src="item.user?.avatarUrl || defaultAvatar"
                  :vip-tier="Number(item.user?.vipTier) || 0"
                  :vip-expire-at="item.user?.vipExpireAt"
                />
              </template>
              <div v-else class="mc-sys-ava mc-sys-ava--group">
                <el-icon :size="18"><component :is="item.listIcon" /></el-icon>
              </div>
              <span v-if="item.unread > 0" class="mc-unread-dot" />
            </div>
            <div class="mc-conv-body">
              <div class="mc-conv-row">
                <span class="mc-conv-name">{{ item.name }}</span>
                <span class="mc-conv-time">{{ formatSessionTime(item.time) }}</span>
              </div>
              <div class="mc-conv-prev" :class="{ 'is-unread': item.unread > 0 }">
                {{ item.preview }}
              </div>
            </div>
          </button>
          <div v-if="listItems.length === 0" class="mc-list-empty">
            <el-icon :size="48" color="#dcdfe6"><ChatLineSquare /></el-icon>
          </div>
        </el-scrollbar>

        <div class="mc-left-bottom">
          <UserAvatarVip
            :size="28"
            :src="userStore.avatarUrl || defaultAvatar"
            :vip-tier="Number(userStore.vipTier) || 0"
            :vip-expire-at="userStore.vipExpireAt"
          />
          <span class="mc-left-uname">{{ userStore.nickname || '用户' }}</span>
          <div class="mc-online mc-online--trailing" :class="{ 'is-offline': !selfOnline }">
            <span class="mc-online-dot" />
            <span>{{ selfOnline ? '在线' : '离线' }}</span>
          </div>
        </div>
      </aside>

      <section class="mc-right">
        <template v-if="currentSession">
          <header class="mc-rhead">
            <div class="mc-rhead-left">
              <UserAvatarVip
                :size="34"
                :src="currentSession.user?.avatarUrl || defaultAvatar"
                :vip-tier="Number(currentSession.user?.vipTier) || 0"
                :vip-expire-at="currentSession.user?.vipExpireAt"
              />
              <span class="mc-rname">{{ currentSession.user?.nickname }}</span>
            </div>
            <div class="mc-online mc-online--trailing" :class="{ 'is-offline': !peerOnline }">
              <span class="mc-online-dot" />
              <span>{{ peerOnline ? '在线' : '离线' }}</span>
            </div>
          </header>

          <el-scrollbar ref="msgScrollbar" class="mc-rbody-scroll">
            <div ref="msgContainer" class="mc-rbody mc-rbody--chat">
              <div
                v-for="msg in messages"
                :key="msg.message?.id"
                class="mc-mrow"
                :class="msg.isOwner ? 'is-me' : 'is-other'"
              >
                <div class="mc-mrow-ava">
                  <UserAvatarVip
                    :size="28"
                    :src="msg.isOwner ? (userStore.avatarUrl || defaultAvatar) : (currentSession.user?.avatarUrl || defaultAvatar)"
                    :vip-tier="msg.isOwner ? Number(userStore.vipTier) || 0 : Number(currentSession.user?.vipTier) || 0"
                    :vip-expire-at="msg.isOwner ? userStore.vipExpireAt : currentSession.user?.vipExpireAt"
                  />
                </div>
                <div class="mc-bwrap">
                  <div class="mc-bubble-action">
                    <div
                      class="mc-bbl"
                      :class="{
                        'is-me': msg.isOwner && !isMediaMessage(msg) && Number(msg.message?.state) !== 2,
                        'is-recalled': Number(msg.message?.state) === 2,
                        'is-media': isMediaMessage(msg) && Number(msg.message?.state) !== 2,
                      }"
                    >
                      <span v-if="Number(msg.message?.state) === 2" class="mc-recalled">
                        {{ msg.isOwner ? '你撤回了一条消息' : '对方撤回了一条消息' }}
                      </span>
                      <template v-else-if="isMediaMessage(msg)">
                        <img
                          :src="msg.message.mediaUrl"
                          alt=""
                          class="mc-chat-img"
                          :class="{ 'is-gif': Number(msg.message?.messageType) === 2 }"
                          :style="bubbleImageStyle(msg.message)"
                        >
                        <button
                          v-if="!msg.isOwner && canFavoriteChatImage(msg)"
                          type="button"
                          class="mc-fav-img-btn"
                          @click="favoriteChatImage(msg)"
                        >
                          添加到表情
                        </button>
                      </template>
                      <span v-else>{{ msg.message?.content }}</span>
                    </div>
                    <button
                      v-if="msg.isOwner && Number(msg.message?.state) !== 2"
                      type="button"
                      class="mc-recall-btn"
                      @click="handleRecall(msg)"
                    >
                      撤回
                    </button>
                  </div>
                  <div class="mc-meta-row" :class="{ 'is-me': msg.isOwner }">
                    <span class="mc-btime">{{ formatTime(msg.message?.createTime) }}</span>
                    <span
                      v-if="msg.isOwner && Number(msg.message?.state) !== 2"
                      class="mc-read"
                    >
                      {{ Number(msg.message?.state) === 1 ? '已读' : '未读' }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </el-scrollbar>

          <footer class="mc-rinput">
            <input
              ref="chatImageInput"
              type="file"
              class="mc-hidden-file"
              accept="image/jpeg,image/jpg,image/png,image/gif"
              @change="onChatImageFileChange"
            >
            <input
              ref="emojiStickerInput"
              type="file"
              class="mc-hidden-file"
              accept="image/jpeg,image/jpg,image/png,image/gif"
              @change="onEmojiStickerFileChange"
            >
            <div class="mc-itools">
              <button type="button" class="mc-itbtn" title="发送图片" @click="triggerChatImagePick">
                <el-icon><Picture /></el-icon>
              </button>
              <el-popover placement="top-start" :width="340" trigger="click" @show="onEmojiPopoverShow">
                <template #reference>
                  <button type="button" class="mc-itbtn" title="表情">
                    <img :src="emojiPackIconUrl" alt="" class="mc-emoji-pack-icon">
                  </button>
                </template>
                <div
                  v-loading="chatEmojiStore.loading || (emojiPanelTab === 'purchased' && emojiShopStore.myPacksLoading)"
                  class="mc-emoji-panel"
                >
                  <el-tabs v-model="emojiPanelTab" class="mc-emoji-tabs" @tab-change="onEmojiTabChange">
                    <el-tab-pane label="收藏" name="favorites">
                      <div v-if="!chatEmojiStore.list.length" class="mc-emoji-empty">
                        <el-icon :size="40" color="#dcdfe6"><Picture /></el-icon>
                      </div>
                      <div v-else class="mc-emoji-grid">
                        <div
                          v-for="em in chatEmojiStore.list"
                          :key="em.id"
                          class="mc-emoji-cell"
                        >
                          <img
                            :src="em.mediaUrl"
                            alt=""
                            class="mc-emoji-thumb"
                            @click="sendMessageFromEmoji(em)"
                          >
                          <button
                            type="button"
                            class="mc-emoji-del"
                            aria-label="删除"
                            @click.stop="chatEmojiStore.remove(em.id)"
                          >
                            <el-icon><Close /></el-icon>
                          </button>
                        </div>
                        <button
                          type="button"
                          class="mc-emoji-add"
                          title="上传并添加到表情"
                          @click="triggerEmojiStickerPick"
                        >
                          <el-icon :size="22"><Plus /></el-icon>
                        </button>
                      </div>
                    </el-tab-pane>
                    <el-tab-pane label="已购" name="purchased">
                      <div v-if="!visiblePacks.length" class="mc-emoji-empty">
                        <el-icon :size="40" color="#dcdfe6"><Picture /></el-icon>
                      </div>
                      <div v-else class="mc-emoji-purchased">
                        <div v-for="pack in visiblePacks" :key="pack.userEmojiId" class="mc-emoji-pack">
                          <div class="mc-emoji-pack-title">{{ pack.name }}</div>
                          <div class="mc-emoji-grid mc-emoji-grid--pack">
                            <img
                              v-for="(url, uidx) in pack.imageUrls"
                              :key="uidx"
                              :src="url"
                              alt=""
                              class="mc-emoji-thumb"
                              @click="sendMessageFromShopUrl(url)"
                            >
                          </div>
                        </div>
                      </div>
                    </el-tab-pane>
                  </el-tabs>
                </div>
              </el-popover>
            </div>
            <div class="mc-irow">
              <textarea
                ref="inputBoxRef"
                v-model="sendContent"
                class="mc-ibox"
                rows="1"
                placeholder="在此输入消息内容…"
                @input="autoResizeInput"
              />
              <button type="button" class="mc-sbtn" :disabled="sending" @click="sendMsg">
                {{ sending ? '发送中' : '发送' }}
              </button>
            </div>
          </footer>
        </template>

        <template v-else-if="currentSystemGroup">
          <header class="mc-rhead">
            <div class="mc-rhead-left">
              <div class="mc-sys-ava mc-sys-ava--group mc-sys-ava--head">
                <el-icon :size="18"><Document /></el-icon>
              </div>
              <span class="mc-rname">{{ currentSystemGroup.name }}</span>
            </div>
          </header>
          <el-scrollbar class="mc-rbody-scroll">
            <div class="mc-rbody mc-rbody--notify">
              <article
                v-for="msg in activeSystemMessages"
                :key="msg.id"
                class="mc-ncard"
              >
                <div class="mc-ncard-head">
                  <span :class="sysTagClass(msg.type)" class="mc-tag">{{ sysTagLabel(msg.type) }}</span>
                  <span class="mc-ncard-time">{{ formatSessionTime(msg.createTime) }}</span>
                </div>
                <p class="mc-ncard-body">
                  <template v-if="parseSystemMessageContent(msg).articleTitle">
                    <span>{{ parseSystemMessageContent(msg).before }}</span>
                    <button
                      type="button"
                      class="mc-article-link"
                      @click="openArticleFromSystem(msg)"
                    >
                      《{{ parseSystemMessageContent(msg).articleTitle }}》
                    </button>
                    <span>{{ parseSystemMessageContent(msg).after }}</span>
                  </template>
                  <template v-else>
                    {{ parseSystemMessageContent(msg).plain }}
                  </template>
                </p>
              </article>
            </div>
          </el-scrollbar>
        </template>

        <div v-else class="mc-welcome">
          <el-icon :size="80" color="#e5e6eb"><ChatLineRound /></el-icon>
        </div>
      </section>
    </div>
  </el-dialog>
</template>

<script setup>
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import { useMessageView } from '@scripts/views/MessageView'

const {
  ChatLineRound,
  ChatLineSquare,
  Close,
  Document,
  Picture,
  Plus,
  Search,
  activeSystemMessages,
  activeTab,
  autoResizeInput,
  bubbleImageStyle,
  canFavoriteChatImage,
  chatEmojiStore,
  chatImageInput,
  currentSession,
  currentSystemGroup,
  defaultAvatar,
  dialogVisible,
  emojiPackIconUrl,
  emojiPanelTab,
  emojiShopStore,
  emojiStickerInput,
  favoriteChatImage,
  focusedConvKey,
  formatSessionTime,
  formatTime,
  handleClose,
  handleRecall,
  inputBoxRef,
  isActiveItem,
  isMediaMessage,
  listItems,
  messages,
  msgContainer,
  msgScrollbar,
  onChatImageFileChange,
  onConvBlur,
  onConvFocus,
  onDialogBlurRoot,
  onDialogOpened,
  onEmojiPopoverShow,
  onEmojiTabChange,
  onEmojiStickerFileChange,
  openArticleFromSystem,
  parseSystemMessageContent,
  peerOnline,
  scrollToBottom,
  searchQuery,
  selfOnline,
  selectListItem,
  sendContent,
  sendMessageFromEmoji,
  sendMessageFromShopUrl,
  sendMsg,
  sending,
  sysTagClass,
  sysTagLabel,
  tabBadges,
  triggerChatImagePick,
  triggerEmojiStickerPick,
  userStore,
  viewerIsVip,
  visiblePacks,
} = useMessageView()
</script>

<style src="@/assets/styles/message.css"></style>
