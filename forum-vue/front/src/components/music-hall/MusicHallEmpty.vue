<template>
  <div class="music-hall-empty" :class="{ 'music-hall-empty--compact': compact }">
    <img :src="emptyMusicUrl" alt="">
    <p>{{ text }}</p>
  </div>
</template>

<script src="./MusicHallEmpty.js"></script>
<style lang="scss" src="./MusicHallEmpty.scss"></style>
