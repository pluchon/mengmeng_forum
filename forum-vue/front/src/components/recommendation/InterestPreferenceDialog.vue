<template>
  <el-dialog
    :model-value="visible"
    class="interest-preference-dialog"
    modal-class="interest-preference-dialog-overlay"
    append-to-body
    width="min(760px, calc(100vw - 32px))"
    :z-index="10000"
    :close-on-click-modal="!saving"
    :close-on-press-escape="!saving"
    @update:model-value="emit('update:visible', $event)"
  >
    <template #header>
      <div class="interest-preference-dialog__header">
        <span class="interest-preference-dialog__eyebrow">内容偏好</span>
        <h2 class="interest-preference-dialog__title">内容偏好</h2>
        <p class="interest-preference-dialog__description">选择你感兴趣的内容，系统也会参考收藏、回复和关注。</p>
      </div>
    </template>

    <div v-loading="loading" class="interest-preference-dialog__body">
      <el-result
        v-if="error"
        icon="error"
        title="内容偏好加载失败"
        :sub-title="error"
      >
        <template #extra>
          <el-button type="primary" @click="emit('retry')">重试</el-button>
        </template>
      </el-result>

      <template v-else-if="categories.length">
        <div class="interest-preference-dialog__summary">
          <span>已选择 <strong>{{ selectedCount }}</strong> / {{ maximumSelection }} 项</span>
          <span class="interest-preference-dialog__hint">可随时在设置中修改</span>
        </div>

        <div class="interest-preference-dialog__groups">
          <section
            v-for="(item, index) in categories"
            :key="item.category.id"
            class="interest-preference-dialog__group"
          >
            <div class="interest-preference-dialog__group-head">
              <span class="interest-preference-dialog__group-icon" aria-hidden="true">{{ categoryIcon(item, index) }}</span>
              <div>
                <h3>{{ item.category.name }}</h3>
                <p>{{ groupDescription(item) }}</p>
              </div>
            </div>
            <el-checkbox-group v-model="selectedBoardIds" :disabled="saving">
              <el-checkbox
                v-for="board in item.boardList || []"
                :key="board.id"
                :value="Number(board.id)"
                class="interest-preference-dialog__option"
                :disabled="isBoardSelectionDisabled(board.id)"
              >
                {{ board.name }}
              </el-checkbox>
            </el-checkbox-group>
          </section>
        </div>
      </template>

      <el-empty v-else-if="!loading" :image-size="64" description="暂无可选择的内容偏好" />
    </div>

    <template #footer>
      <div class="interest-preference-dialog__footer">
        <span>不确定也没关系，之后的互动会继续优化推荐。</span>
        <el-button type="primary" round :loading="saving" :disabled="loading || !!error" @click="emit('save')">
          保存偏好
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup src="./InterestPreferenceDialog.js"></script>
<style scoped lang="scss" src="./InterestPreferenceDialog.scss"></style>
