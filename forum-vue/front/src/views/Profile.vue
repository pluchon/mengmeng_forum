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
              <div class="name-meta-row">
                <h1 class="nickname">{{ userInfo?.nickname || '匿名用户' }}</h1>
                <span class="id-inline">ID: {{ userInfo?.id || '---' }}</span>
              </div>
              <div class="name-row-right">
                <IpRegionLabel :region="profileIpRegion" />
                <div v-if="!isMe" class="action-btns">
                  <el-button
                    round
                    :type="isFollowing ? 'default' : 'primary'"
                    :loading="followSaving"
                    @click="toggleFollow"
                  >
                    {{ isFollowing ? '已关注' : '关注' }}
                  </el-button>
                  <el-button round @click="handleChat">私信</el-button>
                </div>
              </div>
            </div>

            <div class="follow-stats-row">
              <button type="button" class="follow-stat follow-stat-btn" @click="openFollowingList">
                <strong>{{ followingCount }}</strong> 关注
              </button>
              <span class="follow-stat-divider" aria-hidden="true">·</span>
              <button type="button" class="follow-stat follow-stat-btn" @click="openFollowersList">
                <strong>{{ followerCount }}</strong> 粉丝
              </button>
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
                @click="activeTab = 'notes'"
              >
                笔记
              </button>
              <button
                type="button"
                class="profile-tab-btn"
                :class="{ 'is-active': activeTab === 'collect' }"
                @click="activeTab = 'collect'"
              >
                收藏
              </button>
              <button
                type="button"
                class="profile-tab-btn"
                :class="{ 'is-active': activeTab === 'liked' }"
                @click="activeTab = 'liked'"
              >
                点赞
              </button>
              <button
                type="button"
                class="profile-tab-btn"
                :class="{ 'is-active': activeTab === 'groups' }"
                @click="activeTab = 'groups'"
              >
                群聊
              </button>
            </nav>
            <div v-if="activeTab === 'notes'" class="profile-tab-post-count">
              <span class="profile-tab-post-count-val">{{ total }}</span>
              <span class="profile-tab-post-count-lab">发帖数</span>
            </div>
            <div v-if="activeTab === 'liked' && isMe" class="profile-tab-post-count">
              <span class="profile-tab-post-count-val">{{ likedTotal }}</span>
              <span class="profile-tab-post-count-lab">点赞帖子</span>
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

          <div v-show="activeTab === 'notes'" class="profile-content">
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
            <div v-if="notesTotalPages > 1" class="profile-pager">
              <el-button size="small" @click="goNotesFirst">首页</el-button>
              <el-button size="small" :disabled="notesPageNum <= 1" @click="goNotesPrev">上一页</el-button>
              <el-input
                v-model="notesPageInput"
                size="small"
                class="profile-pager-input"
                @keyup.enter="jumpNotesPage"
              />
              <span class="profile-pager-sep">/ {{ notesTotalPages }}</span>
              <el-button size="small" :disabled="notesPageNum >= notesTotalPages" @click="goNotesNext">下一页</el-button>
            </div>
          </div>

          <div v-show="activeTab === 'collect'" class="profile-content profile-fav-list" v-loading="loadingFavorites">
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
                <div>
                  <div class="profile-fav-folder-name">
                    <el-icon class="profile-fav-folder-icon">
                      <Unlock v-if="Number(f.isPublic) === 1" />
                      <Lock v-else />
                    </el-icon>
                    {{ f.name }}
                  </div>
                  <div class="profile-fav-folder-meta">
                    {{ Number(f.isPublic) === 1 ? '公开' : '私密' }} · {{ f.itemCount ?? 0 }} 条
                  </div>
                </div>
                <button type="button" class="profile-fav-view-btn" @click="openFavoriteDialog(f)">查看</button>
              </div>
            </el-card>

            <el-empty v-if="!loadingFavorites && favoriteFolders.length === 0" description="暂无收藏" />
            <div v-if="favoriteFolderTotalPages > 1" class="profile-pager">
              <el-button size="small" @click="goFavoriteFoldersFirst">首页</el-button>
              <el-button size="small" :disabled="favoriteFolderPageNum <= 1" @click="goFavoriteFoldersPrev">上一页</el-button>
              <el-input
                v-model="favoriteFolderPageInput"
                size="small"
                class="profile-pager-input"
                @keyup.enter="jumpFavoriteFolderPage"
              />
              <span class="profile-pager-sep">/ {{ favoriteFolderTotalPages }}</span>
              <el-button
                size="small"
                :disabled="favoriteFolderPageNum >= favoriteFolderTotalPages"
                @click="goFavoriteFoldersNext"
              >
                下一页
              </el-button>
            </div>
          </div>

          <div v-show="activeTab === 'liked'" class="profile-content">
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
            <div v-if="isMe && likedTotalPages > 1" class="profile-pager">
              <el-button size="small" @click="goLikedFirst">首页</el-button>
              <el-button size="small" :disabled="likedPageNum <= 1" @click="goLikedPrev">上一页</el-button>
              <el-input
                v-model="likedPageInput"
                size="small"
                class="profile-pager-input"
                @keyup.enter="jumpLikedPage"
              />
              <span class="profile-pager-sep">/ {{ likedTotalPages }}</span>
              <el-button size="small" :disabled="likedPageNum >= likedTotalPages" @click="goLikedNext">下一页</el-button>
            </div>
          </div>

          <div v-show="activeTab === 'groups'" class="profile-content profile-public-groups" v-loading="publicGroupsLoading">
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
                <div class="profile-public-group-meta">
                  <span>{{ group.memberCount || 0 }}/{{ group.memberLimit || 0 }} 人</span>
                  <span>建群 {{ formatProfileDate(group.createTime) }}</span>
                </div>
              </div>
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
            <div v-if="publicGroupsTotalPages > 1" class="profile-pager">
              <el-button size="small" @click="goPublicGroupsFirst">首页</el-button>
              <el-button size="small" :disabled="publicGroupsPageNum <= 1" @click="goPublicGroupsPrev">上一页</el-button>
              <el-input
                v-model="publicGroupsPageInput"
                size="small"
                class="profile-pager-input"
                @keyup.enter="jumpPublicGroupsPage"
              />
              <span class="profile-pager-sep">/ {{ publicGroupsTotalPages }}</span>
              <el-button size="small" :disabled="publicGroupsPageNum >= publicGroupsTotalPages" @click="goPublicGroupsNext">下一页</el-button>
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
            <div v-if="isMe && favoriteDialogVisible" class="profile-fav-visibility">
              <span class="profile-fav-visibility-label">公开</span>
              <el-switch
                :model-value="favoriteFolderPublic === 1"
                :loading="favoriteVisibilitySaving"
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
            <div class="profile-fav-item-snippet">{{ row.article?.content }}</div>
          </div>
          <div class="profile-fav-item-stats">
            <span>赞 {{ row.article?.likeCount ?? 0 }}</span>
            <span>评 {{ row.article?.replyCount ?? 0 }}</span>
            <span>藏 {{ row.article?.favoriteCount ?? 0 }}</span>
          </div>
          <div class="profile-fav-item-side">
            <div class="profile-fav-item-author">
              <UserAvatarVip
                :size="40"
                :src="row.author?.avatarUrl || defaultAvatar"
                :vip-tier="Number(row.author?.vipTier) || 0"
                :vip-expire-at="row.author?.vipExpireAt"
              />
              <span>{{ row.author?.nickname || '匿名用户' }}</span>
            </div>
          </div>
        </div>
        <el-empty v-if="!favoriteDialogLoading && !favoriteDialogItems.length" description="暂无收藏帖子" />
        <div v-if="favoriteDialogTotalPages > 1" class="profile-pager profile-pager--dialog">
          <el-button size="small" @click="goFavoriteDialogFirst">首页</el-button>
          <el-button size="small" :disabled="favoriteDialogPageNum <= 1" @click="goFavoriteDialogPrev">上一页</el-button>
          <el-input
            v-model="favoriteDialogPageInput"
            size="small"
            class="profile-pager-input"
            @keyup.enter="jumpFavoriteDialogPage"
          />
          <span class="profile-pager-sep">/ {{ favoriteDialogTotalPages }}</span>
          <el-button
            size="small"
            :disabled="favoriteDialogPageNum >= favoriteDialogTotalPages"
            @click="goFavoriteDialogNext"
          >
            下一页
          </el-button>
        </div>
      </div>
    </el-dialog>

    <el-dialog
      v-model="favoriteCreateVisible"
      title="新建收藏夹"
      width="420px"
      class="profile-fav-create-dialog"
      destroy-on-close
    >
      <el-form label-width="84px">
        <el-form-item label="名称">
          <el-input
            v-model="favoriteCreateForm.name"
            maxlength="25"
            show-word-limit
            placeholder="输入收藏夹名称"
          />
        </el-form-item>
        <el-form-item label="可见性">
          <el-radio-group v-model="favoriteCreateForm.isPublic">
            <el-radio :value="1">公开</el-radio>
            <el-radio :value="0">私密</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="favoriteCreateVisible = false">取消</el-button>
        <el-button type="primary" :loading="favoriteCreateSaving" @click="saveFavoriteFolder">保存</el-button>
      </template>
    </el-dialog>

    <UserFollowListDialog ref="followListDialogRef" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Edit } from '@element-plus/icons-vue'
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import IpRegionLabel from '@/components/common/IpRegionLabel.vue'
import UserFollowListDialog from '@/components/user/UserFollowListDialog.vue'
import { useProfile } from '@scripts/views/Profile'

const followListDialogRef = ref(null)

const {
  Camera,
  Lock,
  Plus,
  Star,
  Unlock,
  activeTab,
  applyJoinPublicGroup,
  articles,
  avatarSrc,
  bgFileInput,
  bgStyle,
  confirmFavoriteFolderRename,
  coverStyle,
  favoriteDialogPageInput,
  favoriteDialogPageNum,
  favoriteDialogTotalPages,
  favoriteFolderError,
  favoriteFolderPageInput,
  favoriteFolderPageNum,
  favoriteFolderRenaming,
  favoriteFolderRenameSaving,
  favoriteFolderRenameValue,
  favoriteFolderTotalPages,
  goFavoriteDialogFirst,
  goFavoriteDialogNext,
  goFavoriteDialogPrev,
  goFavoriteFoldersFirst,
  goFavoriteFoldersNext,
  goFavoriteFoldersPrev,
  goLikedFirst,
  goLikedNext,
  goLikedPrev,
  goNotesFirst,
  goNotesNext,
  goNotesPrev,
  handleBgUpload,
  handleChat,
  toggleFollow,
  isFollowing,
  followSaving,
  followingCount,
  followerCount,
  isMe,
  jumpFavoriteDialogPage,
  jumpFavoriteFolderPage,
  jumpLikedPage,
  jumpNotesPage,
  likedArticles,
  likedPageInput,
  likedPageNum,
  likedTotal,
  likedTotalPages,
  favoriteCreateForm,
  favoriteCreateSaving,
  favoriteCreateVisible,
  favoriteCoverStyle,
  favoriteDialogItems,
  favoriteDialogLoading,
  favoriteDialogTitle,
  favoriteDialogVisible,
  favoriteFolderPublic,
  favoriteFolders,
  favoriteVisibilitySaving,
  formatProfileDate,
  goPublicGroupsFirst,
  goPublicGroupsNext,
  goPublicGroupsPrev,
  groupAvatarText,
  isJoinedPublicGroup,
  isPendingPublicGroup,
  joiningGroupId,
  jumpPublicGroupsPage,
  loadingFavorites,
  loading,
  notesPageInput,
  notesPageNum,
  notesTotalPages,
  openArticleFromFavorite,
  openArticleFromLiked,
  openArticleFromNotes,
  openCreateFavoriteFolder,
  openFavoriteDialog,
  openPublicGroupCard,
  publicGroups,
  publicGroupsLoading,
  publicGroupsPageInput,
  publicGroupsPageNum,
  publicGroupsTotalPages,
  profileIpRegion,
  saveFavoriteFolder,
  startFavoriteFolderRename,
  toggleFavoriteFolderPublic,
  total,
  triggerBgUpload,
  userInfo,
  displayVipTier,
  displayVipExpireAt,
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
