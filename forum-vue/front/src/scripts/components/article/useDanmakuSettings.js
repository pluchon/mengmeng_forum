import { ref } from 'vue'
import {
  DANMAKU_AREA_OPTIONS,
  DANMAKU_DENSITY_OPTIONS,
  DANMAKU_DEFAULT_SETTINGS,
  DANMAKU_TYPE_FILTER_OPTIONS,
  loadDanmakuSettings,
  saveDanmakuSettings,
} from '@/constants/danmaku'

export function useDanmakuSettings() {
  const settings = ref(loadDanmakuSettings())
  const settingsOpen = ref(false)

  function toggleSettings() {
    settingsOpen.value = !settingsOpen.value
  }

  function closeSettings() {
    settingsOpen.value = false
  }

  function updateSettings(patch) {
    settings.value = saveDanmakuSettings({ ...settings.value, ...patch })
  }

  function setEnabled(enabled) {
    updateSettings({ enabled: !!enabled })
  }

  function setOpacity(opacity) {
    updateSettings({ opacity })
  }

  function setAreaPercent(areaPercent) {
    updateSettings({ areaPercent })
  }

  function setDensity(density) {
    updateSettings({ density })
  }

  function setTypeFilter(key, value) {
    updateSettings({ [key]: !!value })
  }

  function setColoredOnly(coloredOnly) {
    updateSettings({ coloredOnly: !!coloredOnly })
  }

  return {
    DANMAKU_AREA_OPTIONS,
    DANMAKU_DENSITY_OPTIONS,
    DANMAKU_DEFAULT_SETTINGS,
    DANMAKU_TYPE_FILTER_OPTIONS,
    closeSettings,
    setAreaPercent,
    setColoredOnly,
    setDensity,
    setEnabled,
    setOpacity,
    setTypeFilter,
    settings,
    settingsOpen,
    toggleSettings,
    updateSettings,
  }
}
