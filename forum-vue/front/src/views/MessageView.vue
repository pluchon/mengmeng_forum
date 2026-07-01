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
            <div v-if="activeTab === 'group'" class="mc-left-title-actions">
              <button
                type="button"
                class="mc-create-group-btn"
                title="创建群聊"
                @click="openCreateGroup"
              >
                <el-icon><Plus /></el-icon>
              </button>
            </div>
          </div>
          <div class="mc-tabs" role="tablist">
            <button
              type="button"
              class="mc-tab"
              :class="{ 'is-on': activeTab === 'all' }"
              @click="activeTab = 'all'"
            >
              全部
              <span v-if="tabBadges.all > 0" class="mc-tab-badge">{{ tabBadges.all > 99 ? '99+' : tabBadges.all }}</span>
            </button>
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
          <div class="mc-search">
            <el-icon class="mc-search-icon"><Search /></el-icon>
            <input
              v-model="searchQuery"
              type="search"
              class="mc-search-input"
              placeholder="搜索消息…"
            >
          </div>
        </div>

        <el-scrollbar class="mc-list-scroll">
          <button
            v-for="item in listItems"
            :key="item.key"
            type="button"
            class="mc-conv"
            :class="{
              'is-on': isActiveItem(item),
              'is-focus-ring': focusedConvKey === item.key,
              'is-group-private': item.kind === 'group' && Number(item.group?.groupType) === 1,
            }"
            @click="selectListItem(item)"
            @focus="onConvFocus(item)"
            @blur="onConvBlur(item)"
          >
            <div class="mc-conv-ava" @click.stop="openPeerProfile(item)">
              <template v-if="item.kind === 'pm'">
                <UserAvatarVip
                  :size="38"
                  :src="item.user?.avatarUrl || defaultAvatar"
                  :vip-tier="Number(item.user?.vipTier) || 0"
                  :vip-expire-at="item.user?.vipExpireAt"
                />
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
                <span class="mc-conv-name mc-conv-name--link" @click.stop="openPeerProfile(item)">{{ item.name }}</span>
                <span class="mc-conv-time">{{ formatSessionTime(item.time) }}</span>
              </div>
              <div class="mc-conv-prev" :class="{ 'is-unread': item.unread > 0 }">
                {{ item.preview }}
              </div>
              <div v-if="item.kind === 'group'" class="mc-conv-meta-row">
                <span class="mc-group-type-badge" :class="{ 'is-private': Number(item.group?.groupType) === 1 }">
                  {{ groupTypeLabel(item.group?.groupType) }}
                </span>
              </div>
            </div>
          </button>
          <div v-if="activeTab === 'group' && groupListLoading" class="mc-list-empty">
            <el-icon :size="40" color="#dcdfe6"><UserFilled /></el-icon>
          </div>
          <div v-else-if="activeTab === 'group' && groupListError" class="mc-list-empty">
            <el-icon :size="40" color="#dcdfe6"><Warning /></el-icon>
          </div>
          <div
            v-if="listItems.length === 0 && !(activeTab === 'group' && (groupListLoading || groupListError))"
            class="mc-list-empty"
          >
            <el-icon :size="48" color="#dcdfe6"><ChatLineSquare /></el-icon>
          </div>
        </el-scrollbar>

        <div class="mc-left-bottom">
          <UserAvatarVip
            :size="28"
            :src="userStore.avatarUrl || defaultAvatar"
            :vip-tier="Number(userStore.vipTier) || 0"
            :vip-expire-at="userStore.vipExpireAt"
          />
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
              <UserAvatarVip
                v-if="currentSession"
                :size="34"
                :src="currentSession.user?.avatarUrl || defaultAvatar"
                :vip-tier="Number(currentSession.user?.vipTier) || 0"
                :vip-expire-at="currentSession.user?.vipExpireAt"
              />
              <div v-else class="mc-group-avatar mc-group-avatar--head">
                <img v-if="groupAvatarUrl(currentGroupSession)" :src="groupAvatarUrl(currentGroupSession)" alt="">
                <span v-else>{{ groupAvatarText(currentGroupSession) }}</span>
              </div>
              <div class="mc-rtitle-stack">
                <span class="mc-rname">
                  {{ activeChatTitle }}
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
            <div v-if="currentSession" class="mc-online mc-online--trailing" :class="{ 'is-offline': !peerOnline }">
              <span class="mc-online-dot" />
              <span>{{ peerOnline ? '在线' : '离线' }}</span>
            </div>
          </header>

          <el-scrollbar ref="msgScrollbar" class="mc-rbody-scroll">
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
                :class="row.msg.isOwner ? 'is-me' : 'is-other'"
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
                    :src="bubbleAvatar(row.msg)"
                    :vip-tier="bubbleVipTier(row.msg)"
                    :vip-expire-at="bubbleVipExpireAt(row.msg)"
                  />
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
                    <div
                      class="mc-bbl"
                      :class="{
                        'is-me': row.msg.isOwner && !isMediaMessage(row.msg) && Number(row.msg.message?.state) !== 2,
                        'is-recalled': Number(row.msg.message?.state) === 2,
                        'is-media': isMediaMessage(row.msg) && Number(row.msg.message?.state) !== 2,
                      }"
                    >
                      <span v-if="Number(row.msg.message?.state) === 2" class="mc-recalled">
                        {{ row.msg.isOwner ? '你撤回了一条消息' : '对方撤回了一条消息' }}
                      </span>
                      <template v-else-if="isMediaMessage(row.msg)">
                        <div class="mc-media-stack">
                          <el-image
                            :src="row.msg.message.mediaUrl"
                            :preview-src-list="[row.msg.message.mediaUrl]"
                            preview-teleported
                            :z-index="10050"
                            fit="contain"
                            class="mc-chat-img"
                            :class="{ 'is-gif': Number(row.msg.message?.messageType) === 2 }"
                            :style="bubbleImageStyle(row.msg.message)"
                          />
                          <button
                            v-if="!row.msg.isOwner && canFavoriteChatImage(row.msg)"
                            type="button"
                            class="mc-fav-img-btn"
                            @click="favoriteChatImage(row.msg)"
                          >
                            添加到表情
                          </button>
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
                    <button
                      v-if="currentSession && row.msg.isOwner && Number(row.msg.message?.state) !== 2"
                      type="button"
                      class="mc-recall-btn"
                      @click="handleRecall(row.msg)"
                    >
                      撤回
                    </button>
                    <button
                      v-if="currentGroupSession && !row.msg.isOwner && Number(row.msg.message?.messageType) !== 9"
                      type="button"
                      class="mc-recall-btn"
                      @click="reportGroupMessage(row.msg)"
                    >
                      举报
                    </button>
                    <button
                      v-if="currentGroupSession && Number(row.msg.message?.messageType) !== 9"
                      type="button"
                      class="mc-reply-btn"
                      @click="startReply(row.msg)"
                    >
                      回复
                    </button>
                  </div>
                  <div class="mc-meta-row" :class="{ 'is-me': row.msg.isOwner }">
                    <span class="mc-btime">{{ formatTime(row.msg.message?.createTime) }}</span>
                    <span
                      v-if="currentSession && row.msg.isOwner && Number(row.msg.message?.state) !== 2"
                      class="mc-read"
                    >
                      {{ Number(row.msg.message?.state) === 1 ? '已读' : '未读' }}
                    </span>
                  </div>
                </div>
              </div>
              </template>
            </div>
          </el-scrollbar>

          <footer class="mc-rinput">
            <input
              v-if="currentSession || currentGroupSession"
              ref="chatImageInput"
              type="file"
              class="mc-hidden-file"
              accept="image/jpeg,image/jpg,image/png,image/gif"
              @change="onChatImageFileChange"
            >
            <input
              v-if="isPrivateChat"
              ref="emojiStickerInput"
              type="file"
              class="mc-hidden-file"
              accept="image/jpeg,image/jpg,image/png,image/gif"
              @change="onEmojiStickerFileChange"
            >
            <div class="mc-itools">
              <button
                v-if="currentSession || currentGroupSession"
                type="button"
                class="mc-itbtn"
                title="发送图片"
                @click="triggerChatImagePick"
              >
                <el-icon><Picture /></el-icon>
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
                    @
                  </button>
                </template>
                <div class="mc-mention-panel">
                  <button type="button" class="mc-mention-all" @click="selectMentionAll">
                    @所有人
                  </button>
                  <input
                    v-model="mentionSearch"
                    type="search"
                    class="mc-mention-search"
                    placeholder="搜索群成员"
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
                      :src="member.user?.avatarUrl || defaultAvatar"
                      :vip-tier="Number(member.user?.vipTier) || 0"
                      :vip-expire-at="member.user?.vipExpireAt"
                    />
                    <span>{{ memberDisplayName(member) }}</span>
                  </button>
                  <div v-if="mentionMembersTotalPages > 1" class="mc-mention-pager">
                    <button type="button" :disabled="mentionMembersPage <= 1" @click="goMentionMembersPrev">
                      上页
                    </button>
                    <span>{{ mentionMembersPage }}/{{ mentionMembersTotalPages }}</span>
                    <button
                      type="button"
                      :disabled="mentionMembersPage >= mentionMembersTotalPages"
                      @click="goMentionMembersNext"
                    >
                      下页
                    </button>
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
                    <img :src="emojiPackIconUrl" alt="" class="mc-emoji-pack-icon">
                  </button>
                </template>
                <div
                  v-loading="chatEmojiStore.loading || (emojiPanelTab === 'purchased' && emojiShopStore.myPacksLoading)"
                  class="mc-emoji-panel"
                >
                  <el-tabs v-model="emojiPanelTab" class="mc-emoji-tabs" @tab-change="onEmojiTabChange">
                    <el-tab-pane label="收藏" name="favorites">
                      <div v-if="!favoriteEmojis.length" class="mc-emoji-empty">
                        <el-icon :size="40" color="#dcdfe6"><Picture /></el-icon>
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
                              @click.stop="removeEmojiKeepPopover(em.id)"
                            >
                              <el-icon><Close /></el-icon>
                            </button>
                          </div>
                        </div>
                        <div v-if="favoriteTotalPages > 1" class="mc-emoji-fav-pager mc-emoji-fav-pager--solo">
                          <button type="button" class="mc-emoji-fav-pager-btn" @click="goFavoriteFirst">首页</button>
                          <button type="button" class="mc-emoji-fav-pager-btn" :disabled="favoritePage <= 1" @click="goFavoritePrev">上页</button>
                          <input
                            v-model="favoritePageInput"
                            type="number"
                            min="1"
                            :max="favoriteTotalPages"
                            class="mc-emoji-fav-pager-input"
                            @keyup.enter="jumpFavoritePage"
                          >
                          <span class="mc-emoji-fav-pager-sep">/ {{ favoriteTotalPages }}</span>
                          <button type="button" class="mc-emoji-fav-pager-btn" :disabled="favoritePage >= favoriteTotalPages" @click="goFavoriteNext">下页</button>
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
                              @click.stop="removeEmojiKeepPopover(em.id)"
                            >
                              <el-icon><Close /></el-icon>
                            </button>
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
                        <div v-if="uploadedTotalPages > 1" class="mc-emoji-fav-pager mc-emoji-fav-pager--solo">
                          <button type="button" class="mc-emoji-fav-pager-btn" @click="goUploadedFirst">首页</button>
                          <button type="button" class="mc-emoji-fav-pager-btn" :disabled="uploadedPage <= 1" @click="goUploadedPrev">上页</button>
                          <input
                            v-model="uploadedPageInput"
                            type="number"
                            min="1"
                            :max="uploadedTotalPages"
                            class="mc-emoji-fav-pager-input"
                            @keyup.enter="jumpUploadedPage"
                          >
                          <span class="mc-emoji-fav-pager-sep">/ {{ uploadedTotalPages }}</span>
                          <button type="button" class="mc-emoji-fav-pager-btn" :disabled="uploadedPage >= uploadedTotalPages" @click="goUploadedNext">下页</button>
                        </div>
                      </div>
                    </el-tab-pane>
                    <el-tab-pane label="已购" name="purchased">
                      <div v-if="!visiblePacks.length" class="mc-emoji-empty">
                        <el-icon :size="40" color="#dcdfe6"><Picture /></el-icon>
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
              />
              <button type="button" class="mc-sbtn" :disabled="sending" @click="sendMsg">
                {{ sending ? '发送中' : '发送' }}
              </button>
            </div>
          </footer>
        </template>

        <template v-else-if="currentSystemGroup">
          <header class="mc-rhead">
            <div class="mc-rhead-left">
              <div class="mc-sys-ava mc-sys-ava--group mc-sys-ava--head">
                <el-icon :size="18"><Document /></el-icon>
              </div>
              <span class="mc-rname">{{ currentSystemGroup.name }}</span>
            </div>
          </header>
          <el-scrollbar class="mc-rbody-scroll">
            <div class="mc-rbody mc-rbody--notify">
              <article
                v-for="msg in activeSystemMessages"
                :key="msg.id"
                class="mc-ncard"
              >
                <div class="mc-ncard-head">
                  <span :class="sysTagClass(msg.type)" class="mc-tag">{{ sysTagLabel(msg.type) }}</span>
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
        </template>

        <div v-else class="mc-welcome">
          <el-icon :size="80" color="#e5e6eb"><ChatLineRound /></el-icon>
        </div>
      </section>
    </div>
  </el-dialog>

  <el-dialog
    v-model="groupCreateVisible"
    title="创建群聊"
    width="360px"
    append-to-body
  >
    <div class="mc-group-form">
      <el-input
        v-model="groupCreateForm.name"
        maxlength="24"
        show-word-limit
        placeholder="群名称"
      />
      <el-select v-model="groupCreateForm.groupType" class="mc-group-form-control">
        <el-option label="公开群" :value="0" />
        <el-option label="私有群" :value="1" />
      </el-select>
      <el-input
        v-model="groupCreateForm.intro"
        maxlength="120"
        show-word-limit
        placeholder="群简介"
      />
    </div>
    <template #footer>
      <div class="mc-group-dialog-footer">
        <button type="button" class="mc-dialog-action" @click="groupCreateVisible = false">
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

  <el-dialog
    v-model="groupSettingsVisible"
    width="640px"
    append-to-body
    class="mc-group-settings-dialog"
    :before-close="beforeCloseGroupSettings"
  >
    <div class="mc-group-settings">
      <div class="mc-group-profile-title">
        群资料
        <span v-if="groupEditDirty" class="mc-group-save-hint">保存后生效</span>
      </div>

      <section class="mc-group-settings-section">
        <div class="mc-group-settings-head">
          <div class="mc-group-settings-title">群成员</div>
          <div class="mc-group-settings-head-right">
            <div class="mc-group-settings-subtitle">{{ groupMembers.length }} 人</div>
            <div v-if="groupMembersTotalPages > 1" class="mc-group-member-pager">
              <button type="button" :disabled="groupMembersPage <= 1" @click="goGroupMembersPrev">
                上一页
              </button>
              <span>{{ groupMembersPage }}/{{ groupMembersTotalPages }}</span>
              <button
                type="button"
                :disabled="groupMembersPage >= groupMembersTotalPages"
                @click="goGroupMembersNext"
              >
                下一页
              </button>
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
              :src="member.user?.avatarUrl || defaultAvatar"
              :vip-tier="Number(member.user?.vipTier) || 0"
              :vip-expire-at="member.user?.vipExpireAt"
            />
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
              <button type="button" class="mc-member-action is-danger" @click.stop="removeMember(member)">
                移除
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
            <span class="mc-group-field-label">群昵称：</span>
            <div class="mc-group-name-row">
              <el-input
                v-model="groupEditForm.name"
                maxlength="24"
                show-word-limit
                placeholder="群名称"
              />
              <input
                ref="groupAvatarInputRef"
                type="file"
                class="mc-hidden-file"
                accept="image/jpeg,image/png,image/gif"
                @change="onGroupAvatarFileChange"
              >
              <button
                type="button"
                class="mc-group-avatar-upload-btn"
                :disabled="uploadingGroupAvatar"
                @click="triggerGroupAvatarUpload"
              >
                {{ uploadingGroupAvatar ? '上传中' : '修改群头像' }}
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
          <button
            type="button"
            class="mc-dialog-action mc-dialog-action--primary mc-group-save-btn"
            :disabled="savingGroupEdit"
            @click="submitGroupEdit"
          >
            {{ savingGroupEdit ? '保存中' : '保存' }}
          </button>
        </div>
      </section>

      <section v-else class="mc-group-settings-section">
        <div class="mc-group-readonly-profile">
          <div class="mc-group-readonly-grid">
            <div class="mc-group-readonly-row">
              <span>群名称：</span>
              <strong>{{ currentGroupSession?.groupName || currentGroupSession?.name || '群聊' }}</strong>
            </div>
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

      <section class="mc-group-danger-zone">
        <button
          v-if="isCurrentGroupOwner"
          type="button"
          class="mc-group-danger-btn"
          @click="dissolveCurrentGroup"
        >
          解散群聊
        </button>
        <button
          v-else
          type="button"
          class="mc-group-danger-btn"
          @click="leaveCurrentGroup"
        >
          退出群聊
        </button>
      </section>
    </div>
  </el-dialog>
</template>

<script setup>
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import { useMessageView } from '@scripts/views/MessageView'

const {
  ArrowLeft,
  ArrowRight,
  ChatLineRound,
  ChatLineSquare,
  Close,
  Document,
  Picture,
  Plus,
  Search,
  Setting,
  UserFilled,
  activeSystemMessages,
  activeTab,
  activeChatSubtitle,
  activeChatTitle,
  autoResizeInput,
  bubbleAvatar,
  bubbleImageStyle,
  bubbleVipExpireAt,
  bubbleVipTier,
  canFavoriteChatImage,
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
  favoritePageInput,
  favoriteTotalPages,
  filteredMentionMembers,
  goGroupMembersNext,
  goGroupMembersPrev,
  goMentionMembersNext,
  goMentionMembersPrev,
  focusedConvKey,
  formatSessionTime,
  formatTime,
  goFavoriteFirst,
  goFavoriteNext,
  goFavoritePrev,
  groupAvatarText,
  groupAvatarUrl,
  handleClose,
  handleRecall,
  inputBoxRef,
  isActiveItem,
  isCurrentGroupOwner,
  isMemberMuted,
  isMediaMessage,
  isPrivateChat,
  leaveCurrentGroup,
  listItems,
  memberDisplayName,
  memberMuteLabel,
  memberRoleLabel,
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
  jumpFavoritePage,
  jumpUploadedPage,
  onPackBarScroll,
  onEmojiStickerFileChange,
  onGroupAvatarFileChange,
  onGroupIntroInput,
  openMentionPicker,
  toggleMentionPicker,
  openArticleFromSystem,
  openGroupMemberProfile,
  openMessageSenderProfile,
  openPeerProfile,
  openGroupSettings,
  packBarCanScrollLeft,
  packBarCanScrollRight,
  packBarRef,
  paginatedFavorites,
  paginatedGroupMembers,
  paginatedMentionMembers,
  paginatedUploaded,
  removeEmojiKeepPopover,
  showUploadOnCurrentPage,
  parseSystemMessageContent,
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
  groupMembersPage,
  groupMembersLoading,
  groupMembersTotalPages,
  groupNotifyOptions,
  groupRemarkForm,
  groupSettingsVisible,
  groupTypeSwitchLocked,
  removeMember,
  reportGroupMessage,
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
  switchGroupType,
  setGroupNotifyMode,
  sendContent,
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
  clearReplyTarget,
  mentionMembersPage,
  mentionMembersTotalPages,
  mentionPopoverVisible,
  mentionSearch,
  uploadedEmojis,
  uploadedPage,
  uploadedPageInput,
  uploadedTotalPages,
  goUploadedFirst,
  goUploadedNext,
  goUploadedPrev,
  userStore,
  uploadingGroupAvatar,
  viewerIsVip,
  visiblePacks,
} = useMessageView()
</script>

<style src="@/assets/styles/message.css"></style>
