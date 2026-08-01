package org.example.forumdemo.converter;

import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.entity.vo.article.ArticleBriefVO;
import org.example.forumdemo.entity.vo.article.ArticleValidateTextVO;
import org.example.forumdemo.entity.vo.common.PageResult;

import java.util.ArrayList;
import java.util.List;

// 帖子实体与 VO 转换
public final class ArticleConverter {

    private ArticleConverter() {
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

    public static List<ArticleBriefVO> toBriefVOList(List<Article> articles) {
        if (articles == null || articles.isEmpty()) {
            return List.of();
        }
        List<ArticleBriefVO> list = new ArrayList<>(articles.size());
        for (Article article : articles) {
            list.add(toBriefVO(article));
        }
        return list;
    }

    public static PageResult<ArticleBriefVO> toBriefPage(PageResult<Article> page) {
        if (page == null) {
            return new PageResult<>(List.of(), 0L, 1, 10, 0L, false);
        }
        return new PageResult<>(
                toBriefVOList(page.getRecords()),
                page.getTotal(),
                page.getPageNum(),
                page.getPageSize(),
                page.getPages(),
                page.getHasNextPage());
    }

    public static ArticleValidateTextVO toValidateTextVO(boolean allowed, String reason) {
        ArticleValidateTextVO vo = new ArticleValidateTextVO();
        vo.setIsAllowed(allowed);
        vo.setReason(reason);
        return vo;
    }
}
