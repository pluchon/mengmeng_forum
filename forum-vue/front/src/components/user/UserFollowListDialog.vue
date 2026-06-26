<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="520px"
    class="follow-list-dialog"
    destroy-on-close
    @closed="closeList"
  >
    <div class="follow-list-search">
      <el-input
        v-model="keyword"
        clearable
        placeholder="在列表内搜索昵称或简介"
        @input="onKeywordInput"
        @clear="clearKeyword"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
    </div>

    <div v-loading="loading" class="follow-list-body">
      <div
        v-for="item in items"
        :key="item.user?.id"
        class="follow-list-row"
      >
        <button
          type="button"
          class="follow-list-user"
          @click="goProfile(item)"
        >
          <UserAvatarVip
            :size="48"
            :src="item.user?.avatarUrl || defaultAvatar"
            :vip-tier="Number(item.user?.vipTier) || 0"
            :vip-expire-at="item.user?.vipExpireAt"
          />
          <div class="follow-list-text">
            <div class="follow-list-name">{{ item.user?.nickname || '匿名用户' }}</div>
            <div class="follow-list-remark">{{ remarkSummary(item.user?.remark) }}</div>
          </div>
        </button>

        <el-button
          v-if="showActionButton(item)"
          round
          size="small"
          class="follow-list-action"
          :type="buttonType(item)"
          :loading="actionSavingId === item.user?.id"
          @click.stop="toggleRowFollow(item)"
        >
          {{ buttonLabel(item) }}
        </el-button>
      </div>

      <el-empty
        v-if="!loading && items.length === 0"
        :description="keyword.trim() ? '没有匹配的用户' : '暂无数据'"
      />
    </div>

    <div v-if="total > PAGE_SIZE" class="follow-list-pagination">
      <el-pagination
        v-model:current-page="pageNum"
        :total="total"
        :page-size="PAGE_SIZE"
        layout="prev, pager, next"
        background
        small
        @current-change="fetchPage"
      />
    </div>
  </el-dialog>
</template>

<script setup src="@scripts/components/UserFollowListDialog.js"></script>

<style scoped src="./UserFollowListDialog.css"></style>
