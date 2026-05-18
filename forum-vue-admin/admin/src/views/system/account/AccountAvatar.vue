<template>
  <section class="account-avatar">
    <a-avatar :size="60">
      <img :src="userStore.avatar" alt="">
    </a-avatar>
    <section class="account-avatar__username">{{ userStore.name }}</section>
    <p class="account-avatar__id">用户 ID：{{ userStore.userInfo.id || '—' }}</p>
    <div v-if="userStore.roles.length" class="account-avatar__roles">
      <a-tag v-for="r in userStore.roles" :key="r" color="arcoblue" size="small">{{ r }}</a-tag>
    </div>
    <p class="account-avatar__hint">头像与昵称与论坛账号一致；修改资料请在用户端个人中心操作。</p>
  </section>
</template>

<script setup lang="ts">
import { useUserStore } from '@/stores'

const userStore = useUserStore()
</script>

<style scoped lang="scss">
.account-avatar {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: fit-content;
  min-height: 204px;
  padding: 20px 16px 24px;
  color: var(--color-white);
  text-align: center;
  background-color: rgb(var(--primary-6));

  &__username {
    margin: 12px 0 6px;
    font-size: 16px;
    font-weight: 600;
  }

  &__id {
    margin: 0 0 10px;
    font-size: 12px;
    opacity: 0.92;
  }

  &__roles {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    justify-content: center;
    margin-bottom: 12px;
  }

  &__hint {
    max-width: 260px;
    margin: 0;
    font-size: 12px;
    line-height: 1.5;
    opacity: 0.88;
  }
}
</style>
