<template>
  <div
    class="article-tag-editor"
    :class="{
      'article-tag-editor--compact': compact,
      'is-ai-generating': aiLoading,
    }"
  >
    <div class="article-tag-editor__head">
      <span v-if="!compact" class="article-tag-editor__label">{{ label }}</span>
      <button
        type="button"
        class="article-tag-editor__ai"
        :disabled="aiLoading || !boardId"
        @click="runSuggest"
      >
        {{ aiLoading ? '推荐中…' : 'AI 推荐' }}
      </button>
      <button type="button" class="article-tag-editor__add" :aria-label="compact ? '添加标签' : undefined" @click="openPicker">
        <el-icon v-if="compact"><Plus /></el-icon>
        <template v-else>+ 添加标签</template>
      </button>
    </div>
    <div v-if="selectedTags.length" class="article-tag-editor__chips">
      <span
        v-for="t in selectedTags"
        :key="t.id"
        class="article-tag-chip"
        :class="`article-tag-chip--${t.colorKey || 'sky'}`"
      >
        {{ t.name }}
        <button type="button" class="article-tag-chip__x" aria-label="移除" @click="removeTag(t.id)">×</button>
      </span>
    </div>
    <p v-else-if="!compact" class="article-tag-editor__hint">最多选择 5 个标签，便于他人发现你的帖子</p>

    <el-dialog v-model="pickerOpen" width="640px" append-to-body class="article-tag-picker-dialog" :show-close="false">
      <template #header="{ titleId, titleClass }">
        <h3 :id="titleId" :class="[titleClass, 'article-tag-picker__title']">选择标签</h3>
      </template>
      <div class="article-tag-picker__content">
        <div v-loading="loading" class="article-tag-picker">
          <button
            v-for="t in availableTags"
            :key="t.id"
            type="button"
            class="article-tag-chip article-tag-chip--pick"
            :class="[
              `article-tag-chip--${t.colorKey || 'sky'}`,
              { 'is-on': isSelected(t.id) },
            ]"
            :disabled="!isSelected(t.id) && selectedIds.length >= max"
            @click="toggleTag(t)"
          >
            {{ t.name }}
          </button>
          <el-empty
            v-if="!loading && availableTags.length === 0"
            description="暂无可选标签"
            :image-size="48"
          />
        </div>
        <AppPagination
          class="article-tag-picker__pagination"
          size="small"
          :current-page="tagPageNum"
          :page-size="tagPageSize"
          :total="tagTotal"
          :pager-count="5"
          :show-jumper="false"
          @current-change="handleTagPageChange"
        />
      </div>
      <div class="article-tag-feedback">
        <span class="article-tag-feedback__label">添加新标签</span>
        <div class="article-tag-feedback__input-wrap">
          <el-input
            v-model="feedbackName"
            size="small"
            maxlength="12"
            placeholder="输入新标签名"
            class="article-tag-feedback__input"
            @keyup.enter="submitFeedback"
          />
          <el-popover placement="top" trigger="click" width="196">
            <template #reference>
              <button
                type="button"
                class="article-tag-color-trigger"
                aria-label="选择标签颜色"
                :style="{ '--tag-color': TAG_COLORS.find((item) => item.key === feedbackColor)?.hex }"
              />
            </template>
            <div class="article-tag-color-palette" aria-label="标签颜色">
              <button
                v-for="color in TAG_COLORS"
                :key="color.key"
                type="button"
                class="article-tag-color-option"
                :class="{ 'is-active': feedbackColor === color.key }"
                :aria-label="color.label"
                :style="{ '--tag-color': color.hex }"
                @click="selectFeedbackColor(color.key)"
              />
            </div>
          </el-popover>
        </div>
        <el-button
          class="article-tag-feedback__submit"
          size="small"
          :loading="feedbackLoading"
          @click="submitFeedback"
        >
          提交反馈
        </el-button>
        <el-input
          v-model="searchKeyword"
          size="small"
          clearable
          placeholder="搜索已有标签"
          class="article-tag-feedback__search"
          @input="handleSearchInput"
          @clear="handleSearchInput"
        />
      </div>
      <template #footer>
        <el-button @click="pickerOpen = false">取消</el-button>
        <el-button class="article-tag-picker__done" @click="pickerOpen = false">完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup src="./ArticleTagEditor.js"></script>

<style scoped src="./ArticleTagEditor.scss"></style>
