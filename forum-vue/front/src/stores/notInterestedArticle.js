import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

export const useNotInterestedArticleStore = defineStore('notInterestedArticle', () => {
  const articleIds = ref([])
  const articleIdSet = computed(() => new Set(articleIds.value))

  function markNotInterested(articleId) {
    const id = Number(articleId)
    if (!Number.isFinite(id) || id <= 0 || articleIdSet.value.has(id)) return
    articleIds.value = [...articleIds.value, id]
  }

  function restoreInterested(articleId) {
    const id = Number(articleId)
    articleIds.value = articleIds.value.filter(item => item !== id)
  }

  function syncFeedbackState(articleId, isNotInterested) {
    if (isNotInterested) {
      markNotInterested(articleId)
      return
    }
    restoreInterested(articleId)
  }

  function isNotInterested(articleId) {
    return articleIdSet.value.has(Number(articleId))
  }

  return {
    articleIds,
    isNotInterested,
    markNotInterested,
    restoreInterested,
    syncFeedbackState,
  }
})
