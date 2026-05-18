<template>
  <div class="red-home-container">
    <div class="main-content">
      <el-card class="animate-fade-up" shadow="never" :body-style="{ padding: '14px 16px' }">
        <div class="head-row">
          <div class="head-title">收藏夹内容</div>
          <el-button text type="primary" @click="$router.push('/favorites')">返回我的收藏夹</el-button>
        </div>
      </el-card>

      <div v-loading="loading" class="fav-items">
        <el-card
          v-for="item in records"
          :key="item.article?.id"
          class="fav-item-card animate-fade-up"
          shadow="hover"
          @click="$router.push(`/article/${item.article?.id}`)"
        >
          <div class="item-title">{{ item.article?.title }}</div>
          <div class="item-meta">
            <span>{{ item.author?.nickname || '匿名用户' }}</span>
            <span class="dot">·</span>
            <span>收藏于 {{ item.favoriteTime || '' }}</span>
          </div>
          <div class="item-actions" @click.stop>
            <el-button size="small" round @click="openMove(item)">移动到…</el-button>
          </div>
        </el-card>

        <el-empty v-if="!loading && records.length === 0" description="这个收藏夹里还没有内容" />
      </div>

      <div v-if="total > pageSize" class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          :total="total"
          :page-size="pageSize"
          layout="prev, pager, next, jumper"
          background
          @current-change="loadArticles"
        />
      </div>

      <el-dialog v-model="moveDialogVisible" title="移动到收藏夹" width="420px" class="red-dialog">
        <el-form label-width="96px">
          <el-form-item label="目标收藏夹">
            <el-select
              v-model="moveToFolderId"
              placeholder="请选择"
              style="width: 100%"
              :loading="foldersLoading"
              filterable
            >
              <el-option v-for="f in myFolders" :key="f.id" :label="f.name" :value="f.id" />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="moveDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="moving" @click="confirmMove">确定</el-button>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<script setup>
import { useFavoriteFolder } from '@scripts/views/FavoriteFolder'

const {
  confirmMove,
  foldersLoading,
  loadArticles,
  loading,
  moveDialogVisible,
  moveToFolderId,
  moving,
  myFolders,
  openMove,
  pageNum,
  pageSize,
  records,
  total,
} = useFavoriteFolder()
</script>

<style scoped>
.head-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.head-title {
  font-weight: 900;
  font-size: 16px;
  color: #1d2129;
}
.fav-items {
  margin-top: 12px;
}
.fav-item-card {
  margin-top: 10px;
  border-radius: 14px;
  position: relative;
}
.item-title {
  font-weight: 900;
  font-size: 15px;
  color: #1d2129;
}
.item-meta {
  margin-top: 8px;
  font-size: 12px;
  color: #86909c;
}
.dot {
  margin: 0 6px;
}
.item-actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}
</style>

