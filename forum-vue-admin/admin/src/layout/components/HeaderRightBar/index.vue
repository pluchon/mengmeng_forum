<!--
  @file HeaderRightBar 组件
  @description 头部右侧工具栏：项目配置、全屏、主题与管理员菜单
-->
<template>
  <a-row justify="end" align="center" class="header-right-bar">
    <a-space size="medium">
      <!-- 项目配置按钮 -->
      <a-tooltip content="项目配置" position="bl">
        <a-button size="mini" class="g-hover-btn" @click="handleOpenSettings">
          <template #icon>
            <icon-settings :size="18" />
          </template>
        </a-button>
      </a-tooltip>

      <!-- 全屏切换按钮 -->
      <a-tooltip v-if="!['xs', 'sm'].includes(breakpoint)" content="全屏切换" position="bottom">
        <a-button size="mini" class="g-hover-btn" @click="toggle">
          <template #icon>
            <icon-fullscreen v-if="!isFullscreen" :size="18" />
            <icon-fullscreen-exit v-else :size="18" />
          </template>
        </a-button>
      </a-tooltip>

      <!-- 暗黑模式切换 -->
      <a-tooltip content="主题切换" position="bottom">
        <GiThemeBtn></GiThemeBtn>
      </a-tooltip>

      <!-- 管理员账户 -->
      <a-dropdown trigger="hover">
        <a-row align="center" :wrap="false" class="header-right-bar__user">
          <!-- 管理员头像 -->
          <a-avatar :size="32">
            <img :src="userStore.avatar" />
          </a-avatar>
          <span class="header-right-bar__username">{{ userStore.name }}</span>
          <icon-down />
        </a-row>

        <template #content>
          <template v-if="USER_MENUS.length">
            <a-doption v-for="item in USER_MENUS" :key="item.key" @click="item.onClick">
              <template #icon>
                <GiIconBox :color="item.iconColor">
                  <component :is="item.icon" />
                </GiIconBox>
              </template>
              <span>{{ item.label }}</span>
            </a-doption>
            <a-divider :margin="0" />
          </template>
          <a-doption @click="handleLogout">
            <template #icon>
              <GiIconBox color="warning">
                <icon-export />
              </GiIconBox>
            </template>
            <span>退出登录</span>
          </a-doption>
        </template>
      </a-dropdown>
    </a-space>
  </a-row>
</template>

<script setup lang="ts">
import { Drawer, Message, Modal } from '@arco-design/web-vue'
import { useFullscreen } from '@vueuse/core'
import { useBreakpoint } from '@/hooks'
import { useUserStore } from '@/stores'
import SettingDrawerPanel from './SettingDrawerPanel.vue'

/** 组件名称 */
defineOptions({ name: 'HeaderRight' })

/** 路由实例 */
const router = useRouter()

/** 状态管理 */
const userStore = useUserStore()

/** 响应式断点 */
const { breakpoint } = useBreakpoint()

/** 全屏控制 */
const { isFullscreen, toggle } = useFullscreen()

/** 用户菜单配置 */
const USER_MENUS: Array<{
  key: string
  label: string
  icon: string
  iconColor: string
  onClick: () => void
}> = []

/** 打开设置抽屉 */
const handleOpenSettings = () => {
  Drawer.open({
    title: '项目配置',
    width: 300,
    footer: false,
    content: () => h(SettingDrawerPanel)
  })
}

/** 处理退出登录 */
const handleLogout = () => {
  Modal.warning({
    title: '提示',
    content: '确认退出登录？',
    hideCancel: false,
    closable: true,
    onBeforeOk: async () => {
      try {
        await userStore.logout()
        router.replace('/login')
        return true
      } catch {
        Message.error('退出登录失败')
        return false
      }
    }
  })
}
</script>

<style lang="scss" scoped>
.arco-dropdown-open .arco-icon-down {
  transform: rotate(180deg);
}

.header-right-bar {
  &__user {
    color: var(--color-text-1);
    cursor: pointer;
  }

  &__username {
    margin-left: 10px;
    white-space: nowrap;

    .arco-icon-down {
      margin-left: 2px;
      transition: all 0.3s;
    }
  }
}
</style>
