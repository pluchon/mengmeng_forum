package org.pluchon.forum.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ArticleStatus;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.common.utils.TransactionHooks;
import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.db.ArticleLike;
import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.entity.vo.article.ArticleListByLikeResponse;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.mapper.ArticleLikeMapper;
import org.pluchon.forum.mapper.ArticleMapper;
import org.pluchon.forum.service.interfaces.article.ArticleLikeService;
import org.pluchon.forum.service.interfaces.article.ArticleHotRankingService;
import org.pluchon.forum.service.interfaces.article.ArticleService;
import org.pluchon.forum.service.interfaces.creator.CreatorDashboardService;
import org.pluchon.forum.service.interfaces.recommendation.RecommendationAiProfileService;
import org.pluchon.forum.service.impl.remote.ContentUserLookupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ArticleLikeServiceImpl implements ArticleLikeService {

    private static final byte DELETE_TRUE = 1;
    private static final byte STATE_FORBIDDEN = 1;

    @Autowired
    private ArticleLikeMapper articleLikeMapper;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ContentUserLookupService userService;

    @Autowired
    private ArticleHotRankingService articleHotRankingService;

    @Autowired
    private RecommendationAiProfileService recommendationAiProfileService;

    @Autowired
    private CreatorDashboardService creatorDashboardService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void likeArticle(Long articleId, Long userId) {
        if (articleId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        Article article = articleService.selectArticleByArticleId(articleId);
        ArticleLike newLike = new ArticleLike();
        newLike.setArticleId(articleId);
        newLike.setUserId(userId);
        try {
            articleLikeMapper.insert(newLike);
        } catch (DuplicateKeyException ex) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "您已经点赞过了"));
        }
        updateLikeCount(articleId, 1);
        if (ArticleStatus.isPublished(article.getStatus())) {
            creatorDashboardService.recordLike(article.getUserId(), 1);
        }
        TransactionHooks.afterCommit(() -> {
            syncUserLikeCacheOnLike(articleId, userId);
            articleHotRankingService.incrementScore(articleId, Constant.HOT_SCORE_WEIGHT_LIKE);
        });
        recommendationAiProfileService.requestProfileRefresh(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlikeArticle(Long articleId, Long userId) {
        if (articleId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        Article article = articleService.selectArticleByArticleId(articleId);
        int deleted = articleLikeMapper.delete(new LambdaQueryWrapper<ArticleLike>()
                .eq(ArticleLike::getArticleId, articleId)
                .eq(ArticleLike::getUserId, userId));
        if (deleted <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "未点赞，无法取消"));
        }
        updateLikeCount(articleId, -1);
        TransactionHooks.afterCommit(() -> {
            syncUserLikeCacheOnUnlike(articleId, userId);
            articleHotRankingService.incrementScore(articleId, -Constant.HOT_SCORE_WEIGHT_LIKE);
        });
        recommendationAiProfileService.requestProfileRefresh(userId);
    }

    // 帖子点赞数 +1 / 1，扣减时不低于 0
    private void updateLikeCount(Long articleId, int delta) {
        String sql = delta > 0 ? "like_count = like_count + 1" : "like_count = GREATEST(like_count - 1, 0)";
        int result = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .ne(Article::getDeleteState, 1)
                .ne(Article::getState, 1)
                .setSql(sql));
        if (result <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
    }

    private void syncUserLikeCacheOnLike(Long articleId, Long userId) {
        String userLikesKey = Constant.REDIS_KEY_USER_LIKES + userId;
        stringRedisTemplate.opsForSet().add(userLikesKey, String.valueOf(articleId));
        stringRedisTemplate.expire(userLikesKey, Constant.REDIS_TTL_USER_LIKES, TimeUnit.SECONDS);
    }

    private void syncUserLikeCacheOnUnlike(Long articleId, Long userId) {
        stringRedisTemplate.opsForSet().remove(Constant.REDIS_KEY_USER_LIKES + userId, String.valueOf(articleId));
    }

    @Override
    public PageResult<ArticleListByLikeResponse> queryArticleListForLikeWithPage(Long userId, Integer pageNum, Integer pageSize) {
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        // 原本按点赞记录分页，再逐条取帖子且不看状态：作者把帖子改回审核中或被拒之后，
        // 点赞列表照样展示，点进去又被详情页挡回来。而且 total 是点赞数、records 会被
        // 过滤，页码和条数对不上。改成先按可见帖子把点赞记录筛出来，再分页
        List<Long> visibleLikedIds = listVisibleLikedArticleIds(userId);
        long total = visibleLikedIds.size();
        int fromIndex = Math.min((validPageNum - 1) * validPageSize, visibleLikedIds.size());
        int toIndex = Math.min(fromIndex + validPageSize, visibleLikedIds.size());
        List<Long> pageIds = visibleLikedIds.subList(fromIndex, toIndex);
        List<ArticleListByLikeResponse> records = buildLikeResponses(pageIds);
        long pages = total == 0 ? 0 : (total + validPageSize - 1) / validPageSize;
        return new PageResult<>(records, total, validPageNum, validPageSize,
                pages, visibleLikedIds.size() > toIndex);
    }

    // 按点赞时间倒序取出「当前仍然可见」的帖子 ID
    private List<Long> listVisibleLikedArticleIds(Long userId) {
        List<ArticleLike> likes = articleLikeMapper.selectList(new LambdaQueryWrapper<ArticleLike>()
                .eq(ArticleLike::getUserId, userId)
                .orderByDesc(ArticleLike::getCreateTime)
                .orderByDesc(ArticleLike::getId)
                .select(ArticleLike::getArticleId, ArticleLike::getCreateTime, ArticleLike::getId));
        if (likes.isEmpty()) {
            return List.of();
        }
        List<Long> orderedIds = likes.stream().map(ArticleLike::getArticleId).distinct().toList();
        Set<Long> visible = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                        .in(Article::getId, orderedIds)
                        .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                        .ne(Article::getDeleteState, DELETE_TRUE)
                        .ne(Article::getState, STATE_FORBIDDEN)
                        .select(Article::getId))
                .stream().map(Article::getId).collect(Collectors.toSet());
        return orderedIds.stream().filter(visible::contains).toList();
    }

    // 原本每条记录一次查帖子 + 一次跨域 Feign 查作者，一页 8 条就是 8 次 Feign
    private List<ArticleListByLikeResponse> buildLikeResponses(List<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Article> articleMap = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                        .in(Article::getId, articleIds))
                .stream().collect(Collectors.toMap(Article::getId, item -> item));
        Set<Long> authorIds = articleMap.values().stream()
                .map(Article::getUserId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, UserInternalVO> userMap = authorIds.isEmpty()
                ? Map.of()
                : userService.loadActiveUsers(authorIds);
        List<ArticleListByLikeResponse> out = new ArrayList<>(articleIds.size());
        for (Long articleId : articleIds) {
            Article article = articleMap.get(articleId);
            if (article == null) {
                continue;
            }
            UserInternalVO user = userMap.get(article.getUserId());
            if (user == null) {
                continue;
            }
            ArticleListByLikeResponse item = new ArticleListByLikeResponse();
            item.setArticle(article);
            item.setUser(org.pluchon.forum.converter.ContentUserBriefConverter.toBrief(user));
            out.add(item);
        }
        return out;
    }

    @Override
    public PageResult<ArticleListByLikeResponse> queryUserArticleListForLikeWithPage(
            Long userId, Integer pageNum, Integer pageSize) {
        return queryArticleListForLikeWithPage(userId, pageNum, pageSize);
    }

}
