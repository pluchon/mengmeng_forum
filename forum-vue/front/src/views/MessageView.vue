<template>
  <el-dialog
    v-model="dialogVisible"
    class="mc-dialog"
    :class="{ 'mc-dialog--vip': viewerIsVip }"
    width="75vw"
    align-center
    append-to-body
    :show-close="false"
    destroy-on-close
    @opened="onDialogOpened"
    @closed="handleClose"
  >
    <button type="button" class="mc-dialog-close" aria-label="关闭" @click="handleClose">
      <el-icon><Close /></el-icon>
    </button>
    <div class="mc" @focusout="onDialogBlurRoot">
      <aside class="mc-left">
        <div class="mc-left-top">
          <div class="mc-left-title-row">
            <div class="mc-left-title">消息中心</div>
          </div>
          <div class="mc-tabs" role="tablist">
            <button
              type="button"
              class="mc-tab"
              :class="{ 'is-on': activeTab === 'pm' }"
              @click="activeTab = 'pm'"
            >
              私信
              <span v-if="tabBadges.pm > 0" class="mc-tab-badge">{{ tabBadges.pm > 99 ? '99+' : tabBadges.pm }}</span>
            </button>
            <button
              type="button"
              class="mc-tab"
              :class="{ 'is-on': activeTab === 'group' }"
              @click="activeTab = 'group'"
            >
              群聊
              <span v-if="tabBadges.group > 0" class="mc-tab-badge">{{ tabBadges.group > 99 ? '99+' : tabBadges.group }}</span>
            </button>
            <button
              type="button"
              class="mc-tab"
              :class="{ 'is-on': activeTab === 'notif' }"
              @click="activeTab = 'notif'"
            >
              通知
              <span v-if="tabBadges.notif > 0" class="mc-tab-badge">{{ tabBadges.notif > 99 ? '99+' : tabBadges.notif }}</span>
            </button>
          </div>
          <div v-if="activeTab !== 'notif'" class="mc-search-row">
            <div class="mc-search">
              <el-icon class="mc-search-icon"><Search /></el-icon>
              <input
                v-model="searchQuery"
                type="search"
                class="mc-search-input"
                placeholder="搜昵称或文本消息内容..."
              >
            </div>
            <button
              v-if="activeTab === 'pm'"
              type="button"
              class="mc-hidden-manage-btn"
              :title="hiddenManagementMode ? '返回普通聊天列表' : '管理已删除聊天'"
              @click="toggleHiddenManagement"
            >
              <el-icon><Back v-if="hiddenManagementMode" /><Delete v-else /></el-icon>
              <span>{{ hiddenManagementMode ? '返回聊天' : '管理已删除聊天' }}</span>
            </button>
            <button
              v-if="activeTab === 'group'"
              type="button"
              class="mc-hidden-manage-btn"
              title="创建群聊"
              @click="openCreateGroup"
            >
              <el-icon><Plus /></el-icon>
              <span>创建群聊</span>
            </button>
          </div>
        </div>

        <el-scrollbar class="mc-list-scroll">
          <div
            v-for="item in listItems"
            :key="item.key"
            class="mc-conv"
            role="button"
            tabindex="0"
            :class="{
              'is-on': isActiveItem(item),
              'is-focus-ring': focusedConvKey === item.key,
              'is-group-private': item.kind === 'group' && Number(item.group?.groupType) === 1,
            }"
            @click="selectListItem(item)"
            @keydown.enter.prevent="selectListItem(item)"
            @focus="onConvFocus(item)"
            @blur="onConvBlur(item)"
          >
            <div class="mc-conv-ava">
              <template v-if="item.kind === 'pm'">
                <UserAvatarVip
                  :size="38"
                  :src="item.user?.avatarUrl || defaultAvatar"                />
              </template>
              <div v-else-if="item.kind === 'group'" class="mc-group-avatar">
                <img v-if="groupAvatarUrl(item.group)" :src="groupAvatarUrl(item.group)" alt="">
                <span v-else>{{ groupAvatarText(item.group) }}</span>
              </div>
              <div v-else class="mc-sys-ava mc-sys-ava--group">
                <el-icon :size="18"><component :is="item.listIcon" /></el-icon>
              </div>
              <span v-if="item.unread > 0" class="mc-unread-dot" />
            </div>
            <div class="mc-conv-body">
              <div class="mc-conv-row">
                <span class="mc-conv-name">
                  <template v-for="(segment, index) in highlightSegments(item.name)" :key="index">
                    <mark v-if="['pm', 'group'].includes(item.kind) && item.nameMatched && segment.matched" class="mc-search-mark">{{ segment.text }}</mark>
                    <span v-else>{{ segment.text }}</span>
                  </template>
                </span>
                <span class="mc-conv-time">{{ formatSessionTime(item.time) }}</span>
              </div>
              <div class="mc-conv-prev" :class="{ 'is-unread': item.unread > 0 }">
                <span class="mc-conv-prev-text">
                  <template v-for="(segment, index) in highlightSegments(item.preview)" :key="index">
                    <mark v-if="['pm', 'group'].includes(item.kind) && item.previewMatched && segment.matched" class="mc-search-mark">{{ segment.text }}</mark>
                    <span v-else>{{ segment.text }}</span>
                  </template>
                </span>
                <span
                  v-if="item.kind === 'group'"
                  class="mc-group-type-badge"
                  :class="{ 'is-private': Number(item.group?.groupType) === 1 }"
                >
                  {{ groupTypeLabel(item.group?.groupType) }}
                </span>
              </div>
              <button
                v-if="item.kind === 'pm'"
                type="button"
                class="mc-conv-visibility-action"
                :title="item.hidden ? '恢复聊天' : '隐藏聊天'"
                @click.stop="item.hidden ? restorePrivateSession(item) : hidePrivateSession(item)"
              >
                <el-icon><RefreshLeft v-if="item.hidden" /><Delete v-else /></el-icon>
              </button>
            </div>
          </div>
          <div v-if="activeTab === 'group' && groupListLoading" class="mc-list-empty mc-list-loading">
            正在加载群聊
          </div>
          <div v-else-if="activeTab === 'group' && groupListError" class="mc-list-empty">
            <el-icon :size="40" color="#dcdfe6"><Warning /></el-icon>
          </div>
          <div
            v-if="listItems.length === 0 && !(activeTab === 'group' && (groupListLoading || groupListError))"
            class="mc-list-empty"
          >
            <template v-if="privateSearchEmpty">
              <img :src="searchChatEmptyUrl" alt="搜索不到" class="mc-search-empty-img">
              <span>搜索不到啊喵......</span>
            </template>
            <template v-else>
              <el-icon :size="48" color="#dcdfe6"><ChatLineSquare /></el-icon>
            </template>
          </div>
        </el-scrollbar>

        <div class="mc-left-bottom">
          <UserAvatarVip
            :size="28"
            :src="userStore.avatarUrl || defaultAvatar"          />
          <span class="mc-left-uname">{{ userStore.nickname || '用户' }}</span>
          <div class="mc-online mc-online--trailing" :class="{ 'is-offline': !selfOnline }">
            <span class="mc-online-dot" />
            <span>{{ selfOnline ? '在线' : '离线' }}</span>
          </div>
        </div>
      </aside>

      <section class="mc-right">
        <template v-if="currentSession || currentGroupSession">
          <header class="mc-rhead">
            <div class="mc-rhead-left">
              <!-- 跳转入口放在这里而不是会话列表：列表是用来找人的，误点一下就跳走 -->
              <button
                v-if="currentSession"
                type="button"
                class="mc-rhead-avatar-btn"
                title="查看对方主页"
                @click="openCurrentPeerProfile"
              >
                <UserAvatarVip
                  :size="34"
                  :src="currentSession.user?.avatarUrl || defaultAvatar"                />
              </button>
              <div v-else class="mc-group-avatar mc-group-avatar--head">
                <img v-if="groupAvatarUrl(currentGroupSession)" :src="groupAvatarUrl(currentGroupSession)" alt="">
                <span v-else>{{ groupAvatarText(currentGroupSession) }}</span>
              </div>
              <div class="mc-rtitle-stack">
                <span class="mc-rname">
                  <button
                    v-if="currentSession"
                    type="button"
                    class="mc-rname-link"
                    title="查看对方主页"
                    @click="openCurrentPeerProfile"
                  >{{ activeChatTitle }}</button>
                  <template v-else>{{ activeChatTitle }}</template>
                  <button
                    v-if="currentGroupSession"
                    type="button"
                    class="mc-group-settings-trigger"
                    title="群设置"
                    @click="openGroupSettings"
                  >
                    <el-icon><Setting /></el-icon>
                  </button>
                </span>
                <span v-if="currentGroupSession" class="mc-rmeta">{{ activeChatSubtitle }}</span>
              </div>
            </div>
            <div v-if="currentSession" class="mc-online mc-online--header" :class="{ 'is-offline': !peerOnline }">
              <span class="mc-online-dot" />
              <span>{{ peerOnline ? '在线' : '离线' }}</span>
            </div>
          </header>

          <el-scrollbar ref="msgScrollbar" class="mc-rbody-scroll" @scroll="onMessagesScroll">
            <div ref="msgContainer" class="mc-rbody mc-rbody--chat">
              <template v-for="row in messageTimeline" :key="row.key">
              <div v-if="row.type === 'date'" class="mc-date-divider">
                <span>{{ row.label }}</span>
              </div>
              <div
                v-else-if="Number(row.msg.message?.messageType) === 9"
                class="mc-group-system-tip"
              >
                <span>{{ row.msg.message?.content }}</span>
              </div>
              <div
                v-else
                class="mc-mrow"
                :class="[row.msg.isOwner ? 'is-me' : 'is-other', { 'is-group': currentGroupSession }]"
              >
                <div
                  class="mc-mrow-ava"
                  role="button"
                  tabindex="0"
                  @click="openMessageSenderProfile(row.msg)"
                  @keydown.enter.prevent="openMessageSenderProfile(row.msg)"
                >
                  <UserAvatarVip
                    :size="28"
                    :src="bubbleAvatar(row.msg)"                  />
                </div>
                <div class="mc-bwrap">
                  <button
                    v-if="currentGroupSession && !row.msg.isOwner"
                    type="button"
                    class="mc-sender-name"
                    @click="openMessageSenderProfile(row.msg)"
                  >
                    {{ row.msg.user?.nickname || '群成员' }}
                  </button>
                  <div class="mc-bubble-action">
                    <span
                      v-if="currentSession
                        && row.msg.isOwner
                        && !row.msg.pendingAlbumState
                        && !isRecalledMessage(row.msg)
                        && !(row.msg.message?.auditFailed || row.msg.auditFailed)
                        && !isGroupInviteCard(row.msg)"
                      class="mc-read mc-read--side"
                    >
                      {{ Number(row.msg.message?.state) === 1 ? '已读' : '未读' }}
                    </span>
                    <div
                      v-if="currentGroupSession && row.msg.isOwner && canShowGroupMessageActions(row.msg)"
                      class="mc-group-message-actions is-left"
                    >
                      <button
                        v-if="canRecallGroupMessage(row.msg)"
                        type="button"
                        @click="handleRecall(row.msg)"
                      >
                        撤回
                      </button>
                      <button type="button" @click="startReply(row.msg)">回复</button>
                    </div>
                    <div
                      class="mc-bbl"
                      :class="{
                        'is-me': row.msg.isOwner && !isMediaMessage(row.msg) && !isRecalledMessage(row.msg),
                        'is-recalled': isRecalledMessage(row.msg),
                        'is-media': isMediaMessage(row.msg) && !isRecalledMessage(row.msg),
                      }"
                    >
                      <span v-if="isRecalledMessage(row.msg)" class="mc-recalled">
                        {{ row.msg.isOwner ? '你撤回了一条消息' : (currentGroupSession ? '群成员撤回了一条消息' : '对方撤回了一条消息') }}
                      </span>
                      <template v-else-if="isGroupInviteCard(row.msg)">
                        <div class="mc-group-invite-card">
                          <div class="mc-group-avatar mc-group-avatar--invite">
                            <img
                              v-if="groupAvatarUrl(groupInviteInfo(row.msg)?.group)"
                              :src="groupAvatarUrl(groupInviteInfo(row.msg)?.group)"
                              alt=""
                            >
                            <span v-else>{{ groupAvatarText(groupInviteInfo(row.msg)?.group) }}</span>
                          </div>
                          <div class="mc-group-invite-main">
                            <div class="mc-group-invite-title">
                              {{ groupInviteInfo(row.msg)?.group?.name || '群聊邀请' }}
                            </div>
                            <div class="mc-group-invite-meta">
                              {{ groupInviteInfo(row.msg)?.group ? `${groupInviteInfo(row.msg).group.memberCount || 0}/${groupInviteInfo(row.msg).group.memberLimit || 0} 人` : '正在加载' }}
                              · {{ groupInviteStatusText(groupInviteInfo(row.msg)) }}
                            </div>
                          </div>
                          <div v-if="canRespondGroupInvite(row.msg)" class="mc-group-invite-actions">
                            <button type="button" @click="acceptInviteCard(row.msg)">✓ 同意</button>
                            <button type="button" @click="declineInviteCard(row.msg)">✕ 拒绝</button>
                          </div>
                        </div>
                      </template>
                      <template v-else-if="isAlbumMessage(row.msg)">
                        <div class="mc-album-stack">
                          <p v-if="row.msg.message?.content" class="mc-album-caption">
                            {{ row.msg.message.content }}
                          </p>
                          <button
                            v-if="row.msg.message.albumImages?.length"
                            type="button"
                            class="mc-album-cover"
                            @click="openAlbumPreview(row.msg, 0)"
                          >
                            <img :src="row.msg.message.albumImages[0].mediaUrl" alt="图集封面" @load="onBubbleMediaLoad">
                            <span class="mc-album-cover-mask">查看图集</span>
                          </button>
                          <div v-if="!row.msg.isOwner" class="mc-album-meta">
                            <span class="mc-btime">{{ formatTime(row.msg.message?.createTime) }}</span>
                          </div>
                          <div v-if="row.msg.message?.replyMessageId" class="mc-reply-quote mc-reply-quote--media">
                            {{ row.msg.message?.replySenderName || '成员' }}:
                            {{ row.msg.message?.replyContent || '消息' }}
                          </div>
                          <div v-if="row.msg.pendingAlbumState" class="mc-album-pending-status">
                            <LoaderCircle
                              v-if="row.msg.pendingAlbumState === 'auditing'"
                              :size="18"
                              class="mc-album-pending-spinner"
                            />
                            <div v-else class="mc-album-pending-failed">
                              <span>{{ row.msg.pendingAlbumError || '发送失败' }}</span>
                              <button type="button" title="重试" @click="retryPendingAlbum(row.msg)">
                                <RotateCcw :size="15" />
                              </button>
                              <button type="button" title="删除" @click="deletePendingAlbum(row.msg)">
                                <Trash2 :size="15" />
                              </button>
                            </div>
                          </div>
                        </div>
                      </template>
                      <template v-else-if="isMediaMessage(row.msg)">
                        <div class="mc-media-stack">
                          <div class="mc-media-visual">
                            <el-image
                              v-if="!isEmojiShopMessage(row.msg)"
                              :src="row.msg.message.mediaUrl"
                              :preview-src-list="[row.msg.message.mediaUrl]"
                              preview-teleported
                              :z-index="10050"
                              fit="contain"
                              class="mc-chat-img"
                              :class="{ 'is-gif': Number(row.msg.message?.messageType) === 2 }"
                              :style="bubbleImageStyle(row.msg.message)"
                              @load="rememberBubbleNaturalSize(row.msg.message, $event)"
                            />
                            <img
                              v-else
                              :src="row.msg.message.mediaUrl"
                              alt="表情商城表情"
                              class="mc-chat-img mc-chat-img--shop"
                              :class="{ 'is-gif': Number(row.msg.message?.messageType) === 2 }"
                              :style="bubbleImageStyle(row.msg.message)"
                              @load="rememberBubbleNaturalSize(row.msg.message, $event)"
                            >
                            <button
                              v-if="isEmojiShopMessage(row.msg)"
                              type="button"
                              class="mc-shop-emoji-badge"
                              title="前往表情商城"
                              @click.stop="openEmojiShopFromMessage(row.msg)"
                            >
                              <img :src="emojiPackIconUrl" alt="表情">
                            </button>
                          </div>
                          <div
                            v-if="!row.msg.isOwner"
                            class="mc-media-meta"
                          >
                            <span class="mc-btime">{{ formatTime(row.msg.message?.createTime) }}</span>
                            <button
                              v-if="canFavoriteChatImage(row.msg)"
                              type="button"
                              class="mc-fav-img-btn"
                              @click="favoriteChatImage(row.msg)"
                            >
                              收藏
                            </button>
                          </div>
                          <div v-if="row.msg.message?.replyMessageId" class="mc-reply-quote mc-reply-quote--media">
                            {{ row.msg.message?.replySenderName || '成员' }}:
                            {{ row.msg.message?.replyContent || '消息' }}
                          </div>
                        </div>
                      </template>
                      <template v-else>
                        <span>{{ row.msg.message?.content }}</span>
                        <div v-if="row.msg.message?.replyMessageId" class="mc-reply-quote">
                          {{ row.msg.message?.replySenderName || '成员' }}:
                          {{ row.msg.message?.replyContent || '消息' }}
                        </div>
                      </template>
                    </div>
                    <div
                      v-if="!row.msg.isOwner && (canShowGroupMessageActions(row.msg) || canReportChatMessage(row.msg))"
                      class="mc-group-message-actions is-right"
                    >
                      <button
                        v-if="canRecallGroupMessage(row.msg)"
                        type="button"
                        @click="handleRecall(row.msg)"
                      >
                        撤回
                      </button>
                      <button v-if="currentGroupSession" type="button" @click="startReply(row.msg)">回复</button>
                      <button v-if="canReportChatMessage(row.msg)" type="button" @click="submitChatMessageReport(row.msg)">举报</button>
                    </div>
                  </div>
                  <div
                    v-if="!row.msg.pendingAlbumState
                      && !isRecalledMessage(row.msg)
                      && !(currentGroupSession && row.msg.isOwner)
                      && !((currentSession || currentGroupSession) && !row.msg.isOwner && isMediaMessage(row.msg))
                      && (!(row.msg.isOwner && (currentSession || currentGroupSession)) || canRecallMessage(row.msg))"
                    class="mc-meta-row"
                    :class="{ 'is-me': row.msg.isOwner }"
                  >
                    <template v-if="currentSession && row.msg.isOwner">
                      <button
                        v-if="canRecallMessage(row.msg)"
                        type="button"
                        class="mc-recall-btn mc-recall-btn--meta"
                        @click="handleRecall(row.msg)"
                      >
                        撤回
                      </button>
                    </template>
                    <span v-else class="mc-btime">{{ formatTime(row.msg.message?.createTime) }}</span>
                  </div>
                </div>
              </div>
              </template>
            </div>
          </el-scrollbar>

          <!-- 正在翻历史时不把人拽回底部，只在这里攒一个角标 -->
          <button
            v-if="pendingNewCount > 0"
            type="button"
            class="mc-new-msg-pill"
            @click="jumpToLatest"
          >
            <el-icon><ArrowDown /></el-icon>
            {{ pendingNewCount }} 条新消息
          </button>

          <footer class="mc-rinput">
            <input
              v-if="currentSession || currentGroupSession"
              ref="chatImageInput"
              type="file"
              class="mc-hidden-file"
              accept="image/jpeg,image/jpg,image/png,image/gif"
              multiple
              @change="onChatImageFileChange"
            >
            <input
              v-if="isPrivateChat"
              ref="emojiStickerInput"
              type="file"
              class="mc-hidden-file"
              accept="image/jpeg,image/jpg,image/png,image/gif"
              multiple
              @change="onEmojiStickerFileChange"
            >
            <div class="mc-itools">
              <button
                v-if="currentSession || currentGroupSession"
                type="button"
                class="mc-itbtn"
                title="发送图片"
                :disabled="sending"
                @click="triggerChatImagePick"
              >
                <ImageIcon :size="18" :stroke-width="1.8" />
              </button>
              <el-popover
                v-if="currentGroupSession"
                v-model:visible="mentionPopoverVisible"
                placement="top-start"
                :width="260"
                trigger="manual"
              >
                <template #reference>
                  <button type="button" class="mc-itbtn mc-at-btn" title="@群成员" @click.stop="toggleMentionPicker">
                    <AtSign :size="18" :stroke-width="1.8" />
                  </button>
                </template>
                <div class="mc-mention-panel">
                  <button
                    v-if="isCurrentGroupManager"
                    type="button"
                    class="mc-mention-all"
                    @click="selectMentionAll"
                  >
                    @所有人
                  </button>
                  <input
                    v-model="mentionSearch"
                    type="search"
                    class="mc-mention-search"
                    placeholder="搜索群成员"
                    @input="onMentionSearchInput"
                  >
                  <div v-if="!filteredMentionMembers.length" class="mc-mention-empty">
                    暂无成员
                  </div>
                  <button
                    v-for="member in paginatedMentionMembers"
                    :key="member.id"
                    type="button"
                    class="mc-mention-item"
                    @click="selectMentionMember(member)"
                  >
                    <UserAvatarVip
                      :size="26"
                      :src="member.user?.avatarUrl || defaultAvatar"                    />
                    <span>{{ memberDisplayName(member) }}</span>
                  </button>
                  <div class="mc-mention-pager">
                    <AppPagination
                      v-model:current-page="mentionMembersPage"
                      size="small"
                      :total="mentionMembersTotal"
                      :page-size="MENTION_PAGE_SIZE"
                      :pager-count="5"
                      :show-jumper="false"
                      @current-change="onMentionMembersPageChange"
                    />
                  </div>
                </div>
              </el-popover>
              <el-popover
                v-model:visible="emojiPopoverVisible"
                placement="top-start"
                :width="340"
                trigger="click"
                :persistent="true"
                @show="onEmojiPopoverShow"
              >
                <template #reference>
                  <button type="button" class="mc-itbtn" title="表情">
                    <Smile :size="18" :stroke-width="1.8" />
                  </button>
                </template>
                <div
                  v-loading="chatEmojiStore.loading || (emojiPanelTab === 'purchased' && emojiShopStore.myPacksLoading)"
                  class="mc-emoji-panel"
                >
                  <el-tabs v-model="emojiPanelTab" class="mc-emoji-tabs" @tab-change="onEmojiTabChange">
                    <el-tab-pane label="收藏" name="favorites">
                      <div v-if="!favoriteEmojis.length" class="mc-emoji-empty">
                        <img :src="emojiPersonEmptyUrl" alt="暂无收藏表情" class="mc-emoji-empty-img">
                        <span class="mc-emoji-empty-text">暂无表情</span>
                      </div>
                      <div v-else class="mc-emoji-favorites">
                        <div class="mc-emoji-grid mc-emoji-grid--favorites">
                          <div
                            v-for="em in paginatedFavorites"
                            :key="em.id"
                            class="mc-emoji-cell"
                          >
                            <img
                              :src="em.mediaUrl"
                              alt=""
                              class="mc-emoji-thumb"
                              @click="sendMessageFromEmoji(em)"
                            >
                            <button
                              type="button"
                              class="mc-emoji-del"
                              aria-label="删除"
                              @click.stop="removeEmojiKeepPopover(em.id, 'favorite')"
                            >
                              <el-icon><Close /></el-icon>
                            </button>
                          </div>
                        </div>
                        <div class="mc-emoji-fav-pager mc-emoji-fav-pager--solo">
                          <AppPagination
                            v-model:current-page="favoritePage"
                            size="small"
                            :total="favoritePagerTotal"
                            :page-size="FAVORITES_PAGE_SIZE"
                            :show-jumper="false"
                            :pager-count="5"
                            @current-change="onFavoritePageChange"
                          />
                        </div>
                      </div>
                    </el-tab-pane>
                    <el-tab-pane label="我的上传" name="uploads">
                      <div class="mc-emoji-favorites">
                        <div class="mc-emoji-grid mc-emoji-grid--favorites">
                          <div
                            v-for="em in paginatedUploaded"
                            :key="em.id"
                            class="mc-emoji-cell"
                          >
                            <img
                              :src="em.mediaUrl"
                              alt=""
                              class="mc-emoji-thumb"
                              @click="sendMessageFromEmoji(em)"
                            >
                            <button
                              type="button"
                              class="mc-emoji-del"
                              aria-label="删除"
                              @click.stop="removeEmojiKeepPopover(em.id, 'uploaded')"
                            >
                              <el-icon><Close /></el-icon>
                            </button>
                          </div>
                          <div
                            v-for="slot in uploadedPendingSlots"
                            :key="slot.id"
                            class="mc-emoji-cell mc-emoji-cell--pending"
                          >
                            <img :src="slot.previewUrl" alt="" class="mc-emoji-thumb">
                            <div class="mc-emoji-pending-mask" aria-hidden="true">
                              <span class="mc-emoji-pending-dot" />
                            </div>
                          </div>
                          <button
                            v-if="isPrivateChat && showUploadOnCurrentPage"
                            type="button"
                            class="mc-emoji-add mc-emoji-add--cell"
                            title="上传表情"
                            @click="triggerEmojiStickerPick"
                          >
                            <el-icon :size="20"><Plus /></el-icon>
                          </button>
                        </div>
                        <div class="mc-emoji-fav-pager mc-emoji-fav-pager--solo">
                          <AppPagination
                            v-model:current-page="uploadedPage"
                            size="small"
                            :total="uploadedPagerTotal"
                            :page-size="FAVORITES_PAGE_SIZE"
                            :show-jumper="false"
                            :pager-count="5"
                            @current-change="onUploadedPageChange"
                          />
                        </div>
                      </div>
                    </el-tab-pane>
                    <el-tab-pane label="已购" name="purchased">
                      <div v-if="!visiblePacks.length" class="mc-emoji-empty">
                        <img :src="emojiPersonEmptyUrl" alt="暂无已购表情" class="mc-emoji-empty-img">
                        <span class="mc-emoji-empty-text">暂无表情</span>
                      </div>
                      <div v-else class="mc-emoji-purchased-layout">
                        <div class="mc-emoji-pack-body">
                          <div class="mc-emoji-grid mc-emoji-grid--pack mc-emoji-grid--scroll">
                            <img
                              v-for="(url, uidx) in (selectedPurchasedPack?.imageUrls || [])"
                              :key="uidx"
                              :src="url"
                              alt=""
                              class="mc-emoji-thumb"
                              @click="sendMessageFromShopUrl(url)"
                            >
                          </div>
                        </div>
                        <div class="mc-emoji-pack-bar">
                          <button
                            v-if="packBarCanScrollLeft"
                            type="button"
                            class="mc-emoji-pack-more"
                            aria-label="向左滚动"
                            @click="scrollPackBarLeft"
                          >
                            <el-icon><ArrowLeft /></el-icon>
                          </button>
                          <div ref="packBarRef" class="mc-emoji-pack-bar-scroll" @scroll="onPackBarScroll">
                            <div
                              v-for="pack in visiblePacks"
                              :key="pack.userEmojiId"
                              class="mc-emoji-pack-bar-item"
                            >
                              <button
                                type="button"
                                class="mc-emoji-pack-cover"
                                :class="{ 'is-active': Number(selectedPurchasedPack?.userEmojiId) === Number(pack.userEmojiId) }"
                                :title="pack.name"
                                @click="selectPurchasedPack(pack)"
                              >
                                <img :src="pack.coverUrl || pack.imageUrls?.[0]" alt="">
                              </button>
                              <transition name="mc-pack-name">
                                <span
                                  v-if="Number(selectedPurchasedPack?.userEmojiId) === Number(pack.userEmojiId)"
                                  :key="pack.userEmojiId"
                                  class="mc-emoji-pack-name"
                                >{{ pack.name }}</span>
                              </transition>
                            </div>
                          </div>
                          <button
                            v-if="packBarCanScrollRight"
                            type="button"
                            class="mc-emoji-pack-more"
                            aria-label="查看更多表情包"
                            @click="scrollPackBarRight"
                          >
                            <el-icon><ArrowRight /></el-icon>
                          </button>
                        </div>
                      </div>
                    </el-tab-pane>
                  </el-tabs>
                </div>
              </el-popover>
              <el-popover
                v-if="currentGroupSession && isCurrentGroupOwner"
                v-model:visible="groupAdminVisible"
                placement="top-start"
                :width="320"
                trigger="manual"
              >
                <template #reference>
                  <button type="button" class="mc-itbtn" title="管理员设置" @click.stop="toggleGroupAdminPicker">
                    <ShieldCheck :size="18" :stroke-width="1.8" />
                  </button>
                </template>
                <div v-loading="groupAdminLoading" class="mc-group-admin-panel">
                  <input
                    v-model="groupAdminSearch"
                    type="search"
                    class="mc-mention-search"
                    placeholder="搜索群成员昵称"
                    @input="onGroupAdminSearchInput"
                  >
                  <div class="mc-group-admin-list">
                    <div v-for="member in groupAdminMembers" :key="member.id" class="mc-group-admin-item">
                      <UserAvatarVip
                        :size="28"
                        :src="member.user?.avatarUrl || defaultAvatar"                      />
                      <span class="mc-group-admin-name">{{ memberDisplayName(member) }}</span>
                      <span v-if="Number(member.role) === 0" class="mc-group-admin-role is-owner">群主</span>
                      <button
                        v-else
                        type="button"
                        class="mc-group-admin-role"
                        :class="Number(member.role) === 2 ? 'is-admin' : 'is-member'"
                        :disabled="Number(groupAdminUpdatingId) === Number(member.user?.id)"
                        @click="toggleGroupAdminRole(member)"
                      >
                        {{ Number(member.role) === 2 ? '撤销管理员' : '设为管理员' }}
                      </button>
                    </div>
                    <div v-if="!groupAdminLoading && !groupAdminMembers.length" class="mc-mention-empty">暂无成员</div>
                  </div>
                  <div class="mc-mention-pager">
                    <AppPagination
                      v-model:current-page="groupAdminPage"
                      size="small"
                      :total="groupAdminTotal"
                      :page-size="5"
                      :pager-count="5"
                      :show-jumper="false"
                      @current-change="onGroupAdminPageChange"
                    />
                  </div>
                </div>
              </el-popover>
              <el-popover
                v-if="isPrivateChat"
                v-model:visible="ownedGroupInviteVisible"
                placement="top-start"
                :width="300"
                trigger="manual"
              >
                <template #reference>
                  <button type="button" class="mc-itbtn" title="邀请入群" @click="openOwnedGroupInvitePicker">
                    <el-icon><UserFilled /></el-icon>
                  </button>
                </template>
                <div class="mc-owned-group-panel" v-loading="ownedGroupsLoading">
                  <input
                    v-model="ownedGroupSearch"
                    type="search"
                    class="mc-mention-search"
                    placeholder="搜索我的群聊"
                  >
                  <div class="mc-owned-group-list">
                    <button
                      v-for="group in ownedGroups"
                      :key="group.id"
                      type="button"
                      class="mc-owned-group-item"
                      :disabled="Number(invitingGroupId) === Number(group.id)"
                      @click="sendGroupInviteFromPm(group)"
                    >
                      <div class="mc-group-avatar mc-group-avatar--mini">
                        <img v-if="groupAvatarUrl(group)" :src="groupAvatarUrl(group)" alt="">
                        <span v-else>{{ groupAvatarText(group) }}</span>
                      </div>
                      <span class="mc-owned-group-name">{{ group.name }}</span>
                      <span class="mc-owned-group-count">{{ ownedGroupMemberText(group) }}</span>
                    </button>
                    <div v-if="!ownedGroupsLoading && ownedGroups.length === 0" class="mc-mention-empty">
                      暂无可邀请的群聊
                    </div>
                  </div>
                  <div class="mc-mention-pager">
                    <AppPagination
                      v-model:current-page="ownedGroupPage"
                      size="small"
                      :total="ownedGroupTotal"
                      :page-size="5"
                      :pager-count="5"
                      :show-jumper="false"
                      @current-change="onOwnedGroupPageChange"
                    />
                  </div>
                </div>
              </el-popover>
            </div>
            <div v-if="(currentSession || currentGroupSession) && pendingAlbumFiles.length" class="mc-album-compose">
              <div class="mc-album-compose-head">
                <span>待发送图片</span>
                <span>{{ pendingAlbumFiles.length }}/10</span>
              </div>
              <div class="mc-album-compose-list">
                <div v-for="item in pendingAlbumFiles" :key="item.id" class="mc-album-compose-item">
                  <img :src="item.previewUrl" :alt="item.file.name">
                  <button type="button" aria-label="移除图片" :disabled="sending" @click="removePendingAlbumFile(item.id)">
                    <el-icon><Close /></el-icon>
                  </button>
                </div>
                <button
                  v-if="pendingAlbumFiles.length < 10"
                  type="button"
                  class="mc-album-compose-add"
                  title="继续添加图片"
                  :disabled="sending"
                  @click="triggerChatImagePick"
                >
                  <el-icon><Plus /></el-icon>
                </button>
              </div>
            </div>
            <div v-if="replyTarget" class="mc-reply-draft">
              <span>回复 {{ replyTarget.senderName }}: {{ replyTarget.content }}</span>
              <button type="button" @click="clearReplyTarget">取消</button>
            </div>
            <div class="mc-irow">
              <textarea
                ref="inputBoxRef"
                v-model="sendContent"
                class="mc-ibox"
                rows="1"
                placeholder="在此输入消息内容…"
                @input="autoResizeInput"
                @keydown="onComposerKeydown"
              />
              <button type="button" class="mc-sbtn" :disabled="sending" @click="sendMsg">
                <el-icon><Promotion /></el-icon>
                <span>{{ sending ? '发送中' : '发送' }}</span>
              </button>
            </div>
          </footer>
        </template>

        <template v-else-if="currentSystemGroup && currentSystemGroup.groupId === 'joinRequest'">
          <header class="mc-rhead">
            <div class="mc-rhead-left">
              <div class="mc-sys-ava mc-sys-ava--group mc-sys-ava--head">
                <el-icon :size="18"><UserFilled /></el-icon>
              </div>
              <span class="mc-rname">进群申请</span>
            </div>
            <el-input
              v-model="notificationSearch"
              class="mc-notification-search"
              clearable
              placeholder="搜索群或群主"
              :prefix-icon="Search"
            />
          </header>
          <div class="mc-notify-shell">
            <el-scrollbar class="mc-rbody-scroll">
              <div class="mc-rbody mc-rbody--notify">
                <article
                  v-for="item in activeJoinRequests"
                  :key="item.id"
                  class="mc-join-request-row"
                >
                  <UserAvatarVip
                    :size="34"
                    :src="(item.viewerSide === 'applicant' ? item.ownerUser?.avatarUrl : item.targetUser?.avatarUrl) || defaultAvatar"                  />
                  <span class="mc-join-request-name">
                    {{ item.viewerSide === 'applicant' ? (item.ownerUser?.nickname || '群主') : (item.targetUser?.nickname || '用户') }}
                  </span>
                  <div class="mc-join-request-group">
                    <div class="mc-group-avatar mc-group-avatar--mini">
                      <img v-if="groupAvatarUrl(item.group)" :src="groupAvatarUrl(item.group)" alt="">
                      <span v-else>{{ groupAvatarText(item.group) }}</span>
                    </div>
                    <span>{{ item.group?.name || '群聊' }}</span>
                  </div>
                  <span class="mc-join-request-time">{{ formatJoinRequestTime(item.createTime) }}</span>
                  <div class="mc-join-request-actions">
                    <template v-if="item.viewerSide === 'owner' && Number(item.status) === 0">
                      <button type="button" @click="approveJoinRequestItem(item)">批准</button>
                      <button type="button" class="is-plain" @click="rejectJoinRequestItem(item)">拒绝</button>
                    </template>
                    <span
                      v-else-if="item.viewerSide === 'applicant'"
                      :class="Number(item.status) === 1 ? 'is-approved' : 'is-rejected'"
                    >
                      {{ Number(item.status) === 1 ? '已通过' : '被拒绝' }}
                    </span>
                    <span
                      v-else
                      :class="Number(item.status) === 1 ? 'is-approved' : Number(item.status) === 2 ? 'is-rejected' : ''"
                    >{{ Number(item.status) === 1 ? '已批准' : Number(item.status) === 3 ? '已作废' : '已拒绝' }}</span>
                  </div>
                </article>
                <div v-if="!activeJoinRequests.length" class="mc-public-empty">暂无进群申请</div>
              </div>
            </el-scrollbar>
            <div class="mc-notification-pager">
              <AppPagination
                v-model:current-page="notificationPage"
                size="small"
                :total="notificationTotal"
                :page-size="JOIN_REQUEST_PAGE_SIZE"
                :pager-count="5"
                :show-jumper="false"
                @current-change="onNotificationPageChange"
              />
            </div>
          </div>
        </template>

        <template v-else-if="currentSystemGroup">
          <header class="mc-rhead">
            <div class="mc-rhead-left">
              <div class="mc-sys-ava mc-sys-ava--group mc-sys-ava--head">
                <el-icon :size="18"><Document /></el-icon>
              </div>
              <span class="mc-rname">{{ currentSystemGroup.name }}</span>
            </div>
            <el-input
              v-model="notificationSearch"
              class="mc-notification-search"
              clearable
              :placeholder="currentSystemGroup.groupId === 'audit' ? '搜索帖子标题' : currentSystemGroup.groupId === 'musicAudit' ? '搜索歌曲' : currentSystemGroup.groupId === 'tag' ? '搜索标签内容' : '搜索举报内容'"
              :prefix-icon="Search"
            />
          </header>
          <div class="mc-notify-shell">
            <el-scrollbar class="mc-rbody-scroll">
              <div class="mc-rbody mc-rbody--notify">
                <article
                  v-for="msg in activeSystemMessages"
                  :key="msg.id"
                  class="mc-ncard"
                  :class="{ 'mc-ncard--audit': [1, 2, 3].includes(Number(msg.type)) }"
                >
                  <div class="mc-ncard-head">
                    <div class="mc-ncard-title-row">
                      <span :class="sysTagClass(msg.type)" class="mc-tag">{{ sysTagLabel(msg.type) }}</span>
                      <strong class="mc-ncard-title">{{ systemNotifyCardTitle(msg) }}</strong>
                    </div>
                    <span class="mc-ncard-time">{{ formatSessionTime(msg.createTime) }}</span>
                  </div>
                  <p class="mc-ncard-body">
                    <template v-if="parseSystemMessageContent(msg).articleTitle">
                      <span>{{ parseSystemMessageContent(msg).before }}</span>
                      <button
                        type="button"
                        class="mc-article-link"
                        @click="openArticleFromSystem(msg)"
                      >
                        《{{ parseSystemMessageContent(msg).articleTitle }}》
                      </button>
                      <span>{{ parseSystemMessageContent(msg).after }}</span>
                    </template>
                    <template v-else>
                      {{ parseSystemMessageContent(msg).plain }}
                    </template>
                  </p>
                </article>
              </div>
            </el-scrollbar>
            <div
              v-if="['audit', 'tag', 'musicAudit'].includes(currentSystemGroup.groupId)"
              class="mc-notification-pager"
            >
              <AppPagination
                v-model:current-page="notificationPage"
                size="small"
                :total="notificationTotal"
                :page-size="SYSTEM_NOTIFY_PAGE_SIZE"
                :pager-count="5"
                :show-jumper="false"
                @current-change="onNotificationPageChange"
              />
            </div>
          </div>
        </template>

        <div v-else class="mc-welcome">
          <img :src="chatUnselectUrl" alt="选择会话" class="mc-welcome-img">
          <span>选择左侧的会话......</span>
        </div>
      </section>
    </div>
  </el-dialog>

  <el-dialog
    v-model="albumPreviewVisible"
    class="mc-album-preview-dialog"
    width="min(760px, 88vw)"
    append-to-body
    align-center
    :title="`查看图集 · ${albumPreviewImages.length} 张`"
  >
    <el-carousel
      v-if="albumPreviewImages.length"
      :key="`${albumPreviewImages.length}-${albumPreviewIndex}`"
      :initial-index="albumPreviewIndex"
      height="min(66vh, 620px)"
      indicator-position="outside"
      arrow="always"
    >
      <el-carousel-item v-for="(image, index) in albumPreviewImages" :key="image.id || index">
        <img :src="image.mediaUrl" :alt="`图集第 ${index + 1} 张图片`" class="mc-album-preview-image">
      </el-carousel-item>
    </el-carousel>
  </el-dialog>

  <el-dialog
    v-model="groupCreateVisible"
    title="创建群聊"
    width="460px"
    append-to-body
    class="mc-group-create-dialog"
    :show-close="false"
  >
    <div class="mc-group-form">
      <div class="mc-group-create-row">
        <el-input
          v-model="groupCreateForm.name"
          maxlength="10"
          show-word-limit
          placeholder="群名称"
        />
        <el-select v-model="groupCreateForm.groupType" class="mc-group-form-control">
          <el-option label="公开群" :value="0" />
          <el-option label="私有群" :value="1" />
        </el-select>
      </div>
      <el-input
        v-model="groupCreateForm.intro"
        type="textarea"
        :rows="4"
        maxlength="120"
        show-word-limit
        placeholder="群简介"
      />
    </div>
    <template #footer>
      <div class="mc-group-dialog-footer">
        <button
          type="button"
          class="mc-dialog-action"
          :disabled="creatingGroup"
          @click="groupCreateVisible = false"
        >
          取消
        </button>
        <button
          type="button"
          class="mc-dialog-action mc-dialog-action--primary"
          :disabled="creatingGroup"
          @click="submitCreateGroup"
        >
          {{ creatingGroup ? '创建中' : '创建' }}
        </button>
      </div>
    </template>
  </el-dialog>

  <Teleport v-if="groupSettingsPortalReady" to=".mc-right">
    <Transition name="mc-group-drawer">
      <div
        v-if="groupSettingsVisible"
        class="mc-group-settings-layer"
        @click.self="requestCloseGroupSettings"
      >
        <aside class="mc-group-settings-drawer" aria-label="群资料">
          <button type="button" class="mc-group-settings-close" title="关闭群资料" @click="requestCloseGroupSettings">
            <el-icon><Close /></el-icon>
          </button>
          <div class="mc-group-settings">
      <div class="mc-group-profile-head">
        <input
          v-if="isCurrentGroupOwner"
          ref="groupAvatarInputRef"
          type="file"
          class="mc-hidden-file"
          accept="image/jpeg,image/png,image/gif"
          @change="onGroupAvatarFileChange"
        >
        <button
          v-if="isCurrentGroupOwner"
          type="button"
          class="mc-group-profile-avatar is-editable"
          :disabled="uploadingGroupAvatar"
          title="修改群头像"
          @click="triggerGroupAvatarUpload"
        >
          <img v-if="groupEditForm.avatarUrl" :src="groupEditForm.avatarUrl" alt="群头像">
          <span v-else>{{ groupAvatarText(groupEditForm) }}</span>
          <span class="mc-group-profile-avatar-mask">{{ uploadingGroupAvatar ? '上传中' : '修改' }}</span>
        </button>
        <div v-else class="mc-group-profile-avatar">
          <img v-if="groupAvatarUrl(currentGroupSession)" :src="groupAvatarUrl(currentGroupSession)" alt="群头像">
          <span v-else>{{ groupAvatarText(currentGroupSession) }}</span>
        </div>
        <el-input
          v-if="isCurrentGroupOwner"
          v-model="groupEditForm.name"
          class="mc-group-profile-name-input"
          maxlength="10"
          show-word-limit
          placeholder="群名称"
        />
        <strong v-else class="mc-group-profile-name">
          {{ currentGroupSession?.groupName || currentGroupSession?.name || '群聊' }}
        </strong>
        <span class="mc-group-profile-count">{{ Number(currentGroupSession?.memberCount) || groupMembersTotal || 0 }} 人</span>
      </div>
      <span v-if="groupEditDirty" class="mc-group-save-hint">保存后生效</span>

      <section class="mc-group-settings-section">
        <div class="mc-group-settings-head">
          <div class="mc-group-settings-title">群成员</div>
          <div class="mc-group-settings-head-right">
            <input
              v-model="groupMemberSearch"
              type="search"
              class="mc-group-member-search"
              placeholder="搜索昵称"
              @input="onGroupMemberSearchInput"
            >
            <div class="mc-group-member-pager">
              <AppPagination
                v-model:current-page="groupMembersPage"
                size="small"
                :total="groupMembersTotal"
                :page-size="4"
                :pager-count="5"
                :show-jumper="false"
                @current-change="onGroupMembersPageChange"
              />
            </div>
          </div>
        </div>

        <div
          v-loading="groupMembersLoading"
          class="mc-member-list mc-member-list--settings"
          :class="{ 'is-member-view': !isCurrentGroupOwner }"
        >
          <div
            v-for="member in paginatedGroupMembers"
            :key="member.id"
            class="mc-member-card"
            role="button"
            tabindex="0"
            @click="openGroupMemberProfile(member)"
            @keydown.enter="openGroupMemberProfile(member)"
            @keydown.space.prevent="openGroupMemberProfile(member)"
          >
            <UserAvatarVip
              :size="32"
              :src="member.user?.avatarUrl || defaultAvatar"            />
            <div class="mc-member-body">
              <span class="mc-member-name">{{ member.user?.nickname || ('用户' + member.user?.id) }}</span>
              <span class="mc-member-meta">
                {{ memberRoleLabel(member.role) }}
                <template v-if="memberMuteLabel(member)"> · {{ memberMuteLabel(member) }}</template>
              </span>
            </div>
            <div
              v-if="isCurrentGroupOwner && Number(member.role) !== 0"
              class="mc-member-actions"
            >
              <button
                type="button"
                class="mc-member-action mc-member-action--icon"
                :class="{ 'is-muted': isMemberMuted(member) }"
                :title="isMemberMuted(member) ? '解除禁言' : '禁言成员'"
                @click.stop="toggleMuteMember(member)"
              >
                <span v-if="isMemberMuted(member)">🚫</span>
                <el-icon v-else><ChatLineRound /></el-icon>
              </button>
              <button
                type="button"
                class="mc-member-action mc-member-action--trash"
                title="移除成员"
                aria-label="移除成员"
                @click.stop="removeMember(member)"
              >
                <Trash2 :size="16" />
              </button>
            </div>
          </div>
          <div v-if="!groupMembersLoading && groupMembers.length === 0" class="mc-public-empty">
            暂无成员
          </div>
        </div>
      </section>

      <section v-if="isCurrentGroupOwner" class="mc-group-settings-section">
        <div class="mc-group-form">
          <div class="mc-group-field">
            <span class="mc-group-field-label">免打扰：</span>
            <div class="mc-group-notify-toggle">
              <button
                v-for="option in groupNotifyOptions"
                :key="option.value"
                type="button"
                :class="{ 'is-active': Number(groupRemarkForm.notifyMode) === Number(option.value) }"
                @click="setGroupNotifyMode(option.value)"
              >
                {{ option.label }}
              </button>
            </div>
          </div>
          <div class="mc-group-field">
            <span class="mc-group-field-label">公开性：</span>
            <div class="mc-group-type-toggle">
              <button
                type="button"
                :class="{ 'is-active': Number(groupEditForm.groupType) === 0 }"
                :disabled="groupTypeSwitchLocked"
                @click="switchGroupType(0)"
              >
                公开群
              </button>
              <button
                type="button"
                :class="{ 'is-active': Number(groupEditForm.groupType) === 1 }"
                :disabled="groupTypeSwitchLocked"
                @click="switchGroupType(1)"
              >
                私有群
              </button>
            </div>
          </div>
          <div class="mc-group-intro-card">
            <div
              ref="groupIntroEditorRef"
              class="mc-group-intro-editor"
              contenteditable="true"
              role="textbox"
              aria-label="群简介"
              data-placeholder="群简介"
              @input="onGroupIntroInput"
            />
            <span class="mc-group-intro-count">{{ groupEditForm.intro.length }} / 120</span>
          </div>
          <div class="mc-group-owner-actions">
            <button type="button" class="mc-group-danger-btn" @click="dissolveCurrentGroup">解散群聊</button>
            <button
              type="button"
              class="mc-dialog-action mc-dialog-action--primary mc-group-save-btn"
              :disabled="savingGroupEdit"
              @click="submitGroupEdit"
            >
              {{ savingGroupEdit ? '保存中' : '保存' }}
            </button>
          </div>
          <p class="mc-group-created-at">
            此群建立于 {{ formatGroupCreatedDate(currentGroupSession?.createTime) }}
          </p>
        </div>
      </section>

      <section v-else class="mc-group-settings-section">
        <div class="mc-group-readonly-profile">
          <div class="mc-group-readonly-grid">
            <div class="mc-group-readonly-row">
              <span>公开性：</span>
              <strong>{{ groupTypeLabel(currentGroupSession?.groupType) }}</strong>
            </div>
          </div>
          <div class="mc-group-readonly-intro">
            {{ currentGroupSession?.intro || '暂无群简介' }}
          </div>
          <div class="mc-group-field">
            <span class="mc-group-field-label">群昵称备注：</span>
            <div class="mc-group-name-row">
              <el-input
                v-model="groupRemarkForm.remarkName"
                maxlength="24"
                show-word-limit
                placeholder="给这个群起个备注"
              />
              <button
                type="button"
                class="mc-group-avatar-upload-btn"
                :disabled="savingGroupRemark"
                @click="submitGroupRemark"
              >
                {{ savingGroupRemark ? '保存中' : '保存备注' }}
              </button>
            </div>
          </div>
          <div class="mc-group-field">
            <span class="mc-group-field-label">免打扰：</span>
            <div class="mc-group-notify-toggle">
              <button
                v-for="option in groupNotifyOptions"
                :key="option.value"
                type="button"
                :class="{ 'is-active': Number(groupRemarkForm.notifyMode) === Number(option.value) }"
                @click="setGroupNotifyMode(option.value)"
              >
                {{ option.label }}
              </button>
            </div>
          </div>
        </div>
      </section>

      <section v-if="!isCurrentGroupOwner" class="mc-group-danger-zone">
        <button
          type="button"
          class="mc-group-danger-btn"
          @click="leaveCurrentGroup"
        >
          退出群聊
        </button>
        <p class="mc-group-created-at">
          此群建立于 {{ formatGroupCreatedDate(currentGroupSession?.createTime) }}
        </p>
      </section>
          </div>
        </aside>
      </div>
    </Transition>
  </Teleport>

  <ReportReasonDialog
    v-model:visible="chatReportDialogVisible"
    title="举报消息"
    :submitting="chatReportSubmitting"
    @submit="confirmChatMessageReport"
  />
</template>

<script setup>
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import ReportReasonDialog from '@/components/common/ReportReasonDialog.vue'
import AppPagination from '@/components/common/AppPagination.vue'
import { useMessageView } from '@scripts/views/MessageView'

const {
  AtSign,
  ArrowLeft,
  ArrowRight,
  albumGridColumns,
  albumPreviewImages,
  albumPreviewIndex,
  albumPreviewVisible,
  ChatLineRound,
  ChatLineSquare,
  Delete,
  RefreshLeft,
  Back,
  Close,
  Document,
  ImageIcon,
  LoaderCircle,
  RotateCcw,
  ShieldCheck,
  Smile,
  Trash2,
  Promotion,
  Plus,
  Search,
  Setting,
  UserFilled,
  activeJoinRequests,
  activeSystemMessages,
  activeTab,
  notificationSearch,
  notificationPage,
  notificationTotal,
  JOIN_REQUEST_PAGE_SIZE,
  SYSTEM_NOTIFY_PAGE_SIZE,
  onNotificationPageChange,
  onComposerKeydown,
  enterToSendEnabled,
  activeChatSubtitle,
  activeChatTitle,
  emojiPersonEmptyUrl,
  chatUnselectUrl,
  chatReportDialogVisible,
  chatReportSubmitting,
  searchChatEmptyUrl,
  autoResizeInput,
  bubbleAvatar,
  bubbleImageStyle,
  canFavoriteChatImage,
  canRecallMessage,
  canRecallGroupMessage,
  canShowGroupMessageActions,
  canRespondGroupInvite,
  chatEmojiStore,
  chatImageInput,
  currentSession,
  currentGroupSession,
  currentSystemGroup,
  defaultAvatar,
  dialogVisible,
  dissolveCurrentGroup,
  emojiPackIconUrl,
  emojiPanelTab,
  emojiPopoverVisible,
  emojiShopStore,
  emojiStickerInput,
  favoriteChatImage,
  favoriteEmojis,
  favoritePage,
  FAVORITES_PAGE_SIZE,
  favoritePagerTotal,
  filteredMentionMembers,
  onGroupMembersPageChange,
  onGroupAdminPageChange,
  onMentionMembersPageChange,
  onOwnedGroupPageChange,
  focusedConvKey,
  formatGroupCreatedDate,
  formatSessionTime,
  formatJoinRequestTime,
  formatTime,
  onFavoritePageChange,
  groupAdminVisible,
  groupAdminMembers,
  groupAdminSearch,
  groupAdminPage,
  groupAdminTotal,
  groupAdminLoading,
  groupAdminUpdatingId,
  groupAvatarText,
  groupAvatarUrl,
  handleClose,
  handleRecall,
  inputBoxRef,
  isActiveItem,
  isCurrentGroupOwner,
  isCurrentGroupManager,
  isMemberMuted,
  isMediaMessage,
  isAlbumMessage,
  isEmojiShopGroupMedia,
  isEmojiShopMessage,
  isGroupInviteCard,
  isRecalledMessage,
  isPrivateChat,
  leaveCurrentGroup,
  listItems,
  privateSearchEmpty,
  hiddenManagementMode,
  textSearchLoading,
  memberDisplayName,
  memberMuteLabel,
  memberRoleLabel,
  ownedGroupInviteVisible,
  ownedGroupSearch,
  ownedGroupPage,
  ownedGroupTotal,
  ownedGroups,
  ownedGroupsLoading,
  ownedGroupMemberText,
  invitingGroupId,
  jumpToLatest,
  onBubbleMediaLoad,
  onMessagesScroll,
  pendingNewCount,
  messageTimeline,
  messages,
  msgContainer,
  msgScrollbar,
  onChatImageFileChange,
  onConvBlur,
  onConvFocus,
  onDialogBlurRoot,
  onDialogOpened,
  onEmojiPopoverShow,
  onEmojiTabChange,
  onPackBarScroll,
  onEmojiStickerFileChange,
  onGroupAvatarFileChange,
  onGroupIntroInput,
  onGroupAdminSearchInput,
  onGroupMemberSearchInput,
  onMentionSearchInput,
  openMentionPicker,
  openOwnedGroupInvitePicker,
  toggleMentionPicker,
  toggleGroupAdminPicker,
  toggleGroupAdminRole,
  openArticleFromSystem,
  openGroupMemberProfile,
  openMessageSenderProfile,
  openCurrentPeerProfile,
  openEmojiShopFromMessage,
  openAlbumPreview,
  hidePrivateSession,
  restorePrivateSession,
  toggleHiddenManagement,
  highlightSegments,
  openGroupSettings,
  packBarCanScrollLeft,
  packBarCanScrollRight,
  packBarRef,
  paginatedFavorites,
  paginatedGroupMembers,
  paginatedMentionMembers,
  paginatedUploaded,
  pendingAlbumFiles,
  retryPendingAlbum,
  deletePendingAlbum,
  removePendingAlbumFile,
  removeEmojiKeepPopover,
  rememberBubbleNaturalSize,
  showUploadOnCurrentPage,
  parseSystemMessageContent,
  systemNotifyCardTitle,
  peerOnline,
  groupAvatarInputRef,
  groupCreateForm,
  groupCreateVisible,
  groupEditDirty,
  groupEditForm,
  groupIntroEditorRef,
  groupListError,
  groupListLoading,
  groupMembers,
  groupMembersTotal,
  groupMemberSearch,
  groupMembersPage,
  groupMembersLoading,
  groupNotifyOptions,
  groupRemarkForm,
  groupInviteInfo,
  groupInviteStatusText,
  groupSettingsVisible,
  groupSettingsPortalReady,
  groupTypeSwitchLocked,
  removeMember,
  approveJoinRequestItem,
  rejectJoinRequestItem,
  acceptInviteCard,
  declineInviteCard,
  replyTarget,
  savingGroupEdit,
  savingGroupRemark,
  scrollPackBarLeft,
  scrollPackBarRight,
  scrollToBottom,
  searchQuery,
  selectPurchasedPack,
  selectedPurchasedPack,
  selfOnline,
  selectMentionMember,
  selectMentionAll,
  selectListItem,
  groupTypeLabel,
  openCreateGroup,
  submitCreateGroup,
  submitGroupEdit,
  submitGroupRemark,
  startReply,
  canReportChatMessage,
  submitChatMessageReport,
  confirmChatMessageReport,
  switchGroupType,
  setGroupNotifyMode,
  sendContent,
  sendGroupInviteFromPm,
  sendMessageFromEmoji,
  sendMessageFromShopUrl,
  sendMsg,
  sending,
  creatingGroup,
  muteMember,
  toggleMuteMember,
  sysTagClass,
  sysTagLabel,
  tabBadges,
  triggerGroupAvatarUpload,
  triggerChatImagePick,
  triggerEmojiStickerPick,
  beforeCloseGroupSettings,
  requestCloseGroupSettings,
  clearReplyTarget,
  mentionMembersPage,
  mentionMembersTotal,
  MENTION_PAGE_SIZE,
  mentionPopoverVisible,
  mentionSearch,
  uploadedEmojis,
  uploadedPage,
  uploadedPendingSlots,
  uploadedPagerTotal,
  onUploadedPageChange,
  userStore,
  uploadingGroupAvatar,
  viewerIsVip,
  visiblePacks,
} = useMessageView()
</script>

<style src="@/assets/styles/message.css"></style>
