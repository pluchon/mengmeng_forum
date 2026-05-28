<template>
  <a-layout class="layout-default">
    <Asider></Asider>
    <a-layout class="layout-default__right">
      <Header></Header>
      <Tabs v-if="appStore.tabVisible"></Tabs>
      <Main></Main>
      <SiteIcpBar />
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { useAppStore } from '@/stores'
import SiteIcpBar from '@/components/SiteIcpBar.vue'
import Asider from './components/Asider/index.vue'
import Header from './components/Header/index.vue'
import Main from './components/Main.vue'
import Tabs from './components/Tabs/index.vue'

defineOptions({ name: 'LayoutDefault' })

const appStore = useAppStore()
</script>

<style lang="scss" scoped>
.layout-default {
  flex-direction: row;
  height: 100%;

  &__right {
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }
}
</style>
