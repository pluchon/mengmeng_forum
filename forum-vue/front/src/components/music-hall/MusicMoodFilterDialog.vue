<template>
  <div v-if="modelValue" class="mood-filter__mask" @click.self="close">
    <div class="mood-filter" role="dialog" aria-modal="true" aria-label="筛选氛围标签">
      <div class="mood-filter__head">
        <span aria-hidden="true" />
        <div class="mood-filter__title">筛选标签</div>
        <button type="button" class="mood-filter__close" aria-label="关闭" @click="close">
          <el-icon><Close /></el-icon>
        </button>
      </div>

      <div class="mood-filter__search">
        <el-input
          v-model="keyword"
          placeholder="搜索标签"
          clearable
          maxlength="8"
          @input="onKeywordChange"
          @clear="onKeywordChange"
        />
      </div>

      <div class="mood-filter__body">
        <div v-if="loading" class="mood-filter__state">加载中...</div>
        <div v-else-if="!options.length" class="mood-filter__state">没有匹配的标签</div>
        <div v-else class="mood-filter__grid">
          <button
            v-for="item in options"
            :key="`mf-${item.name}`"
            type="button"
            class="mood-filter__chip"
            :class="{ 'is-active': draft.includes(item.name) }"
            @click="toggle(item.name)"
          >
            {{ item.name }}
            <i v-if="item.source === 'AI'" class="mood-filter__chip-badge">AI</i>
          </button>
        </div>
      </div>

      <div class="mood-filter__pager">
        <AppPagination
          :current-page="pageNum"
          :total="pageTotal"
          :page-size="1"
          size="small"
          :pager-count="5"
          :show-jumper="false"
          :hide-on-single-page="false"
          :disabled="loading"
          @current-change="onPageChange"
        />
      </div>

      <div class="mood-filter__foot">
        <span class="mood-filter__count">已选 {{ draft.length }} / {{ MAX_MOODS }}</span>
        <div class="mood-filter__actions">
          <button type="button" class="mood-filter__reset" :disabled="!draft.length" @click="clearAll">
            清空
          </button>
          <button type="button" class="mood-filter__save" @click="save">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup src="./MusicMoodFilterDialog.js"></script>
<style lang="scss" src="./MusicMoodFilterDialog.scss"></style>
