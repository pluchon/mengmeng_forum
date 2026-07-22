<template>
  <article
    class="search-user-row"
    role="button"
    tabindex="0"
    @click="emitOpen"
    @keydown.enter.prevent="emitOpen"
  >
    <div class="search-user-row__avatar">
      <UserAvatarVip
        :size="52"
        :src="user.avatarUrl || defaultAvatar"
        :vip-tier="Number(user.vipTier) || 0"
        :vip-expire-at="user.vipExpireAt"
      />
    </div>
    <strong class="search-user-row__name">{{ user.nickname || `用户 ${user.id}` }}</strong>
    <div class="search-user-row__stats">
      <span>关注数：{{ user.followingCount ?? 0 }}</span>
      <span>粉丝数：{{ user.followerCount ?? 0 }}</span>
    </div>
    <el-button
      v-if="!isSelf"
      class="search-user-row__follow"
      :class="{ 'is-following': user.isFollowing }"
      :loading="saving"
      :disabled="saving"
      round
      @click.stop="emitToggleFollow"
    >
      {{ user.isFollowing ? '已关注' : '关注' }}
    </el-button>
    <span v-else class="search-user-row__self">本人</span>
  </article>
</template>

<script setup src="./SearchUserRow.js"></script>
<style scoped lang="scss" src="./SearchUserRow.scss"></style>
