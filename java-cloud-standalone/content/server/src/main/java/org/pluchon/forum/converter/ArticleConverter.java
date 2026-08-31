package org.pluchon.forum.converter;

import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.vo.article.ArticleBriefVO;
import org.pluchon.forum.entity.vo.common.PageResult;

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
        vo.setAuditResultMessage(article.getAuditResultMessage());
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

    /**
     * 抹掉只该给作者本人看的字段。
     *
     * <p>审核评语是审核系统写给作者的，内容不受控——AI 的判定理由、人工审核的备注
     * 都会落在这里。它不该跟着帖子出现在热帖榜、收藏夹或别人的主页上，
     * 而热帖榜是连游客都能拿到的。
     */
    public static ArticleBriefVO stripAuthorOnlyFields(ArticleBriefVO vo) {
        if (vo != null) {
            vo.setAuditResultMessage(null);
        }
        return vo;
    }

    public static void stripAuthorOnlyFields(java.util.Collection<ArticleBriefVO> list) {
        if (list == null) {
            return;
        }
        list.forEach(ArticleConverter::stripAuthorOnlyFields);
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

}
