package org.pluchon.forum.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.common.utils.TransactionHooks;
import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.db.ArticleLike;
import org.pluchon.forum.api.auth.UserInternalVO;
import org.pluchon.forum.entity.vo.article.ArticleListByLikeResponse;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.user.UserBriefVO;
import org.pluchon.forum.mapper.ArticleLikeMapper;
import org.pluchon.forum.mapper.ArticleMapper;
import org.pluchon.forum.service.interfaces.article.ArticleLikeService;
import org.pluchon.forum.service.interfaces.article.ArticleHotRankingService;
import org.pluchon.forum.service.interfaces.article.ArticleService;
import org.pluchon.forum.service.interfaces.recommendation.RecommendationAiProfileService;
import org.pluchon.forum.service.impl.remote.ContentUserLookupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ArticleLikeServiceImpl implements ArticleLikeService {

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void likeArticle(Long articleId, Long userId) {
        if (articleId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        articleService.selectArticleByArticleId(articleId);
        ArticleLike newLike = new ArticleLike();
        newLike.setArticleId(articleId);
        newLike.setUserId(userId);
        try {
            articleLikeMapper.insert(newLike);
        } catch (DuplicateKeyException ex) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "您已经点赞过了"));
        }
        updateLikeCount(articleId, 1);
        TransactionHooks.afterCommit(() -> {
            syncUserLikeCacheOnLike(articleId, userId);
            articleHotRankingService.incrementScore(articleId, Constant.HOT_SCORE_WEIGHT_LIKE);
        });
        recommendationAiProfileService.requestProfileRefresh(userId);
        log.info("用户 {} 点赞帖子 {}", userId, articleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlikeArticle(Long articleId, Long userId) {
        if (articleId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
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
        log.info("用户 {} 取消点赞帖子 {}", userId, articleId);
    }

    /** 帖子点赞数 +1 / -1，扣减时不低于 0 */
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
        Page<ArticleLike> page = PageUtils.getPage(validPageNum, validPageSize);
        Page<ArticleLike> result = articleLikeMapper.selectPage(page, new LambdaQueryWrapper<ArticleLike>()
                .eq(ArticleLike::getUserId, userId)
                .orderByDesc(ArticleLike::getCreateTime));
        List<ArticleListByLikeResponse> records = result.getRecords().stream()
                .map(this::buildLikeResponse)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), validPageNum, validPageSize,
                result.getPages(), result.hasNext());
    }

    @Override
    public PageResult<ArticleListByLikeResponse> queryUserArticleListForLikeWithPage(
            Long userId, Integer pageNum, Integer pageSize) {
        return queryArticleListForLikeWithPage(userId, pageNum, pageSize);
    }

    private ArticleListByLikeResponse buildLikeResponse(ArticleLike like) {
        try {
            Article article = articleService.selectArticleByArticleId(like.getArticleId());
            UserInternalVO user = userService.queryUserByUserId(article.getUserId());
            ArticleListByLikeResponse item = new ArticleListByLikeResponse();
            item.setArticle(article);
            item.setUser(org.pluchon.forum.converter.ContentUserBriefConverter.toBrief(user));
            return item;
        } catch (ApplicationException e) {
            log.warn("点赞列表中帖子 {} 已不可用，跳过", like.getArticleId());
            return null;
        }
    }

}
