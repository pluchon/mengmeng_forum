<template>
  <transition name="el-zoom-in-center">
    <div
      v-if="visible"
      class="message-tip-bubble message-tip-bubble--envelope"
      role="button"
      tabindex="0"
      @click.stop="onOpen"
      @keydown.enter.prevent="onOpen"
    >
      <div class="tip-content">
        <!-- 群聊：第一行群头像与群名，第二行「谁说了什么」 -->
        <span class="tip-title">
          <img
            v-if="displayAvatarUrl"
            class="tip-group-avatar"
            :src="displayAvatarUrl"
            alt=""
          >
          {{ displayTitle }}
        </span>
        <span class="tip-preview">{{ displayPreview }}</span>
        <span v-if="unread > 0" class="tip-count">{{ unread }} 条未读</span>
      </div>
      <el-icon class="tip-close" @click.stop="onClose"><Close /></el-icon>
    </div>
  </transition>
</template>

<script setup src="@scripts/components/layout/MessageIncomingBubble.js"></script>
