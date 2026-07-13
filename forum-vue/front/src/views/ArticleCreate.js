import { InfoFilled, Picture, VideoCamera } from '@element-plus/icons-vue'
import ArticleCreateGallerySection from '@/components/article/ArticleCreateGallerySection.vue'
import ArticleCreateVideoSection from '@/components/article/ArticleCreateVideoSection.vue'
import ArticleAiWriteAssist from '@/components/article/ArticleAiWriteAssist.vue'
import ArticleTagEditor from '@/components/article/ArticleTagEditor.vue'
import { useArticleCreate } from '@scripts/views/ArticleCreate'
import { ARTICLE_TYPE } from '@/utils/articleQuestion'

const {
  WangEditor,
  applyAiContent,
  bindGalleryItemsRef,
  canAddGallery,
  cascaderOptions,
  editorMode,
  form,
  galleryInputRef,
  galleryMaxCount,
  galleryStripFadeLeft,
  galleryStripOverflow,
  galleryUrls,
  mediaMode,
  videoUrl,
  videoUploading,
  videoUploadProgress,
  videoUploadError,
  galleryUploading,
  videoInputRef,
  handleBoardChange,
  handleCancel,
  handleMdFileSelected,
  handleMdInsertImage,
  handlePublish,
  handleSaveDraft,
  isEdit,
  mdFileInput,
  mdTextareaRef,
  mdWrap,
  onGalleryFilesSelected,
  openGalleryPicker,
  openVideoPicker,
  removeVideo,
  onVideoFileSelected,
  removeGalleryAt,
  renderedPreview,
  selectedBoard,
  setEditorMode,
  setMediaMode,
  onMdKeydown,
  submitting,
  tagIds,
  updateGalleryStripState,
} = useArticleCreate()
