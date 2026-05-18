import { ref, shallowRef, onBeforeUnmount, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { openImageUploadLoading, validateLocalImageFile } from '@/utils/imageUploadFeedback'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import '@wangeditor/editor/dist/css/style.css'
import '@/assets/styles/components.css'
import { uploadArticleImage } from '@/api/article'

/** 发帖场景精简工具栏：粗体/斜体/下划线/删除线、颜色、列表、链接、撤销重做 */
const TOOLBAR_KEYS_SLIM = [
  'bold', 'italic', 'underline', 'through', '|',
  'color', '|',
  'bulletedList', 'numberedList', '|',
  'insertLink', '|',
  'undo', 'redo',
]

export function useWangEditor(props, emit) {
  const editorHeight = computed(() => props.minHeight)

  const editorRef = shallowRef()
  const content = ref(props.modelValue)

  const toolbarConfig = computed(() => {
    if (props.toolbarSlim || props.toolbarSuppressImage) {
      return { toolbarKeys: TOOLBAR_KEYS_SLIM }
    }
    return {
      excludeKeys: ['fullScreen', 'insertVideo', 'uploadVideo', 'codeBlock', 'group-video'],
    }
  })

  const editorConfig = computed(() => {
    const base = {
      placeholder: props.placeholder,
    }

    if (props.toolbarSuppressImage) {
      return {
        ...base,
        customPaste(editor, event) {
          const items = event.clipboardData?.items
          if (items) {
            for (let i = 0; i < items.length; i++) {
              if (items[i].type?.startsWith('image/')) {
                ElMessage.warning('富文本模式不支持插入图片，请使用下方笔记相册')
                return false
              }
            }
          }
          return true
        },
      }
    }

    return {
      ...base,
      MENU_CONF: {
        uploadImage: {
          async customUpload(file, insertFn) {
            const pre = validateLocalImageFile(file)
            if (!pre.ok) {
              ElMessage.warning(pre.message)
              return
            }
            const loading = openImageUploadLoading(file, '正在上传图片…')
            try {
              const res = await uploadArticleImage(file)
              if (res.code === 0 && res.data) {
                insertFn(res.data, '文章图片', res.data)
              } else {
                console.error('上传失败:', res.message)
              }
            } catch (err) {
              console.error('图片上传异常:', err)
            } finally {
              loading.close()
            }
          },
        },
      },
    }
  })

  function handleChange(editor) {
    let html = editor.getHtml()
    html = html.replace(/<p>\s*<br\s*\/?>\s*<\/p>/gi, '')
    emit('update:modelValue', html)
  }

  function handleCreated(editor) {
    editorRef.value = editor
    if (props.modelValue) {
      editor.setHtml(props.modelValue)
    }
  }

  watch(() => props.modelValue, (newVal) => {
    if (newVal && editorRef.value) {
      editorRef.value.setHtml(newVal)
    }
  }, { immediate: false })

  onBeforeUnmount(() => {
    editorRef.value?.destroy()
  })

  return {
    Editor,
    Toolbar,
    content,
    editorConfig,
    editorHeight,
    editorRef,
    handleChange,
    handleCreated,
    toolbarConfig,
  }
}
