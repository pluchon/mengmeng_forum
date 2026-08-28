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
        placeholder="搜索用户昵称"
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
            :src="item.user?.avatarUrl || defaultAvatar"          />
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

      <div v-if="!loading && items.length === 0" class="follow-list-empty">
        <img :src="emptyImage" alt="" class="follow-list-empty-image">
        <p>暂无数据</p>
      </div>
    </div>

    <div v-if="total > PAGE_SIZE" class="follow-list-pagination">
      <AppPagination
        v-model:current-page="pageNum"
        size="small"
        :total="total"
        :page-size="PAGE_SIZE"
        :pager-count="5"
        :show-jumper="false"
        @current-change="fetchPage"
      />
    </div>
  </el-dialog>
</template>

<script setup src="@scripts/components/UserFollowListDialog.js"></script>

<style scoped src="./UserFollowListDialog.css"></style>
