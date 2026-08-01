package org.pluchon.forum.converter;

import org.pluchon.forum.api.content.ArticleInternalVO;
import org.pluchon.forum.entity.db.Article;

// ArticleInternalVO ↔ Article 壳（AI 等跨域回表后本地组装）
public final class ArticleInternalConverter {

    private ArticleInternalConverter() {
    }

    public static Article toArticleShell(ArticleInternalVO vo) {
        if (vo == null) {
            return null;
        }
        Article article = new Article();
        article.setId(vo.getId());
        article.setBoardId(vo.getBoardId());
        article.setUserId(vo.getUserId());
        article.setTitle(vo.getTitle());
        article.setContent(vo.getContent());
        article.setVisitCount(vo.getVisitCount());
        article.setReplyCount(vo.getReplyCount());
        article.setLikeCount(vo.getLikeCount());
        article.setFavoriteCount(vo.getFavoriteCount());
        article.setSubReplyCount(vo.getSubReplyCount());
        article.setCoverImg(vo.getCoverImg());
        article.setMediaType(vo.getMediaType());
        article.setVideoUrl(vo.getVideoUrl());
        article.setArticleType(vo.getArticleType());
        article.setQuestionStatus(vo.getQuestionStatus());
        article.setAcceptedReplyId(vo.getAcceptedReplyId());
        article.setStatus(vo.getStatus());
        article.setState(vo.getState());
        article.setDeleteState(vo.getDeleteState());
        article.setCreateTime(vo.getCreateTime());
        article.setUpdateTime(vo.getUpdateTime());
        return article;
    }
}
