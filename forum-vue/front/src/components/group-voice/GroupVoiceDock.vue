<template>
  <div v-if="showDock" class="group-voice-dock">
    <button
      type="button"
      class="group-voice-float"
      :style="dockStyle"
      :title="voiceStore.floatTitle"
      @click="openDialog"
      @pointerdown="startDrag"
    >
      <el-icon><PhoneFilled /></el-icon>
    </button>
  </div>

  <el-dialog
    v-model="voiceStore.dialogVisible"
    class="group-voice-dialog"
    width="440px"
    append-to-body
    :show-close="false"
  >
    <template #header>
      <div class="group-voice-head" :class="{ 'is-private': voiceStore.voiceKind === 'private' }">
        <div class="group-voice-title-row" :class="{ 'is-private': voiceStore.voiceKind === 'private' }">
          <strong>{{ voiceStore.dialogTitle }}</strong>
          <span v-if="voiceStore.voiceKind !== 'private'">{{ voiceStore.memberCount }}/{{ voiceStore.maxSeats }} 人正在聊天</span>
        </div>
        <button type="button" class="group-voice-icon-btn" aria-label="关闭" @click="voiceStore.dialogVisible = false">
          <el-icon><Close /></el-icon>
        </button>
      </div>
    </template>

    <div class="group-voice-seats" :class="{ 'is-private': voiceStore.maxSeats === 2 }">
      <div
        v-for="seat in seats"
        :key="seat.key"
        class="group-voice-seat"
        :class="{ 'is-empty': !seat.participant }"
      >
        <span
          v-if="seat.participant"
          class="group-voice-seat-status"
          :class="{ 'is-connected': voiceStore.isParticipantConnected(seat.participant) }"
          :title="connectionLabel(seat.participant)"
          :aria-label="connectionLabel(seat.participant)"
        />
        <div
          v-if="seat.participant"
          class="group-voice-avatar-shell"
          :class="{ 'is-vip': isVipParticipant(seat.participant) }"
        >
          <div class="group-voice-avatar">
            <img
              v-if="avatarSrc(seat.participant)"
              :src="avatarSrc(seat.participant)"
              alt=""
              @error="markAvatarFailed(seat.participant)"
            >
            <span v-else>{{ avatarText(seat.participant) }}</span>
          </div>
        </div>
        <div v-else class="group-voice-empty-avatar">+</div>
        <span class="group-voice-seat-name">{{ seat.participant ? voiceStore.nameFor(seat.participant) : '空席位' }}</span>
      </div>
    </div>

    <div class="group-voice-controls">
      <button
        type="button"
        class="group-voice-control"
        :class="{ 'is-off': voiceStore.muted }"
        :disabled="voiceStore.deafened"
        @click="voiceStore.toggleMuted"
      >
        <span class="group-voice-mic-icon">
          <el-icon><Microphone /></el-icon>
          <span
            v-for="bar in volumeBars"
            :key="bar.index"
            class="group-voice-volume-bar"
            :style="{ height: bar.height }"
          />
        </span>
        <span>{{ voiceStore.muted ? '麦克风已关' : '麦克风开启' }}</span>
      </button>
      <button
        type="button"
        class="group-voice-control"
        :class="{ 'is-off': voiceStore.deafened }"
        @click="voiceStore.toggleDeafened"
      >
        <el-icon><Headset /></el-icon>
        <span>{{ voiceStore.deafened ? '已关闭喇叭' : '正在收听' }}</span>
      </button>
      <button type="button" class="group-voice-leave" @click="leaveVoice">
        <el-icon><Phone /></el-icon>
        <span>退出</span>
      </button>
    </div>

    <div v-if="voiceStore.outputDevices.length || voiceStore.remoteAudioBlocked" class="group-voice-audio-line">
      <label v-if="voiceStore.outputDevices.length" class="group-voice-output-select">
        <span>音频输出设备：</span>
        <select
          :value="voiceStore.outputDeviceId"
          @change="voiceStore.setOutputDevice($event.target.value)"
        >
          <option value="">系统默认</option>
          <option
            v-for="device in voiceStore.outputDevices"
            :key="device.deviceId"
            :value="device.deviceId"
          >
            {{ device.label || '音频输出设备' }}
          </option>
        </select>
      </label>
      <button v-if="voiceStore.remoteAudioBlocked" type="button" @click="voiceStore.unlockRemoteAudio">
        播放声音
      </button>
    </div>
  </el-dialog>
</template>

<script setup>
import { useGroupVoiceDock } from '@/scripts/components/group-voice/GroupVoiceDock'

const {
  Close,
  Headset,
  Microphone,
  Phone,
  PhoneFilled,
  avatarSrc,
  avatarText,
  connectionLabel,
  dockStyle,
  isVipParticipant,
  leaveVoice,
  markAvatarFailed,
  openDialog,
  seats,
  showDock,
  startDrag,
  voiceStore,
  volumeBars,
} = useGroupVoiceDock()
</script>

<style src="@/assets/styles/group-voice.css"></style>
