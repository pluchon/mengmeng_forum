<template>
  <div class="red-profile-page shell-page-scroll">
    <div class="profile-banner" :style="{ backgroundImage: bgStyle }">
      <div v-if="isMe" class="banner-upload-overlay">
        <button type="button" class="banner-upload-btn" @click.stop="triggerBgUpload">
          <el-icon :size="20"><Camera /></el-icon>
          <span>更换背景</span>
        </button>
        <button
          v-if="hasBannerImage"
          type="button"
          class="banner-upload-btn"
          @click.stop="openBannerPreview"
        >
          <el-icon :size="20"><ZoomIn /></el-icon>
          <span>查看背景</span>
        </button>
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
              :size="100"            />
            <span class="profile-avatar-id">ID: {{ userInfo?.id || '---' }}</span>
          </div>
          <div class="info-content">
            <div class="name-row">
              <div class="name-meta-row">
                <h1 class="nickname">{{ userInfo?.nickname || '匿名用户' }}</h1>
                <div class="follow-stats-row">
                  <button type="button" class="follow-stat follow-stat-btn" @click="openFollowingList">
                    <strong>{{ followingCount }}</strong> 关注
                  </button>
                  <span class="follow-stat-divider" aria-hidden="true">|</span>
                  <button type="button" class="follow-stat follow-stat-btn" @click="openFollowersList">
                    <strong>{{ followerCount }}</strong> 粉丝
                  </button>
                </div>
              </div>
              <div class="name-row-right">
                <IpRegionLabel :region="profileIpRegion" />
                <div v-if="!isMe" class="action-btns">
                  <el-button
                    class="profile-follow-btn"
                    :class="{ 'is-following': isFollowing }"
                    :loading="followSaving"
                    @click="toggleFollow"
                  >
                    {{ isFollowing ? '已关注' : '关注' }}
                  </el-button>
                  <el-button class="profile-chat-btn" @click="handleChat">私信</el-button>
                </div>
              </div>
            </div>

            <p class="bio">个人简介：{{ userInfo?.remark || '还没有填写个人简介哦' }}</p>
          </div>
        </section>

        <div class="profile-tabs-wrap">
          <div class="profile-tabs-bar">
            <nav class="profile-tabs-nav" aria-label="个人主页内容分类">
              <button
                type="button"
                class="profile-tab-btn"
                :class="{ 'is-active': activeTab === 'notes' }"
                @click="selectProfileTab('notes')"
              >
                笔记
              </button>
              <button
                type="button"
                class="profile-tab-btn"
                :class="{ 'is-active': activeTab === 'collect' }"
                @click="selectProfileTab('collect')"
              >
                收藏
              </button>
              <button
                type="button"
                class="profile-tab-btn"
                :class="{ 'is-active': activeTab === 'liked' }"
                @click="selectProfileTab('liked')"
              >
                点赞
              </button>
              <button
                type="button"
                class="profile-tab-btn"
                :class="{ 'is-active': activeTab === 'groups' }"
                @click="selectProfileTab('groups')"
              >
                群聊
              </button>
              <button
                v-if="isMe"
                type="button"
                class="profile-tab-btn"
                :class="{ 'is-active': activeTab === 'not-interested' }"
                @click="selectProfileTab('not-interested')"
              >
                管理不感兴趣的帖子
              </button>
            </nav>
            <div v-if="activeTab === 'notes'" class="profile-tab-post-count">
              <span class="profile-tab-post-count-val">{{ total }}</span>
              <span class="profile-tab-post-count-lab">发帖数</span>
            </div>
            <div v-if="activeTab === 'liked'" class="profile-tab-post-count">
              <span class="profile-tab-post-count-val">{{ likedTotal }}</span>
              <span class="profile-tab-post-count-lab">点赞帖子</span>
            </div>
            <div v-if="activeTab === 'not-interested'" class="profile-tab-post-count">
              <span class="profile-tab-post-count-val">{{ notInterestedTotal }}</span>
              <span class="profile-tab-post-count-lab">不感兴趣帖子数</span>
            </div>
            <button
              v-if="activeTab === 'collect' && isMe"
              type="button"
              class="profile-fav-create-btn"
              @click="openCreateFavoriteFolder"
            >
              <el-icon><Plus /></el-icon>
              新建
            </button>
          </div>

          <div v-show="activeTab === 'notes'" class="profile-content profile-content--paged">
            <div class="profile-content__body">
              <el-row :gutter="20">
                <el-col v-for="item in articles" :key="item.id" :xs="12" :sm="8" :md="6">
                  <el-card
                    class="note-card animate-fade-up"
                    :body-style="{ padding: '0px' }"
                    shadow="hover"
                    @click="openArticleFromNotes(item)"
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
            <div class="profile-pager">
              <AppPagination
                v-model:current-page="notesPageNum"
                :total="notesTotal"
                :page-size="PROFILE_PAGE_SIZE"
                :disabled="loading"
                @current-change="loadProfile"
              />
            </div>
          </div>

          <div v-if="activeTab === 'collect'" class="profile-content profile-fav-list profile-content--paged" v-loading="loadingFavorites">
            <div class="profile-content__body">
              <el-alert
                v-if="favoriteFolderError"
                :title="favoriteFolderError"
                type="error"
                :closable="false"
                show-icon
              />
              <el-card
                v-for="f in favoriteFolders"
                :key="f.id"
                class="profile-fav-folder-card animate-fade-up"
                :class="{ 'profile-fav-folder-card--private': Number(f.isPublic) !== 1 }"
                shadow="never"
                :body-style="{ padding: '14px 16px' }"
              >
                <div class="profile-fav-folder-row">
                  <button
                    v-if="isMe"
                    type="button"
                    class="profile-fav-folder-cover"
                    :class="{ 'is-uploading': Number(favoriteCoverUploadingId) === Number(f.id) }"
                    :aria-label="f.coverUrl ? '更换收藏夹封面' : '上传收藏夹封面'"
                    @click.stop="triggerFavoriteCoverUpload(f)"
                  >
                    <img v-if="f.coverUrl" :src="f.coverUrl" alt="">
                    <span v-else class="profile-fav-folder-cover-letter">{{ favoriteFolderInitial(f) }}</span>
                    <span class="profile-fav-folder-cover-overlay">
                      <el-icon><Camera /></el-icon>
                      {{ Number(favoriteCoverUploadingId) === Number(f.id) ? '上传中' : (f.coverUrl ? '更换封面' : '上传封面') }}
                    </span>
                  </button>
                  <div v-else class="profile-fav-folder-cover profile-fav-folder-cover--readonly">
                    <img v-if="f.coverUrl" :src="f.coverUrl" alt="">
                    <span v-else class="profile-fav-folder-cover-letter">{{ favoriteFolderInitial(f) }}</span>
                  </div>
                  <div class="profile-fav-folder-main">
                    <div class="profile-fav-folder-name">
                      {{ f.name }}
                    </div>
                    <div class="profile-fav-folder-meta">
                      {{ f.itemCount ?? 0 }} 条收藏
                    </div>
                  </div>
                  <div class="profile-fav-folder-actions">
                    <span
                      class="profile-fav-status-tag"
                      :class="Number(f.isPublic) === 1 ? 'is-public' : 'is-private'"
                    >
                      {{ Number(f.isPublic) === 1 ? '公开' : '私密' }}
                    </span>
                    <button type="button" class="profile-fav-view-btn" @click="openFavoriteDialog(f)">查看</button>
                  </div>
                </div>
              </el-card>

              <el-empty v-if="!loadingFavorites && favoriteFolders.length === 0" description="暂无收藏" />
            </div>
            <div class="profile-pager">
              <AppPagination
                v-model:current-page="favoriteFolderPageNum"
                :total="favoriteFolderTotal"
                :page-size="FAVORITE_FOLDER_PAGE_SIZE"
                :disabled="loadingFavorites"
                @current-change="loadFavoriteFolders"
              />
            </div>
          </div>

          <div v-if="activeTab === 'liked'" class="profile-content profile-content--paged">
            <div class="profile-content__body">
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
                    @click="openArticleFromLiked(item)"
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
            <div class="profile-pager">
              <AppPagination
                v-model:current-page="likedPageNum"
                :total="likedTotal"
                :page-size="PROFILE_PAGE_SIZE"
                @current-change="loadLikedArticles"
              />
            </div>
          </div>

          <div v-if="activeTab === 'groups'" class="profile-content profile-public-groups profile-content--paged" v-loading="publicGroupsLoading">
            <div class="profile-content__body">
              <div
                v-for="group in publicGroups"
                :key="group.id"
                class="profile-public-group-row"
                :class="{ 'is-clickable': isJoinedPublicGroup(group) }"
                :role="isJoinedPublicGroup(group) ? 'button' : undefined"
                :tabindex="isJoinedPublicGroup(group) ? 0 : undefined"
                @click="openPublicGroupCard(group)"
                @keydown.enter.prevent="openPublicGroupCard(group)"
              >
                <div class="profile-public-group-avatar">
                  <img v-if="group.avatarUrl" :src="group.avatarUrl" alt="">
                  <span v-else>{{ groupAvatarText(group) }}</span>
                </div>
                <div class="profile-public-group-main">
                  <div class="profile-public-group-name">{{ group.name }}</div>
                </div>
                <div class="profile-public-group-owner">
                  <UserAvatarVip
                    :size="36"
                    :src="group.ownerUser?.avatarUrl || defaultAvatar"                  />
                  <span>{{ group.ownerUser?.nickname || '群主' }}</span>
                </div>
                <span class="profile-public-group-count">{{ group.memberCount || 0 }}/{{ group.memberLimit || 0 }} 人</span>
                <button
                  type="button"
                  class="profile-public-group-join"
                  :class="{ 'is-joined': isJoinedPublicGroup(group), 'is-pending': isPendingPublicGroup(group) }"
                  :disabled="isPendingPublicGroup(group) || Number(joiningGroupId) === Number(group.id)"
                  @click.stop="applyJoinPublicGroup(group)"
                >
                  {{ isJoinedPublicGroup(group) ? '已加入' : (isPendingPublicGroup(group) || Number(joiningGroupId) === Number(group.id) ? '申请中' : '申请加群') }}
                </button>
              </div>
              <el-empty v-if="!publicGroupsLoading && publicGroups.length === 0" description="暂无公开群聊" />
            </div>
            <div class="profile-pager">
              <AppPagination
                v-model:current-page="publicGroupsPageNum"
                :total="publicGroupsTotal"
                :page-size="PUBLIC_GROUP_PAGE_SIZE"
                :disabled="publicGroupsLoading"
                @current-change="loadPublicGroups"
              />
            </div>
          </div>

          <div v-if="activeTab === 'not-interested' && isMe" class="profile-content profile-content--paged" v-loading="notInterestedLoading">
            <div class="profile-content__body">
              <el-row :gutter="20">
                <el-col
                  v-for="item in notInterestedArticles"
                  :key="item.article?.id"
                  :xs="12"
                  :sm="8"
                  :md="6"
                >
                  <el-card
                    class="note-card note-card--outlined animate-fade-up"
                    :body-style="{ padding: '0px' }"
                    shadow="never"
                    @click="openArticleFromNotInterested(item)"
                  >
                    <div class="note-cover" :style="coverStyle(item.article)">
                      <span v-if="!item.article?.coverImg" class="cover-text">{{ item.article?.title?.substring(0, 1) }}</span>
                      <div v-if="Number(item.article?.mediaType) === 1" class="note-cover-play" aria-hidden="true" />
                    </div>
                    <div class="note-info">
                      <h3 class="note-title">{{ item.article?.title }}</h3>
                      <div class="profile-not-interested-card-footer">
                        <span class="note-meta">
                          <el-icon><Star /></el-icon>
                          {{ item.article?.likeCount || 0 }}
                        </span>
                        <button
                          type="button"
                          class="profile-not-interested-restore"
                          :disabled="Number(notInterestedRestoringId) === Number(item.article?.id)"
                          @click.stop="restoreNotInterestedArticle(item)"
                        >
                          恢复兴趣
                        </button>
                      </div>
                    </div>
                  </el-card>
                </el-col>
              </el-row>
              <el-empty v-if="!notInterestedLoading && notInterestedArticles.length === 0" description="暂无不感兴趣帖子" />
            </div>
            <div class="profile-pager">
              <AppPagination
                v-model:current-page="notInterestedPageNum"
                :total="notInterestedTotal"
                :page-size="PROFILE_PAGE_SIZE"
                :disabled="notInterestedLoading"
                @current-change="loadNotInterestedArticles"
              />
            </div>
          </div>
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
    <input
      ref="favoriteCoverInputRef"
      type="file"
      accept="image/*"
      style="display: none"
      @change="handleFavoriteCoverFile"
    >

    <el-dialog
      v-model="favoriteDialogVisible"
      class="profile-fav-dialog"
      width="min(920px, 94vw)"
      align-center
      destroy-on-close
      :show-close="false"
    >
      <template #header>
        <div class="profile-fav-dialog-head">
          <div class="profile-fav-dialog-title-wrap">
            <template v-if="favoriteFolderRenaming && isMe">
              <el-input
                v-model="favoriteFolderRenameValue"
                maxlength="25"
                show-word-limit
                size="small"
                class="profile-fav-rename-input"
                @keyup.enter="confirmFavoriteFolderRename"
              />
              <button
                type="button"
                class="profile-fav-rename-save"
                :disabled="favoriteFolderRenameSaving"
                aria-label="保存收藏夹名称"
                @click="confirmFavoriteFolderRename"
              >
                ✓
              </button>
            </template>
            <template v-else>
              <span class="profile-fav-dialog-title">{{ favoriteDialogTitle }}</span>
              <button
                v-if="isMe"
                type="button"
                class="profile-fav-edit-btn"
                aria-label="编辑收藏夹名称"
                @click="startFavoriteFolderRename"
              >
                <el-icon><Edit /></el-icon>
              </button>
            </template>
          </div>
          <div class="profile-fav-dialog-head-actions">
            <button
              v-if="canDeleteActiveFavoriteFolder"
              type="button"
              class="profile-fav-delete-btn"
              aria-label="删除收藏夹"
              title="删除收藏夹"
              @click="deleteCurrentFavoriteFolder"
            >
              <Trash2 :size="18" />
            </button>
            <div v-if="isMe && favoriteDialogVisible" class="profile-fav-visibility">
              <span class="profile-fav-visibility-label">公开</span>
              <el-switch
                :model-value="favoriteFolderPublic === 1"
                :loading="favoriteVisibilitySaving"
                active-color="#ff5f8f"
                @change="toggleFavoriteFolderPublic"
              />
            </div>
            <button
              type="button"
              class="profile-fav-dialog-close"
              aria-label="关闭"
              @click="favoriteDialogVisible = false"
            >
              ×
            </button>
          </div>
        </div>
      </template>
      <div v-loading="favoriteDialogLoading" class="profile-fav-dialog-body profile-content--paged-dialog">
        <div class="profile-content__body profile-fav-dialog-list">
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
            <div class="profile-fav-item-snippet">{{ row.article?.content }}</div>
          </div>
          <div class="profile-fav-item-stats">
            <span title="点赞数" aria-label="点赞数">
              <ThumbsUp :size="16" />
              {{ row.article?.likeCount ?? 0 }}
            </span>
            <span title="评论数" aria-label="评论数">
              <MessageCircle :size="16" />
              {{ row.article?.replyCount ?? 0 }}
            </span>
            <span title="收藏数" aria-label="收藏数">
              <Bookmark :size="16" />
              {{ row.article?.favoriteCount ?? 0 }}
            </span>
          </div>
          <div class="profile-fav-item-side">
            <div class="profile-fav-item-author">
              <UserAvatarVip
                :size="40"
                :src="row.author?.avatarUrl || defaultAvatar"              />
              <span :title="row.author?.nickname || '匿名用户'">{{ displayAuthorNickname(row.author?.nickname) }}</span>
            </div>
          </div>
        </div>
        <div
          v-if="!favoriteDialogLoading && !favoriteDialogItems.length"
          class="profile-fav-empty"
        >
          <img :src="emptyFavoriteArticleUrl" alt="" class="profile-fav-empty__img" />
          <p class="profile-fav-empty__text">这个收藏夹还没有帖子</p>
        </div>
        </div>
        <div class="profile-pager profile-pager--dialog">
          <AppPagination
            v-model:current-page="favoriteDialogPageNum"
            size="small"
            :total="favoriteDialogTotal"
            :page-size="FAVORITE_DIALOG_PAGE_SIZE"
            :disabled="favoriteDialogLoading"
            @current-change="loadFavoriteDialogArticles"
          />
        </div>
      </div>
    </el-dialog>

    <el-dialog
      v-model="favoriteCreateVisible"
      width="420px"
      class="profile-fav-create-dialog"
      align-center
      destroy-on-close
      :show-close="false"
    >
      <template #header>
        <div class="profile-fav-create-title">新建收藏夹</div>
      </template>
      <el-form class="profile-fav-create-form" label-position="top">
        <div class="profile-fav-create-row">
          <el-form-item label="名称" class="profile-fav-create-name">
          <el-input
            v-model="favoriteCreateForm.name"
            maxlength="25"
            show-word-limit
            placeholder="输入收藏夹名称"
          />
          </el-form-item>
          <el-form-item label="可见性" class="profile-fav-create-visibility">
            <div class="profile-fav-visibility-segment" role="group" aria-label="收藏夹可见性">
              <button
                type="button"
                :class="{ 'is-active': Number(favoriteCreateForm.isPublic) === 0 }"
                @click="setFavoriteCreateVisibility(0)"
              >私密</button>
              <button
                type="button"
                :class="{ 'is-active': Number(favoriteCreateForm.isPublic) === 1 }"
                @click="setFavoriteCreateVisibility(1)"
              >公开</button>
            </div>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="favoriteCreateVisible = false">取消</el-button>
        <el-button class="profile-fav-create-save" :loading="favoriteCreateSaving" @click="saveFavoriteFolder">保存</el-button>
      </template>
    </el-dialog>

    <UserFollowListDialog ref="followListDialogRef" />
    <ProfileBannerDialog ref="bannerDialogRef" @confirm="onBannerCropConfirm" />
  </div>
</template>

<script setup>
defineOptions({ name: 'Profile' })
import { ref } from 'vue'
import { Edit } from '@element-plus/icons-vue'
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import IpRegionLabel from '@/components/common/IpRegionLabel.vue'
import UserFollowListDialog from '@/components/user/UserFollowListDialog.vue'
import ProfileBannerDialog from '@/components/profile/ProfileBannerDialog.vue'
import AppPagination from '@/components/common/AppPagination.vue'
import { useProfile } from '@scripts/views/Profile'

const followListDialogRef = ref(null)

const {
  Bookmark,
  Camera,
  FAVORITE_DIALOG_PAGE_SIZE,
  FAVORITE_FOLDER_PAGE_SIZE,
  MessageCircle,
  PROFILE_PAGE_SIZE,
  PUBLIC_GROUP_PAGE_SIZE,
  Plus,
  Star,
  ThumbsUp,
  Trash2,
  ZoomIn,
  activeTab,
  applyJoinPublicGroup,
  articles,
  avatarSrc,
  bannerDialogRef,
  bgFileInput,
  bgStyle,
  canDeleteActiveFavoriteFolder,
  confirmFavoriteFolderRename,
  coverStyle,
  defaultAvatar,
  deleteCurrentFavoriteFolder,
  displayAuthorNickname,
  favoriteFolderError,
  favoriteCoverInputRef,
  favoriteCoverUploadingId,
  favoriteFolderInitial,
  favoriteFolderPageNum,
  favoriteFolderTotal,
  handleBgUpload,
  handleFavoriteCoverFile,
  handleChat,
  toggleFollow,
  isFollowing,
  followSaving,
  followingCount,
  followerCount,
  isMe,
  hasBannerImage,
  likedArticles,
  likedPageNum,
  likedTotal,
  selectProfileTab,
  favoriteCreateForm,
  favoriteCreateSaving,
  favoriteCreateVisible,
  favoriteCoverStyle,
  favoriteDialogItems,
  favoriteDialogLoading,
  favoriteDialogPageNum,
  favoriteDialogTitle,
  favoriteDialogTotal,
  favoriteDialogVisible,
  favoriteFolderPublic,
  favoriteFolderRenaming,
  favoriteFolderRenameSaving,
  favoriteFolderRenameValue,
  favoriteFolders,
  favoriteVisibilitySaving,
  formatProfileDate,
  groupAvatarText,
  isJoinedPublicGroup,
  isPendingPublicGroup,
  joiningGroupId,
  loadFavoriteDialogArticles,
  loadFavoriteFolders,
  loadLikedArticles,
  loadNotInterestedArticles,
  loadProfile,
  loadPublicGroups,
  loadingFavorites,
  loading,
  notesPageNum,
  notesTotal,
  notInterestedArticles,
  notInterestedLoading,
  notInterestedPageNum,
  notInterestedRestoringId,
  notInterestedTotal,
  onBannerCropConfirm,
  openArticleFromFavorite,
  openArticleFromLiked,
  openArticleFromNotInterested,
  openArticleFromNotes,
  openBannerPreview,
  openCreateFavoriteFolder,
  openFavoriteDialog,
  openPublicGroupCard,
  publicGroups,
  publicGroupsLoading,
  publicGroupsPageNum,
  publicGroupsTotal,
  profileIpRegion,
  saveFavoriteFolder,
  setFavoriteCreateVisibility,
  restoreNotInterestedArticle,
  startFavoriteFolderRename,
  toggleFavoriteFolderPublic,
  total,
  triggerBgUpload,
  triggerFavoriteCoverUpload,
  userInfo,
  emptyFavoriteArticleUrl,
} = useProfile()

function openFollowingList() {
  const uid = userInfo.value?.id
  if (!uid) return
  followListDialogRef.value?.openList('following', uid)
}

function openFollowersList() {
  const uid = userInfo.value?.id
  if (!uid) return
  followListDialogRef.value?.openList('followers', uid)
}
</script>

<style scoped src="@/assets/styles/user.css"></style>
<style scoped lang="scss" src="./Profile.scss"></style>
