package org.pluchon.forum.service.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.pluchon.forum.api.content.ArticleInternalVO;
import org.pluchon.forum.common.enums.ArticleStatus;
import org.pluchon.forum.common.utils.SearchKeywordHelper;
import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.db.ArticleLike;
import org.pluchon.forum.entity.db.UserMusicFavorite;
import org.pluchon.forum.mapper.ArticleLikeMapper;
import org.pluchon.forum.mapper.ArticleMapper;
import org.pluchon.forum.mapper.UserMusicFavoriteMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

// 内容域内部帖子查询服务
@Service
public class ArticleInternalReadService {

    private static final byte DELETE_TRUE = 1;
    private static final byte STATE_FORBIDDEN = 1;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private ArticleLikeMapper articleLikeMapper;

    @Autowired
    private UserMusicFavoriteMapper userMusicFavoriteMapper;

    public List<ArticleInternalVO> listByIds(List<Long> ids) {
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

    public List<ArticleInternalVO> searchCandidates(String keyword, Integer limit) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        int size = limit == null || limit <= 0 ? 40 : Math.min(limit, 80);
        List<String> terms = SearchKeywordHelper.expandTerms(keyword.trim());
        LambdaQueryWrapper<Article> query = new LambdaQueryWrapper<Article>()
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .and(wrapper -> {
                    boolean first = true;
                    for (String term : terms) {
                        if (first) {
                            wrapper.like(Article::getTitle, term);
                            first = false;
                        } else {
                            wrapper.or().like(Article::getTitle, term);
                        }
                        wrapper.or().like(Article::getContent, term);
                    }
                })
                .orderByDesc(Article::getUpdateTime);
        return articleMapper.selectPage(new Page<>(1, size, false), query).getRecords()
                .stream()
                .map(this::toVo)
                .collect(Collectors.toList());
    }

    public List<String> listLikedTitles(Long userId, Integer limit) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        int size = limit == null || limit <= 0 ? 6 : Math.min(limit, 12);
        List<ArticleLike> likes = articleLikeMapper.selectPage(new Page<>(1, size, false),
                new LambdaQueryWrapper<ArticleLike>()
                        .eq(ArticleLike::getUserId, userId)
                        .orderByDesc(ArticleLike::getCreateTime)
                        .orderByDesc(ArticleLike::getId)).getRecords();
        List<Long> articleIds = likes.stream()
                .map(ArticleLike::getArticleId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (articleIds.isEmpty()) {
            return List.of();
        }
        return articleMapper.selectList(new LambdaQueryWrapper<Article>()
                        .in(Article::getId, articleIds)
                        .ne(Article::getDeleteState, DELETE_TRUE)
                        .ne(Article::getState, STATE_FORBIDDEN)
                        .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode()))
                .stream()
                .sorted((a, b) -> Integer.compare(articleIds.indexOf(a.getId()), articleIds.indexOf(b.getId())))
                .map(Article::getTitle)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(size)
                .collect(Collectors.toList());
    }

    public List<String> listFavoriteSongTitles(Long userId, Integer limit) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        int size = limit == null || limit <= 0 ? 6 : Math.min(limit, 12);
        return userMusicFavoriteMapper.selectPage(new Page<>(1, size, false),
                        new LambdaQueryWrapper<UserMusicFavorite>()
                                .eq(UserMusicFavorite::getUserId, userId)
                                .eq(UserMusicFavorite::getDeleteState, (byte) 0)
                                .orderByDesc(UserMusicFavorite::getUpdateTime)
                                .orderByDesc(UserMusicFavorite::getId))
                .getRecords()
                .stream()
                .map(UserMusicFavorite::getTitle)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(size)
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
