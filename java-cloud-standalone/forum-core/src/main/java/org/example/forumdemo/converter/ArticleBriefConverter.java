package org.example.forumdemo.converter;

import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.entity.vo.article.ArticleBriefVO;

// 跨域（如 mascot）所需的帖子摘要转换，完整 ArticleConverter 在 content 模块
public final class ArticleBriefConverter {

    private ArticleBriefConverter() {
    }

    public static ArticleBriefVO toBriefVO(Article article) {
        if (article == null) {
            return null;
        }
        ArticleBriefVO vo = new ArticleBriefVO();
        vo.setId(article.getId());
        vo.setBoardId(article.getBoardId());
        vo.setUserId(article.getUserId());
        vo.setTitle(article.getTitle());
        vo.setContent(article.getContent());
        vo.setVisitCount(article.getVisitCount());
        vo.setReplyCount(article.getReplyCount());
        vo.setLikeCount(article.getLikeCount());
        vo.setCoverImg(article.getCoverImg());
        vo.setMediaType(article.getMediaType());
        vo.setVideoUrl(article.getVideoUrl());
        vo.setFavoriteCount(article.getFavoriteCount());
        vo.setArticleType(article.getArticleType());
        vo.setQuestionStatus(article.getQuestionStatus());
        vo.setAcceptedReplyId(article.getAcceptedReplyId());
        vo.setStatus(article.getStatus());
        vo.setState(article.getState());
        vo.setCreateTime(article.getCreateTime());
        vo.setUpdateTime(article.getUpdateTime());
        return vo;
    }
}
