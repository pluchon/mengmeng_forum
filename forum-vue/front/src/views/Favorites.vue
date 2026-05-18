<template>
  <div class="red-home-container">
    <div class="main-content">
      <el-card class="animate-fade-up" shadow="never" :body-style="{ padding: '14px 16px' }">
        <div class="fav-head">
          <div class="fav-title">我的收藏夹</div>
          <el-button type="primary" round :icon="PlusIcon" @click="openCreate">新建</el-button>
        </div>
      </el-card>

      <div v-loading="loading" class="fav-list">
        <el-card
          v-for="folder in folders"
          :key="folder.id"
          class="fav-folder-card animate-fade-up"
          shadow="hover"
          @click="openFolder(folder)"
        >
          <div class="folder-row">
            <div class="folder-left">
              <div class="folder-name">
                {{ folder.name }}
                <el-tag v-if="Number(folder.isDefault) === 1" size="small" type="danger" effect="light" round class="ml-8">
                  默认
                </el-tag>
              </div>
              <div class="folder-meta">
                <el-icon class="mr-4">
                  <UnlockIcon v-if="Number(folder.isPublic) === 1" />
                  <LockIcon v-else />
                </el-icon>
                <span>{{ Number(folder.isPublic) === 1 ? '公开' : '私密' }}</span>
                <span class="dot">·</span>
                <span>{{ folder.itemCount ?? 0 }} 条</span>
              </div>
            </div>

            <div class="folder-actions" @click.stop>
              <el-button text type="primary" :icon="EditIcon" @click="openEdit(folder)">编辑</el-button>
              <el-button
                v-if="Number(folder.isDefault) !== 1"
                text
                type="danger"
                :icon="DeleteIcon"
                @click="removeFolder(folder)"
              >
                删除
              </el-button>
            </div>
          </div>
        </el-card>

        <el-empty v-if="!loading && folders.length === 0" description="还没有收藏夹，先建一个吧" />
      </div>

      <el-dialog v-model="createDialogVisible" :title="dialogTitle" width="420px" class="red-dialog">
        <el-form label-width="84px">
          <el-form-item label="名称">
            <el-input v-model="folderForm.name" maxlength="50" show-word-limit placeholder="输入收藏夹名称" />
          </el-form-item>
          <el-form-item label="可见性">
            <el-radio-group v-model="folderForm.isPublic">
              <el-radio :value="1">公开</el-radio>
              <el-radio :value="0">私密</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="createDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="saveFolder">保存</el-button>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<script setup>
import { useFavorites } from '@scripts/views/Favorites'

const {
  DeleteIcon,
  EditIcon,
  LockIcon,
  PlusIcon,
  UnlockIcon,
  createDialogVisible,
  dialogTitle,
  folderForm,
  folders,
  loading,
  openCreate,
  openEdit,
  openFolder,
  removeFolder,
  saveFolder,
  saving,
} = useFavorites()
</script>

<style scoped>
.fav-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.fav-title {
  font-weight: 900;
  font-size: 16px;
  color: #1d2129;
}

.fav-list {
  margin-top: 12px;
}

.fav-folder-card {
  margin-top: 10px;
  border-radius: 14px;
}

.folder-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.folder-name {
  font-weight: 900;
  color: #1d2129;
}

.folder-meta {
  margin-top: 6px;
  font-size: 12px;
  color: #86909c;
  display: flex;
  align-items: center;
}

.dot {
  margin: 0 8px;
}

.ml-8 {
  margin-left: 8px;
}

.mr-4 {
  margin-right: 4px;
}

.folder-actions {
  display: flex;
  gap: 6px;
}
</style>

