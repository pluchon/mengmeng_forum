package org.example.forumdemo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.forum.api.content.ArticleInternalApi;
import org.example.forum.api.content.ArticleInternalVO;
import org.example.forumdemo.common.enums.ArticleStatus;
import org.example.forumdemo.common.utils.SearchKeywordHelper;
import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.mapper.ArticleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

// 帖子内部接口：契约路径已是 /article/internal/**，勿叠加类级前缀
@RestController
public class ArticleInternalController implements ArticleInternalApi {

    private static final byte DELETE_TRUE = 1;
    private static final byte STATE_FORBIDDEN = 1;

    @Autowired
    private ArticleMapper articleMapper;

    @Override
    public List<ArticleInternalVO> listByIds(@RequestParam("ids") List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> distinct = ids.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (distinct.isEmpty()) {
            return Collections.emptyList();
        }
        return articleMapper.selectList(new LambdaQueryWrapper<Article>()
                        .in(Article::getId, distinct)
                        .ne(Article::getDeleteState, DELETE_TRUE))
                .stream()
                .map(this::toVo)
                .collect(Collectors.toList());
    }

    @Override
    public List<ArticleInternalVO> searchCandidates(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "limit", defaultValue = "40") Integer limit) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        int lim = limit == null || limit <= 0 ? 40 : Math.min(limit, 80);
        List<String> terms = SearchKeywordHelper.expandTerms(keyword.trim());
        LambdaQueryWrapper<Article> query = new LambdaQueryWrapper<Article>()
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .and(w -> {
                    boolean first = true;
                    for (String term : terms) {
                        if (first) {
                            w.like(Article::getTitle, term);
                            first = false;
                        } else {
                            w.or().like(Article::getTitle, term);
                        }
                        w.or().like(Article::getContent, term);
                    }
                })
                .orderByDesc(Article::getUpdateTime);
        return articleMapper.selectPage(new Page<>(1, lim, false), query).getRecords()
                .stream()
                .map(this::toVo)
                .collect(Collectors.toList());
    }

    private ArticleInternalVO toVo(Article article) {
        ArticleInternalVO vo = new ArticleInternalVO();
        vo.setId(article.getId());
        vo.setBoardId(article.getBoardId());
        vo.setUserId(article.getUserId());
        vo.setTitle(article.getTitle());
        vo.setContent(article.getContent());
        vo.setVisitCount(article.getVisitCount());
        vo.setReplyCount(article.getReplyCount());
        vo.setLikeCount(article.getLikeCount());
        vo.setFavoriteCount(article.getFavoriteCount());
        vo.setSubReplyCount(article.getSubReplyCount());
        vo.setCoverImg(article.getCoverImg());
        vo.setMediaType(article.getMediaType());
        vo.setVideoUrl(article.getVideoUrl());
        vo.setArticleType(article.getArticleType());
        vo.setQuestionStatus(article.getQuestionStatus());
        vo.setAcceptedReplyId(article.getAcceptedReplyId());
        vo.setStatus(article.getStatus());
        vo.setState(article.getState());
        vo.setDeleteState(article.getDeleteState());
        vo.setCreateTime(article.getCreateTime());
        vo.setUpdateTime(article.getUpdateTime());
        return vo;
    }
}
