<template>
  <div class="red-profile-page shell-page-scroll">
    <div class="profile-banner" :style="{ backgroundImage: bgStyle }">
      <div v-if="isMe" class="banner-upload-overlay" @click="triggerBgUpload">
        <el-icon :size="24"><Camera /></el-icon>
        <span>更换背景</span>
      </div>
    </div>

    <div class="profile-container">
      <div v-if="loading && !userInfo" class="loading-state">
        <el-skeleton animated :rows="5" />
      </div>

      <template v-else>
        <section class="user-info-section animate-fade-up">
          <div class="avatar-wrap">
            <UserAvatarVip
              :key="avatarSrc"
              :src="avatarSrc"
              :size="100"
              :vip-tier="displayVipTier"
              :vip-expire-at="displayVipExpireAt"
            />
          </div>
          <div class="info-content">
            <div class="name-row">
              <div class="nickname-with-badge">
                <h1 class="nickname">{{ userInfo?.nickname || '匿名用户' }}</h1>
                <img
                  v-if="showVipBadge"
                  :src="vipCrownSrc"
                  class="profile-vip-crown"
                  width="28"
                  height="28"
                  alt="VIP"
                />
              </div>
              <div v-if="isMe" class="action-btns">
                <el-button round class="profile-edit-btn" @click="$router.push('/settings')">编辑资料</el-button>
              </div>
              <div v-else class="action-btns">
                <el-button round @click="handleChat">私信</el-button>
              </div>
            </div>

            <div class="id-row">ID: {{ userInfo?.id || '---' }}</div>

            <p class="bio">{{ userInfo?.remark || '还没有填写个人简介哦' }}</p>

            <div class="stat-item">
              <span class="val">{{ total }}</span>
              <span class="lab">发帖数</span>
            </div>
          </div>
        </section>

        <div class="profile-tabs-wrap">
          <el-tabs v-model="activeTab" class="red-profile-tabs">
            <el-tab-pane label="笔记" name="notes">
              <div class="profile-content">
                <el-row :gutter="20">
                  <el-col v-for="item in articles" :key="item.id" :xs="12" :sm="8" :md="6">
                    <el-card
                      class="note-card animate-fade-up"
                      :body-style="{ padding: '0px' }"
                      shadow="hover"
                      @click="$router.push(`/article/${item.id}`)"
                    >
                      <div class="note-cover" :style="coverStyle(item)">
                        <span v-if="!item.coverImg" class="cover-text">{{ item.title?.substring(0, 1) }}</span>
                        <div v-if="Number(item.mediaType) === 1" class="note-cover-play" aria-hidden="true" />
                      </div>
                      <div class="note-info">
                        <h3 class="note-title">{{ item.title }}</h3>
                        <div class="note-meta">
                          <el-icon><Star /></el-icon>
                          <span>{{ item.likeCount || 0 }}</span>
                        </div>
                      </div>
                    </el-card>
                  </el-col>
                </el-row>
                <el-empty v-if="articles.length === 0" description="还没有发布过笔记哦" />
              </div>
            </el-tab-pane>

            <el-tab-pane label="收藏" name="collect">
              <div class="profile-content profile-fav-list" v-loading="loadingFavorites">
                <el-card
                  v-for="f in favoriteFolders"
                  :key="f.id"
                  class="profile-fav-folder-card animate-fade-up"
                  shadow="never"
                  :body-style="{ padding: '14px 16px' }"
                >
                  <div class="profile-fav-folder-row">
                    <div>
                      <div class="profile-fav-folder-name">{{ f.name }}</div>
                      <div class="profile-fav-folder-meta">
                        {{ Number(f.isPublic) === 1 ? '公开' : '私密' }} · {{ f.itemCount ?? 0 }} 条
                      </div>
                    </div>
                    <button type="button" class="profile-fav-view-btn" @click="openFavoriteDialog(f)">查看</button>
                  </div>
                </el-card>

                <el-empty v-if="!loadingFavorites && favoriteFolders.length === 0" description="暂无收藏" />
              </div>
            </el-tab-pane>

            <el-tab-pane label="点赞" name="liked">
              <div class="profile-content">
                <el-row :gutter="20">
                  <el-col
                    v-for="item in likedArticles"
                    :key="item.article?.id || item.id"
                    :xs="12"
                    :sm="8"
                    :md="6"
                  >
                    <el-card
                      class="note-card note-card--outlined animate-fade-up"
                      :body-style="{ padding: '0px' }"
                      shadow="never"
                      @click="$router.push(`/article/${item.article?.id || item.id}`)"
                    >
                      <div class="note-cover" :style="coverStyle(item.article || item)">
                        <span
                          v-if="!(item.article || item).coverImg"
                          class="cover-text"
                        >{{ (item.article?.title || item.title)?.substring(0, 1) }}</span>
                        <div v-if="Number((item.article || item).mediaType) === 1" class="note-cover-play" aria-hidden="true" />
                      </div>
                      <div class="note-info">
                        <h3 class="note-title">{{ item.article?.title || item.title }}</h3>
                        <div class="note-meta">
                          <el-icon><Star /></el-icon>
                          <span>{{ item.article?.likeCount || item.likeCount || 0 }}</span>
                        </div>
                      </div>
                    </el-card>
                  </el-col>
                </el-row>
                <el-empty v-if="likedArticles.length === 0" description="还没有点赞过帖子哦" />
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>
      </template>
    </div>

    <input
      ref="bgFileInput"
      type="file"
      accept="image/*"
      style="display: none"
      @change="handleBgUpload"
    >

    <el-dialog
      v-model="favoriteDialogVisible"
      class="profile-fav-dialog"
      width="min(920px, 94vw)"
      align-center
      destroy-on-close
      :title="favoriteDialogTitle"
    >
      <div v-loading="favoriteDialogLoading" class="profile-fav-dialog-body">
        <div
          v-for="row in favoriteDialogItems"
          :key="row.article?.id"
          class="profile-fav-item-row"
          role="button"
          tabindex="0"
          @click="openArticleFromFavorite(row)"
          @keydown.enter.prevent="openArticleFromFavorite(row)"
        >
          <div class="profile-fav-item-cover" :style="favoriteCoverStyle(row.article)">
            <img v-if="row.article?.coverImg" :src="row.article.coverImg" alt="">
          </div>
          <div class="profile-fav-item-main">
            <div class="profile-fav-item-title">{{ row.article?.title }}</div>
            <div class="profile-fav-item-stats">
              <span>赞 {{ row.article?.likeCount ?? 0 }}</span>
              <span>评 {{ row.article?.replyCount ?? 0 }}</span>
              <span>藏 {{ row.article?.favoriteCount ?? 0 }}</span>
            </div>
            <div class="profile-fav-item-snippet">{{ favoriteSnippet(row.article) }}</div>
          </div>
        </div>
        <el-empty v-if="!favoriteDialogLoading && !favoriteDialogItems.length" description="暂无收藏帖子" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import { useProfile } from '@scripts/views/Profile'
import vipCrownUrl from '@/assets/svg/VIP.svg?url'

const vipCrownSrc = vipCrownUrl

const {
  Camera,
  Star,
  activeTab,
  articles,
  avatarSrc,
  bgFileInput,
  bgStyle,
  coverStyle,
  handleBgUpload,
  handleChat,
  isMe,
  likedArticles,
  favoriteCoverStyle,
  favoriteDialogItems,
  favoriteDialogLoading,
  favoriteDialogTitle,
  favoriteDialogVisible,
  favoriteFolders,
  favoriteSnippet,
  loadingFavorites,
  loading,
  openArticleFromFavorite,
  openFavoriteDialog,
  total,
  triggerBgUpload,
  userInfo,
  displayVipTier,
  displayVipExpireAt,
  showVipBadge,
} = useProfile()
</script>

<style scoped src="@/assets/styles/user.css"></style>
