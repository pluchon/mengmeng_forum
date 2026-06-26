<template>
  <section class="gobang-chat-box game-room-chat-panel" :class="{ 'is-readonly': !canChat }">
    <div class="gobang-chat-head">
      <el-icon><ChatDotRound /></el-icon>
      <strong>房间聊天</strong>
    </div>
    <div ref="chatListRef" class="gobang-chat-list">
      <div
        v-for="(msg, index) in messages"
        :key="index"
        class="gobang-chat-msg"
        :class="{ 'is-me': msg.userId === currentUserId }"
      >
        <span>{{ displayName(msg.userId) }}</span>
        <img v-if="isEmojiMessage(msg)" :src="msg.emojiUrl || msg.content" alt="表情" />
        <p v-else>{{ msg.content }}</p>
      </div>
      <div v-if="!messages?.length" class="gobang-chat-empty">暂无消息</div>
    </div>
    <div class="game-room-chat-tools">
      <el-popover
        v-model:visible="emojiPanelOpen"
        placement="top-start"
        :width="320"
        trigger="click"
        teleported
        popper-class="comment-emoji-popper"
        :z-index="6500"
        :disabled="!canChat"
        @show="onEmojiPanelShow"
      >
        <template #reference>
          <button
            type="button"
            class="game-room-chat-emoji-btn"
            title="已购表情"
            :disabled="!canChat"
            @click.stop
          >
            <img :src="emojiPackIconUrl" alt="" class="comment-emoji-pack-icon">
          </button>
        </template>
        <div v-loading="emojiShopStore.myPacksLoading" class="comment-emoji-panel">
          <div v-if="!visiblePacks.length" class="comment-emoji-empty">暂无已购表情包</div>
          <div v-else class="mc-emoji-purchased-layout">
            <div class="mc-emoji-pack-body">
              <div class="mc-emoji-grid mc-emoji-grid--pack mc-emoji-grid--scroll">
                <img
                  v-for="(url, uidx) in (selectedPack?.imageUrls || [])"
                  :key="uidx"
                  :src="url"
                  alt=""
                  class="mc-emoji-thumb"
                  @click="onPickEmoji(url)"
                >
              </div>
            </div>
            <div class="mc-emoji-pack-bar">
              <button
                v-if="packBarCanScrollLeft"
                type="button"
                class="mc-emoji-pack-more"
                aria-label="向左查看更多"
                @click="scrollPackBarLeft"
              >
                <el-icon><ArrowLeft /></el-icon>
              </button>
              <div
                ref="packBarRef"
                class="mc-emoji-pack-bar-scroll"
                @scroll="onPackBarScroll"
              >
                <div
                  v-for="pack in visiblePacks"
                  :key="pack.userEmojiId"
                  class="mc-emoji-pack-bar-item"
                >
                  <button
                    type="button"
                    class="mc-emoji-pack-cover"
                    :class="{ 'is-active': Number(selectedPack?.shopId) === Number(pack.shopId) }"
                    :title="pack.name"
                    @click="selectPack(pack)"
                  >
                    <img :src="pack.coverUrl || pack.imageUrls?.[0]" alt="">
                  </button>
                  <transition name="mc-pack-name">
                    <span
                      v-if="Number(selectedPack?.shopId) === Number(pack.shopId)"
                      :key="pack.shopId"
                      class="mc-emoji-pack-name"
                    >{{ pack.name }}</span>
                  </transition>
                </div>
              </div>
              <button
                v-if="packBarCanScrollRight"
                type="button"
                class="mc-emoji-pack-more"
                aria-label="向右查看更多"
                @click="scrollPackBarRight"
              >
                <el-icon><ArrowRight /></el-icon>
              </button>
            </div>
          </div>
        </div>
      </el-popover>
    </div>
    <div class="gobang-chat-input">
      <el-input
        v-model="chatText"
        :disabled="!canChat"
        maxlength="200"
        placeholder="发送消息"
        @keyup.enter="sendChat"
      />
      <el-button class="game-room-chat-send" :disabled="!canChat || !chatText.trim()" @click="sendChat">
        发送
      </el-button>
    </div>
  </section>
</template>

<script setup src="@scripts/components/game/GameRoomChatPanel.js"></script>

<style scoped src="@/assets/styles/game-room-chat.css"></style>
