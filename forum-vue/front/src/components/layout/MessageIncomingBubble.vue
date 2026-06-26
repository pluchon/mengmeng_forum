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
        <span class="tip-title">{{ preview?.sender || '新私信' }}</span>
        <span class="tip-preview">{{ displayPreview }}</span>
        <span v-if="unread > 0" class="tip-count">{{ unread }} 条未读</span>
      </div>
      <el-icon class="tip-close" @click.stop="onClose"><Close /></el-icon>
    </div>
  </transition>
</template>

<script setup src="@scripts/components/layout/MessageIncomingBubble.js"></script>
