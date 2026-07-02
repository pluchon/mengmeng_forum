import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import {
  getGroupVoiceSession,
  joinGroupVoiceSession,
  leaveGroupVoiceSession,
  startGroupVoiceSession,
} from '@/api/groupChat'
import {
  acceptPrivateVoiceSession,
  declinePrivateVoiceSession,
  getPrivateVoiceSession,
  leavePrivateVoiceSession,
  startPrivateVoiceSession,
} from '@/api/message'
import { getVoiceIceConfig } from '@/api/voice'
import { useUserStore } from '@/stores/user'
import { useWebSocket } from '@/composables/useWebSocket'
import { DEFAULT_AVATAR } from '@/utils/constants'

const GROUP_MAX_SEATS = 6
const PRIVATE_MAX_SEATS = 2
const DEFAULT_RTC_CONFIG = {
  iceServers: [{ urls: 'stun:stun.l.google.com:19302' }],
}
const VOICE_SESSION_STORAGE_KEY = 'forum:voice:joined-session'
const LEGACY_GROUP_STORAGE_KEY = 'forum:group-voice:joined-group-id'
const CONNECT_RETRY_DELAYS = [0, 700, 1800]

const CONNECTION_STATUS_TEXT = {
  idle: '未连接',
  ready: '等待新伙伴加入',
  connecting: '正在连接',
  connected: '语音已连接',
  failed: '连接失败',
}

export const useGroupVoiceStore = defineStore('groupVoice', () => {
  const session = ref(null)
  const sessionsByGroup = ref({})
  const privateSessionsByPeer = ref({})
  const dialogVisible = ref(false)
  const floatPosition = ref({ top: 86, right: 24 })
  const muted = ref(false)
  const deafened = ref(false)
  const joining = ref(false)
  const localStream = ref(null)
  const remoteStreams = ref({})
  const volumeLevel = ref(0)
  const outputDevices = ref([])
  const outputDeviceId = ref('')
  const remoteAudioBlocked = ref(false)
  const connectionStatus = ref('idle')
  const connectionMessage = ref('')
  const signalStats = ref({
    connected: 0,
    connecting: 0,
    failed: 0,
    dropped: 0,
  })
  const iceConfig = ref(DEFAULT_RTC_CONFIG)

  const peerConnections = new Map()
  const remoteAudioEls = new Map()
  const connectTimers = new Set()
  let audioContext = null
  let analyser = null
  let volumeFrameId = 0
  let webRtcUnsupportedShown = false
  let iceConfigLoaded = false

  const userStore = useUserStore()
  const voiceKind = computed(() => session.value?.voiceKind || null)
  const active = computed(() => session.value?.active === true)
  const joined = computed(() => session.value?.currentUserJoined === true)
  const participants = computed(() => session.value?.participants || [])
  const memberCount = computed(() => Number(session.value?.memberCount) || 0)
  const maxSeats = computed(() =>
    Number(session.value?.maxSeats) || (voiceKind.value === 'private' ? PRIVATE_MAX_SEATS : GROUP_MAX_SEATS),
  )
  const currentGroupId = computed(() =>
    voiceKind.value === 'group' ? session.value?.groupId || null : null,
  )
  const currentPeerUserId = computed(() =>
    voiceKind.value === 'private' ? session.value?.peerUserId || null : null,
  )
  const currentVoiceKey = computed(() => sessionKeyFor(session.value))
  const roomVersion = computed(() => session.value?.roomVersion || null)
  const currentConnectionId = computed(() => session.value?.currentConnectionId || '')
  const remotePeerCount = computed(() => Object.keys(remoteStreams.value).length)
  const connectionStatusText = computed(() =>
    connectionMessage.value || CONNECTION_STATUS_TEXT[connectionStatus.value] || '未连接',
  )
  const dialogTitle = computed(() => (voiceKind.value === 'private' ? '私聊语音' : '群语音'))
  const floatTitle = computed(() => (voiceKind.value === 'private' ? '私聊语音聊天' : '群语音聊天'))

  async function fetchSession(groupId) {
    if (!groupId || !userStore.isLoggedIn) return null
    const res = await getGroupVoiceSession(groupId)
    if (res.code === 0) {
      const next = normalizeGroupSession(res.data)
      cacheSession(next)
      applyStatusSession(next)
      persistIfJoined(next)
      return next
    }
    return null
  }

  async function fetchPrivateSession(peerUserId) {
    if (!peerUserId || !userStore.isLoggedIn) return null
    const res = await getPrivateVoiceSession(peerUserId)
    if (res.code === 0) {
      const next = normalizePrivateSession(res.data)
      cacheSession(next)
      applyStatusSession(next)
      persistIfJoined(next)
      return next
    }
    return null
  }

  async function start(groupId) {
    if (!groupId || !canEnterVoice('group', groupId)) return null
    joining.value = true
    try {
      const res = await startGroupVoiceSession(groupId)
      if (res.code !== 0) return null
      const next = normalizeGroupSession(res.data)
      applySession(next)
      persistJoinedSession(next)
      await showDialogNextFrame()
      await startLocalVoiceOrLeave()
      return next
    } finally {
      joining.value = false
    }
  }

  async function join(groupId) {
    if (!groupId || !canEnterVoice('group', groupId)) return null
    if (active.value && joined.value && Number(currentGroupId.value) === Number(groupId) && localStream.value) {
      dialogVisible.value = true
      scheduleConnectPeers()
      return session.value
    }
    joining.value = true
    try {
      const res = await joinGroupVoiceSession(groupId)
      if (res.code !== 0) return null
      const next = normalizeGroupSession(res.data)
      applySession(next)
      persistJoinedSession(next)
      await showDialogNextFrame()
      await startLocalVoiceOrLeave()
      return next
    } finally {
      joining.value = false
    }
  }

  async function startPrivate(peerUserId) {
    if (!peerUserId || !canEnterVoice('private', peerUserId)) return null
    joining.value = true
    try {
      const res = await startPrivateVoiceSession(peerUserId)
      if (res.code !== 0) return null
      const next = normalizePrivateSession(res.data)
      applySession(next)
      persistJoinedSession(next)
      await startLocalVoiceOrLeave()
      return next
    } finally {
      joining.value = false
    }
  }

  async function acceptPrivate(peerUserId) {
    if (!peerUserId || !canEnterVoice('private', peerUserId)) return null
    if (active.value && joined.value && Number(currentPeerUserId.value) === Number(peerUserId) && localStream.value) {
      dialogVisible.value = true
      scheduleConnectPeers()
      return session.value
    }
    joining.value = true
    try {
      const res = await acceptPrivateVoiceSession(peerUserId)
      if (res.code !== 0) return null
      const next = normalizePrivateSession(res.data)
      applySession(next)
      persistJoinedSession(next)
      await showDialogNextFrame()
      await startLocalVoiceOrLeave()
      return next
    } finally {
      joining.value = false
    }
  }

  async function declinePrivate(peerUserId) {
    if (!peerUserId) return null
    const res = await declinePrivateVoiceSession(peerUserId)
    if (res.code !== 0) return null
    const next = normalizePrivateSession(res.data)
    cacheSession(next)
    if (currentVoiceKey.value === sessionKeyFor(next)) {
      await leave(false)
    }
    return next
  }

  async function leave(callApi = true) {
    const previous = session.value
    const groupId = previous?.voiceKind === 'group' ? previous.groupId : null
    const peerUserId = previous?.voiceKind === 'private' ? previous.peerUserId : null
    resetPeerRuntime()
    stopLocalStream()
    dialogVisible.value = false
    clearPersistedSession()
    if (callApi && previous?.voiceKind === 'group' && groupId) {
      try {
        const res = await leaveGroupVoiceSession(groupId)
        if (res.code === 0) {
          applySession(normalizeGroupSession(res.data))
          return
        }
      } catch {
        clearSession()
        return
      }
    }
    if (callApi && previous?.voiceKind === 'private' && peerUserId) {
      try {
        const res = await leavePrivateVoiceSession(peerUserId)
        if (res.code === 0) {
          applySession(normalizePrivateSession(res.data))
          return
        }
      } catch {
        clearSession()
        return
      }
    }
    clearSession()
  }

  function onVoiceStatus(payload) {
    const next = normalizeStatusSession(payload)
    if (!next) return
    cacheSession(next)
    const previousKey = currentVoiceKey.value
    const wasJoined = joined.value
    const wasPrivateWaiting = session.value?.voiceKind === 'private'
      && session.value?.active === true
      && session.value?.currentUserJoined === true
      && session.value?.currentUserInitiator === true
      && (Number(session.value?.memberCount) || 0) < PRIVATE_MAX_SEATS
    const nextKey = sessionKeyFor(next)
    if (wasJoined && previousKey === nextKey && next.active === false) {
      void leave(false)
      return
    }
    applyStatusSession(next)
    persistIfJoined(next)
    if (previousKey && currentVoiceKey.value && previousKey !== currentVoiceKey.value) {
      resetPeerRuntime()
    }
    if (wasPrivateWaiting
        && next.voiceKind === 'private'
        && next.currentUserJoined === true
        && (Number(next.memberCount) || 0) >= PRIVATE_MAX_SEATS) {
      void showDialogNextFrame()
    }
    if (joined.value && localStream.value) {
      scheduleConnectPeers()
    } else {
      refreshConnectionStatus()
    }
  }

  async function onVoiceSignal(payload) {
    const fromUserId = Number(payload?.fromUserId)
    if (!Number.isFinite(fromUserId) || fromUserId === selfUserId()) {
      return
    }
    try {
      const ready = await ensureSignalSession(payload)
      if (!ready || !isSignalForCurrentConnection(payload)) {
        incrementSignalStat('dropped')
        return
      }
      await enterMedia()
      const fromConnectionId = String(payload.fromConnectionId || '')
      const record = ensurePeerConnection(fromUserId, fromConnectionId)
      const signalType = String(payload.signalType || '').trim()
      if (signalType === 'offer') {
        await handleRemoteOffer(record, payload.payload)
        return
      }
      if (signalType === 'answer') {
        await handleRemoteAnswer(record, payload.payload)
        return
      }
      if (signalType === 'candidate') {
        await handleRemoteCandidate(record, payload.payload)
      }
    } catch (error) {
      handlePeerConnectionError(error)
    }
  }

  async function ensureSignalSession(payload) {
    const type = payload?.type === 'private_voice_signal' ? 'private' : 'group'
    if (type === voiceKind.value && joined.value) {
      if (type === 'group' && Number(payload?.groupId) === Number(currentGroupId.value)) {
        return true
      }
      if (type === 'private' && payload?.sessionId === session.value?.sessionId) {
        return true
      }
    }
    if (type === 'group') {
      const groupId = Number(payload?.groupId)
      if (!Number.isFinite(groupId)) return false
      const next = await fetchSession(groupId)
      return next?.active === true && next.currentUserJoined === true
    }
    const peerUserId = Number(payload?.peerUserId)
    if (!Number.isFinite(peerUserId)) return false
    const next = await fetchPrivateSession(peerUserId)
    return next?.active === true && next.currentUserJoined === true
  }

  async function handleRemoteOffer(record, offer) {
    const pc = record.pc
    const offerCollision = record.makingOffer || pc.signalingState !== 'stable'
    record.ignoreOffer = !isPolitePeer(record.connectionId) && offerCollision
    if (record.ignoreOffer) {
      incrementSignalStat('dropped')
      return
    }
    if (offerCollision) {
      await pc.setLocalDescription({ type: 'rollback' })
    }
    await pc.setRemoteDescription(offer)
    await flushPendingCandidates(record)
    const answer = await pc.createAnswer()
    await pc.setLocalDescription(answer)
    sendSignal(record.peerId, 'answer', pc.localDescription)
  }

  async function handleRemoteAnswer(record, answer) {
    record.ignoreOffer = false
    if (record.pc.signalingState !== 'have-local-offer') {
      incrementSignalStat('dropped')
      return
    }
    await record.pc.setRemoteDescription(answer)
    await flushPendingCandidates(record)
  }

  async function handleRemoteCandidate(record, candidate) {
    if (record.ignoreOffer || !candidate) {
      incrementSignalStat('dropped')
      return
    }
    if (record.pc.remoteDescription) {
      await record.pc.addIceCandidate(candidate)
      return
    }
    record.pendingCandidates.push(candidate)
  }

  async function connectPeers() {
    if (!joined.value || !localStream.value || !currentConnectionId.value || !roomVersion.value) {
      refreshConnectionStatus()
      return
    }
    let expectedPeers = 0
    for (const participant of participants.value) {
      const peerId = Number(participant?.user?.id)
      const peerConnectionId = String(participant?.connectionId || '')
      if (!Number.isFinite(peerId) || peerId === selfUserId() || !peerConnectionId) continue
      expectedPeers += 1
      const record = ensurePeerConnection(peerId, peerConnectionId)
      if (shouldInitiateOffer(participant) && record.pc.signalingState === 'stable' && !record.pc.localDescription) {
        await makeOffer(record)
      }
    }
    prunePeerConnections()
    refreshConnectionStatus(expectedPeers)
  }

  function ensurePeerConnection(peerId, peerConnectionId) {
    const current = peerConnections.get(peerId)
    if (current?.connectionId === peerConnectionId) {
      return current
    }
    if (current) {
      closePeerRecord(peerId, current)
    }
    const pc = createPeerConnection()
    const record = {
      pc,
      peerId,
      connectionId: peerConnectionId,
      makingOffer: false,
      ignoreOffer: false,
      pendingCandidates: [],
    }
    if (localStream.value) {
      localStream.value.getTracks().forEach((track) => pc.addTrack(track, localStream.value))
    }
    pc.onnegotiationneeded = () => {
      const participant = participantByUserId(peerId)
      if (participant && shouldInitiateOffer(participant)) {
        void makeOffer(record).catch(handlePeerConnectionError)
      }
    }
    pc.onicecandidate = (event) => {
      if (event.candidate) {
        sendSignal(peerId, 'candidate', event.candidate)
      }
    }
    pc.onconnectionstatechange = () => {
      refreshConnectionStatus()
    }
    pc.oniceconnectionstatechange = () => {
      refreshConnectionStatus()
    }
    pc.ontrack = (event) => {
      const stream = event.streams?.[0] || new MediaStream([event.track])
      if (!stream) return
      remoteStreams.value = { ...remoteStreams.value, [peerId]: stream }
      attachRemoteAudio(peerId, stream)
      refreshConnectionStatus()
    }
    peerConnections.set(peerId, record)
    refreshConnectionStatus()
    return record
  }

  async function makeOffer(record) {
    if (record.makingOffer || record.pc.signalingState !== 'stable') return
    record.makingOffer = true
    try {
      const offer = await record.pc.createOffer()
      await record.pc.setLocalDescription(offer)
      sendSignal(record.peerId, 'offer', record.pc.localDescription)
    } finally {
      record.makingOffer = false
    }
  }

  async function startLocalVoiceOrLeave() {
    try {
      await loadIceConfig()
      await enterMedia()
      scheduleConnectPeers()
    } catch (error) {
      await leave()
      throw error
    }
  }

  async function enterMedia() {
    if (localStream.value) return localStream.value
    try {
      const stream = await requestMicrophoneStream()
      localStream.value = stream
      applyLocalAudioState()
      startVolumeMeter(stream)
      void loadOutputDevices()
      refreshConnectionStatus()
      return stream
    } catch (error) {
      const message = microphoneErrorMessage(error)
      connectionStatus.value = 'failed'
      connectionMessage.value = message
      ElMessage.error(message)
      throw new Error(message, { cause: error })
    }
  }

  async function requestMicrophoneStream() {
    if (!navigator.mediaDevices?.getUserMedia) {
      throw new DOMException('getUserMedia unavailable', 'SecurityError')
    }
    try {
      return await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
        video: false,
      })
    } catch (error) {
      if (error?.name === 'OverconstrainedError' || error?.name === 'ConstraintNotSatisfiedError') {
        return navigator.mediaDevices.getUserMedia({ audio: true, video: false })
      }
      throw error
    }
  }

  function microphoneErrorMessage(error) {
    const name = error?.name || ''
    if (!window.isSecureContext) {
      return '浏览器只允许 HTTPS 或 localhost 使用麦克风，请切换到 localhost 或配置 HTTPS'
    }
    if (name === 'NotAllowedError' || name === 'SecurityError' || name === 'PermissionDeniedError') {
      return '麦克风被浏览器或系统拒绝，请检查 Edge 站点权限和 Windows 麦克风隐私设置'
    }
    if (name === 'NotFoundError' || name === 'DevicesNotFoundError') {
      return '没有检测到可用麦克风设备'
    }
    if (name === 'NotReadableError' || name === 'TrackStartError') {
      return '麦克风被其他程序占用，或系统暂时无法打开该设备'
    }
    if (name === 'OverconstrainedError' || name === 'ConstraintNotSatisfiedError') {
      return '当前麦克风不支持浏览器要求的音频参数'
    }
    if (name === 'AbortError') {
      return '麦克风启动失败，请重新插拔设备或刷新页面后重试'
    }
    return '无法打开麦克风，请检查浏览器权限、系统权限和设备状态'
  }

  function sendSignal(targetUserId, signalType, payload) {
    const target = participantByUserId(targetUserId)
    if (!roomVersion.value
        || !currentConnectionId.value
        || !target?.connectionId
        || !payload) {
      return
    }
    const { sendNotifyMessage } = useWebSocket()
    if (voiceKind.value === 'group' && currentGroupId.value) {
      sendNotifyMessage({
        type: 'group_voice_signal',
        groupId: currentGroupId.value,
        roomVersion: roomVersion.value,
        senderConnectionId: currentConnectionId.value,
        targetUserId,
        targetConnectionId: target.connectionId,
        signalType,
        payload,
      })
      return
    }
    if (voiceKind.value === 'private' && currentPeerUserId.value) {
      sendNotifyMessage({
        type: 'private_voice_signal',
        peerUserId: currentPeerUserId.value,
        roomVersion: roomVersion.value,
        senderConnectionId: currentConnectionId.value,
        targetUserId,
        targetConnectionId: target.connectionId,
        signalType,
        payload,
      })
    }
  }

  function applySession(next) {
    const previous = session.value
    const normalized = normalizeStatusSession({ session: next }) || next
    session.value = normalized
    cacheSession(normalized)
    if (!normalized?.active) {
      connectionStatus.value = 'idle'
      connectionMessage.value = ''
      return
    }
    if (previous
        && previous.roomVersion
        && normalized.roomVersion
        && previous.roomVersion !== normalized.roomVersion) {
      resetPeerRuntime()
    }
    refreshConnectionStatus()
  }

  function applyStatusSession(next) {
    if (!next) return
    const nextKey = sessionKeyFor(next)
    const currentKey = currentVoiceKey.value
    const wasJoined = joined.value
    if (next.currentUserJoined || (currentKey && currentKey === nextKey) || (!wasJoined && !session.value)) {
      applySession(next)
    }
  }

  function clearSession() {
    session.value = null
    muted.value = false
    deafened.value = false
    remoteStreams.value = {}
    remoteAudioBlocked.value = false
    connectionStatus.value = 'idle'
    connectionMessage.value = ''
    resetSignalStats()
  }

  function cacheSession(next) {
    if (!next) return
    if (next.voiceKind === 'group' && next.groupId) {
      sessionsByGroup.value = {
        ...sessionsByGroup.value,
        [String(next.groupId)]: next,
      }
      return
    }
    if (next.voiceKind === 'private' && next.peerUserId) {
      privateSessionsByPeer.value = {
        ...privateSessionsByPeer.value,
        [String(next.peerUserId)]: next,
      }
    }
  }

  function sessionFor(groupId) {
    if (!groupId) return null
    return sessionsByGroup.value[String(groupId)] || null
  }

  function privateSessionFor(peerUserId) {
    if (!peerUserId) return null
    return privateSessionsByPeer.value[String(peerUserId)] || null
  }

  async function restorePersistedSession() {
    const persisted = readPersistedSession()
    if (!persisted || !userStore.isLoggedIn) return null
    const next = persisted.voiceKind === 'private'
      ? await fetchPrivateSession(persisted.peerUserId)
      : await fetchSession(persisted.groupId)
    if (!next?.active || !next.currentUserJoined) {
      clearPersistedSession()
      return null
    }
    applySession(next)
    return next
  }

  async function openVoiceDialog() {
    if (!active.value || !joined.value) return
    await showDialogNextFrame()
    if (!localStream.value) {
      if (voiceKind.value === 'private') {
        await acceptPrivate(currentPeerUserId.value)
      } else {
        await join(currentGroupId.value)
      }
      return
    }
    scheduleConnectPeers()
  }

  function normalizeStatusSession(payload) {
    const raw = payload?.session || payload
    if (!raw) return null
    if (raw.voiceKind === 'private' || raw.peerUserId != null || raw.sessionId != null) {
      return normalizePrivateSession(raw)
    }
    return normalizeGroupSession(raw)
  }

  function normalizeGroupSession(raw) {
    if (!raw) return null
    const list = Array.isArray(raw.participants) ? raw.participants.slice(0, GROUP_MAX_SEATS) : []
    return {
      ...raw,
      voiceKind: 'group',
      active: raw.active === true,
      participants: list,
      memberCount: Number(raw.memberCount) || list.length,
      maxSeats: Number(raw.maxSeats) || GROUP_MAX_SEATS,
      roomVersion: raw.roomVersion == null ? null : Number(raw.roomVersion),
      currentConnectionId: raw.currentConnectionId || '',
    }
  }

  function normalizePrivateSession(raw) {
    if (!raw) return null
    const list = Array.isArray(raw.participants) ? raw.participants.slice(0, PRIVATE_MAX_SEATS) : []
    return {
      ...raw,
      voiceKind: 'private',
      active: raw.active === true,
      participants: list,
      memberCount: Number(raw.memberCount) || list.length,
      maxSeats: Number(raw.maxSeats) || PRIVATE_MAX_SEATS,
      roomVersion: raw.roomVersion == null ? null : Number(raw.roomVersion),
      currentConnectionId: raw.currentConnectionId || '',
    }
  }

  function toggleMuted() {
    if (deafened.value) return
    muted.value = !muted.value
    applyLocalAudioState()
  }

  function toggleDeafened() {
    deafened.value = !deafened.value
    if (deafened.value) muted.value = true
    applyLocalAudioState()
    remoteAudioEls.forEach((audio) => {
      audio.muted = deafened.value
    })
  }

  function applyLocalAudioState() {
    if (!localStream.value) return
    localStream.value.getAudioTracks().forEach((track) => {
      track.enabled = !muted.value && !deafened.value
    })
  }

  function attachRemoteAudio(peerId, stream) {
    let audio = remoteAudioEls.get(peerId)
    if (!audio) {
      audio = document.createElement('audio')
      audio.autoplay = true
      audio.playsInline = true
      audio.style.display = 'none'
      document.body.appendChild(audio)
      remoteAudioEls.set(peerId, audio)
    }
    audio.srcObject = stream
    audio.muted = deafened.value
    void applyOutputDevice(audio).then(() => playRemoteAudio(audio))
  }

  async function loadOutputDevices() {
    if (!navigator.mediaDevices?.enumerateDevices) return
    try {
      const devices = await navigator.mediaDevices.enumerateDevices()
      outputDevices.value = devices
        .filter((device) => device.kind === 'audiooutput')
        .map((device) => ({
          deviceId: device.deviceId,
          label: device.label,
        }))
    } catch {
      outputDevices.value = []
    }
  }

  async function setOutputDevice(deviceId) {
    outputDeviceId.value = deviceId || ''
    const audios = Array.from(remoteAudioEls.values())
    await Promise.all(audios.map((audio) => applyOutputDevice(audio)))
    await unlockRemoteAudio()
  }

  async function applyOutputDevice(audio) {
    if (!audio || typeof audio.setSinkId !== 'function') return
    try {
      await audio.setSinkId(outputDeviceId.value || '')
    } catch {
      ElMessage.warning('当前浏览器不允许切换到该声音输出设备')
    }
  }

  async function playRemoteAudio(audio) {
    if (!audio) return
    try {
      await audio.play()
      remoteAudioBlocked.value = false
    } catch {
      remoteAudioBlocked.value = true
    }
  }

  async function unlockRemoteAudio() {
    remoteAudioBlocked.value = false
    const audios = Array.from(remoteAudioEls.values())
    for (const audio of audios) {
      await playRemoteAudio(audio)
    }
  }

  function stopLocalStream() {
    if (!localStream.value) return
    localStream.value.getTracks().forEach((track) => track.stop())
    localStream.value = null
    stopVolumeMeter()
  }

  function resetPeerRuntime() {
    clearConnectTimers()
    closePeerConnections()
    removeRemoteAudios()
    remoteStreams.value = {}
    remoteAudioBlocked.value = false
    resetSignalStats()
    refreshConnectionStatus()
  }

  function closePeerConnections() {
    peerConnections.forEach((record, peerId) => closePeerRecord(peerId, record))
    peerConnections.clear()
  }

  function closePeerRecord(peerId, record) {
    try {
      record.pc.close()
    } catch {
      /* ignore */
    }
    removeRemoteAudio(peerId)
  }

  function prunePeerConnections() {
    const activePeers = new Map()
    participants.value.forEach((participant) => {
      const peerId = Number(participant?.user?.id)
      const peerConnectionId = String(participant?.connectionId || '')
      if (Number.isFinite(peerId) && peerId !== selfUserId() && peerConnectionId) {
        activePeers.set(peerId, peerConnectionId)
      }
    })
    peerConnections.forEach((record, peerId) => {
      if (activePeers.get(peerId) !== record.connectionId) {
        closePeerRecord(peerId, record)
        peerConnections.delete(peerId)
      }
    })
  }

  async function flushPendingCandidates(record) {
    const candidates = record.pendingCandidates.splice(0)
    for (const candidate of candidates) {
      await record.pc.addIceCandidate(candidate)
    }
  }

  function removeRemoteAudio(peerId) {
    const audio = remoteAudioEls.get(peerId)
    if (audio) {
      audio.srcObject = null
      audio.remove()
      remoteAudioEls.delete(peerId)
    }
    const next = { ...remoteStreams.value }
    delete next[peerId]
    remoteStreams.value = next
  }

  function removeRemoteAudios() {
    Array.from(remoteAudioEls.keys()).forEach(removeRemoteAudio)
  }

  function avatarFor(participant) {
    return participant?.user?.avatarUrl || DEFAULT_AVATAR
  }

  function nameFor(participant) {
    return participant?.user?.nickname || '空席位'
  }

  function isParticipantConnected(participant) {
    const userId = Number(participant?.user?.id)
    if (!Number.isFinite(userId)) {
      return false
    }
    if (userId === selfUserId()) {
      return Boolean(localStream.value)
    }
    if (remoteStreams.value[userId]) {
      return true
    }
    const record = peerConnections.get(userId)
    if (!record) {
      return false
    }
    const pcState = record.pc.connectionState
    const iceState = record.pc.iceConnectionState
    return pcState === 'connected' || iceState === 'connected' || iceState === 'completed'
  }

  function scheduleConnectPeers() {
    clearConnectTimers()
    CONNECT_RETRY_DELAYS.forEach((delay) => {
      const timer = setTimeout(() => {
        connectTimers.delete(timer)
        void connectPeers().catch(handlePeerConnectionError)
      }, delay)
      connectTimers.add(timer)
    })
  }

  function clearConnectTimers() {
    connectTimers.forEach((timer) => clearTimeout(timer))
    connectTimers.clear()
  }

  function isSignalForCurrentConnection(payload) {
    if (Number(payload.roomVersion) !== Number(roomVersion.value)) return false
    if (String(payload.targetConnectionId || '') !== currentConnectionId.value) return false
    const from = participantByUserId(payload.fromUserId)
    return String(payload.fromConnectionId || '') === String(from?.connectionId || '')
  }

  function shouldInitiateOffer(participant) {
    const peerConnectionId = String(participant?.connectionId || '')
    if (!currentConnectionId.value || !peerConnectionId) return false
    if (currentConnectionId.value !== peerConnectionId) {
      return currentConnectionId.value < peerConnectionId
    }
    return selfUserId() < Number(participant?.user?.id)
  }

  function isPolitePeer(peerConnectionId) {
    if (!currentConnectionId.value || !peerConnectionId) return true
    return currentConnectionId.value > peerConnectionId
  }

  function participantByUserId(userId) {
    const id = Number(userId)
    return participants.value.find((participant) => Number(participant?.user?.id) === id) || null
  }

  function selfUserId() {
    return Number(userStore.id)
  }

  function canEnterVoice(kind, id) {
    if (!active.value || !joined.value) return true
    const currentKey = currentVoiceKey.value
    const nextKey = kind === 'private' ? privateSessionKeyByPeer(id) : groupSessionKey(id)
    if (!currentKey || currentKey === nextKey) return true
    ElMessage.warning('请先退出当前语音聊天')
    return false
  }

  async function loadIceConfig() {
    if (iceConfigLoaded) return
    try {
      const res = await getVoiceIceConfig()
      const servers = Array.isArray(res?.data?.iceServers) ? res.data.iceServers : []
      if (servers.length > 0) {
        iceConfig.value = { iceServers: servers }
      }
    } catch {
      iceConfig.value = DEFAULT_RTC_CONFIG
    } finally {
      iceConfigLoaded = true
    }
  }

  function startVolumeMeter(stream) {
    stopVolumeMeter()
    const AudioContextCtor = window.AudioContext || window.webkitAudioContext
    if (!AudioContextCtor) return
    audioContext = new AudioContextCtor()
    const source = audioContext.createMediaStreamSource(stream)
    analyser = audioContext.createAnalyser()
    analyser.fftSize = 256
    source.connect(analyser)
    const data = new Uint8Array(analyser.frequencyBinCount)
    const tick = () => {
      if (!analyser) return
      analyser.getByteFrequencyData(data)
      const sum = data.reduce((total, value) => total + value, 0)
      volumeLevel.value = Math.min(1, sum / data.length / 96)
      volumeFrameId = requestAnimationFrame(tick)
    }
    tick()
  }

  function stopVolumeMeter() {
    if (volumeFrameId) {
      cancelAnimationFrame(volumeFrameId)
      volumeFrameId = 0
    }
    analyser = null
    if (audioContext) {
      audioContext.close().catch(() => {})
      audioContext = null
    }
    volumeLevel.value = 0
  }

  async function showDialogNextFrame() {
    await new Promise((resolve) => requestAnimationFrame(resolve))
    dialogVisible.value = true
  }

  function refreshConnectionStatus(expectedPeers = null) {
    if (!active.value || !joined.value) {
      connectionStatus.value = 'idle'
      connectionMessage.value = ''
      resetSignalStats()
      return
    }
    const stats = { connected: 0, connecting: 0, failed: 0, dropped: signalStats.value.dropped }
    peerConnections.forEach((record) => {
      const pcState = record.pc.connectionState
      const iceState = record.pc.iceConnectionState
      if (pcState === 'connected' || iceState === 'connected' || iceState === 'completed') {
        stats.connected += 1
      } else if (pcState === 'failed' || iceState === 'failed') {
        stats.failed += 1
      } else {
        stats.connecting += 1
      }
    })
    signalStats.value = stats
    const peers = expectedPeers == null ? Math.max(0, participants.value.length - 1) : expectedPeers
    if (stats.failed > 0 && stats.connected === 0) {
      connectionStatus.value = 'failed'
    } else if (stats.connected > 0) {
      connectionStatus.value = 'connected'
    } else if (peers === 0) {
      connectionStatus.value = 'ready'
    } else {
      connectionStatus.value = 'connecting'
    }
    connectionMessage.value = ''
  }

  function resetSignalStats() {
    signalStats.value = {
      connected: 0,
      connecting: 0,
      failed: 0,
      dropped: 0,
    }
  }

  function incrementSignalStat(key) {
    signalStats.value = {
      ...signalStats.value,
      [key]: Number(signalStats.value[key]) + 1,
    }
  }

  function createPeerConnection() {
    const PeerConnectionCtor = resolveNativeConstructor([
      'RTCPeerConnection',
      'webkitRTCPeerConnection',
      'mozRTCPeerConnection',
    ])
    if (!PeerConnectionCtor) {
      throw webRtcUnavailableError()
    }
    try {
      return new PeerConnectionCtor(iceConfig.value || DEFAULT_RTC_CONFIG)
    } catch (error) {
      try {
        return new PeerConnectionCtor()
      } catch (fallbackError) {
        throw webRtcCreateError(error, fallbackError)
      }
    }
  }

  function resolveNativeConstructor(names) {
    const root = typeof window !== 'undefined' ? window : globalThis
    for (const name of names) {
      const candidate = root?.[name] || globalThis?.[name]
      if (typeof candidate === 'function') return candidate
    }
    return null
  }

  function webRtcUnavailableError() {
    const error = new Error('当前浏览器没有开放 WebRTC 能力，请检查浏览器版本、站点是否为 localhost/HTTPS，以及是否被浏览器策略禁用')
    error.name = 'WebRtcUnsupportedError'
    return error
  }

  function webRtcCreateError(primaryError, fallbackError) {
    const detail = fallbackError?.message || primaryError?.message || ''
    const message = detail
      ? `浏览器创建 WebRTC 连接失败：${detail}`
      : '浏览器创建 WebRTC 连接失败，请检查 Edge 的 WebRTC/隐私策略设置'
    const error = new Error(message)
    error.name = 'WebRtcCreateError'
    return error
  }

  function handlePeerConnectionError(error) {
    if (error?.name === 'WebRtcUnsupportedError' || error?.name === 'WebRtcCreateError') {
      if (!webRtcUnsupportedShown) {
        webRtcUnsupportedShown = true
        ElMessage.error(error.message)
      }
      connectionStatus.value = 'failed'
      connectionMessage.value = error.message
      return
    }
    connectionStatus.value = 'failed'
    connectionMessage.value = '语音连接失败，请退出后重新加入'
  }

  function persistIfJoined(next) {
    if (next?.currentUserJoined) {
      persistJoinedSession(next)
      return
    }
    const persisted = readPersistedSession()
    if (persisted && persisted.key === sessionKeyFor(next)) {
      clearPersistedSession()
    }
  }

  function persistJoinedSession(next) {
    if (!next?.voiceKind) return
    const data = {
      voiceKind: next.voiceKind,
      groupId: next.groupId || null,
      peerUserId: next.peerUserId || null,
      key: sessionKeyFor(next),
    }
    try {
      localStorage.setItem(VOICE_SESSION_STORAGE_KEY, JSON.stringify(data))
      localStorage.removeItem(LEGACY_GROUP_STORAGE_KEY)
    } catch {
      /* ignore */
    }
  }

  function readPersistedSession() {
    try {
      const raw = localStorage.getItem(VOICE_SESSION_STORAGE_KEY)
      if (raw) {
        const data = JSON.parse(raw)
        if (data?.voiceKind === 'private' && data.peerUserId) return data
        if (data?.voiceKind === 'group' && data.groupId) return data
      }
      const legacyGroupId = Number(localStorage.getItem(LEGACY_GROUP_STORAGE_KEY))
      if (Number.isFinite(legacyGroupId) && legacyGroupId > 0) {
        return {
          voiceKind: 'group',
          groupId: legacyGroupId,
          key: groupSessionKey(legacyGroupId),
        }
      }
      return null
    } catch {
      return null
    }
  }

  function clearPersistedSession() {
    try {
      localStorage.removeItem(VOICE_SESSION_STORAGE_KEY)
      localStorage.removeItem(LEGACY_GROUP_STORAGE_KEY)
    } catch {
      /* ignore */
    }
  }

  function sessionKeyFor(next) {
    if (!next) return ''
    if (next.voiceKind === 'private') {
      return privateSessionKeyByPeer(next.peerUserId)
    }
    if (next.voiceKind === 'group') {
      return groupSessionKey(next.groupId)
    }
    return ''
  }

  function groupSessionKey(groupId) {
    return groupId ? `group:${groupId}` : ''
  }

  function privateSessionKeyByPeer(peerUserId) {
    return peerUserId ? `private-peer:${peerUserId}` : ''
  }

  return {
    acceptPrivate,
    active,
    avatarFor,
    connectionStatus,
    connectionStatusText,
    currentGroupId,
    currentPeerUserId,
    deafened,
    dialogTitle,
    dialogVisible,
    fetchPrivateSession,
    fetchSession,
    floatPosition,
    floatTitle,
    join,
    joined,
    joining,
    leave,
    maxSeats,
    memberCount,
    muted,
    nameFor,
    isParticipantConnected,
    onVoiceSignal,
    onVoiceStatus,
    openVoiceDialog,
    outputDeviceId,
    outputDevices,
    participants,
    privateSessionFor,
    remoteAudioBlocked,
    remotePeerCount,
    session,
    sessionFor,
    signalStats,
    restorePersistedSession,
    setOutputDevice,
    start,
    startPrivate,
    declinePrivate,
    toggleDeafened,
    toggleMuted,
    unlockRemoteAudio,
    voiceKind,
    volumeLevel,
  }
})
