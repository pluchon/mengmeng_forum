<template>
  <div class="wang-editor-wrapper" :style="{ minHeight }">
    <Toolbar
      :key="toolbarSuppressImage ? 'tb-no-img' : 'tb-full'"
      :editor="editorRef"
      :defaultConfig="toolbarConfig"
      mode="simple"
      class="wang-toolbar"
    />
    <Editor :defaultConfig="editorConfig" v-model="content" mode="simple" class="wang-editor"
      :style="{ height: editorHeight }"
      @onCreated="handleCreated" @onChange="handleChange" />
  </div>
</template>

<script setup>
import { useWangEditor } from '@scripts/components/common/WangEditor'

const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '请输入内容...' },
  minHeight: { type: String, default: '400px' },
  /** 隐藏图片相关能力（发帖富文本：禁止插入/粘贴图片） */
  toolbarSuppressImage: { type: Boolean, default: false },
  /** 使用精简工具栏（发帖推荐） */
  toolbarSlim: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue'])

const {
  Editor,
  Toolbar,
  content,
  editorConfig,
  editorHeight,
  editorRef,
  handleChange,
  handleCreated,
  toolbarConfig,
} = useWangEditor(props, emit)
</script>

