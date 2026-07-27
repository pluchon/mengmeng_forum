import { DEFAULT_AVATAR } from '@/utils/constants'

export function useMascotRelatedArticlesDialog(_props, emit) {
  function coverStyle(article) {
    if (article?.coverImg) {
      return {
        backgroundImage: `url(${article.coverImg})`,
        backgroundSize: 'cover',
        backgroundPosition: 'center',
      }
    }
    return { background: 'hsl(330, 70%, 94%)' }
  }

  function openArticle(articleId) {
    if (!articleId) return
    emit('open-article', articleId)
  }

  return {
    DEFAULT_AVATAR,
    coverStyle,
    openArticle,
  }
}
