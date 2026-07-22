<template>
  <el-card
    class="note-card note-card--masonry animate-fade-up"
    :class="{ 'note-card--question': isQuestion }"
    :body-style="{ padding: '0px' }"
    shadow="hover"
    @click="emitOpen"
  >
    <div class="note-cover note-cover--fluid">
      <img
        v-if="coverImageUrl"
        class="note-cover-img"
        :src="coverImageUrl"
        :alt="article.title || ''"
        loading="lazy"
      >
      <div
        v-else
        class="note-cover-placeholder"
        :class="{ 'note-cover-placeholder--video': Number(article.mediaType) === 1 }"
        :style="placeholderStyle"
      >
        <span class="cover-title">{{ shortTitle }}</span>
      </div>
      <div v-if="Number(article.mediaType) === 1" class="note-cover-play" aria-hidden="true" />
    </div>
    <div
      class="note-info"
      :class="{
        'note-info--question': isQuestion,
        'note-info--resolved': isQuestion && Number(article.questionStatus) === 1,
      }"
    >
      <div v-if="isQuestion" class="question-card-meta">
        <span class="question-card-status" :class="questionStatusClass(article.questionStatus)">
          <span class="question-card-status__dot" />
          {{ questionStatusLabel(article.questionStatus) }}
        </span>
      </div>
      <h3 class="note-title">{{ article.title }}</h3>
      <div class="note-footer">
        <div class="author">
          <UserAvatarVip
            :size="22"
            :src="entry.user?.avatarUrl || defaultAvatar"
            :vip-tier="Number(entry.user?.vipTier) || 0"
            :vip-expire-at="entry.user?.vipExpireAt"
          />
          <span class="nickname" :title="entry.user?.nickname">{{ displayNickname }}</span>
        </div>
        <div v-if="isQuestion" class="question-answer-count">
          <span>{{ article.replyCount || 0 }}回答</span>
        </div>
        <div v-else class="likes">
          <LikeCountIcon />
          <span>{{ article.likeCount || 0 }}</span>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup src="./SearchArticleCard.js"></script>
<style scoped lang="scss" src="./SearchArticleCard.scss"></style>
