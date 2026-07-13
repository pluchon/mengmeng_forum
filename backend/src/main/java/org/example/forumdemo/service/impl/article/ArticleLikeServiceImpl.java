package org.example.forumdemo.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.utils.PageUtils;
import org.example.forumdemo.common.utils.TransactionHooks;
import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.entity.db.ArticleLike;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.article.ArticleListByLikeResponse;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.user.UserBriefVO;
import org.example.forumdemo.mapper.ArticleLikeMapper;
import org.example.forumdemo.mapper.ArticleMapper;
import org.example.forumdemo.service.interfaces.article.ArticleLikeService;
import org.example.forumdemo.service.interfaces.article.ArticleHotRankingService;
import org.example.forumdemo.service.interfaces.article.ArticleService;
import org.example.forumdemo.service.interfaces.user.UserService;
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
    private UserService userService;

    @Autowired
    private ArticleHotRankingService articleHotRankingService;

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

    private ArticleListByLikeResponse buildLikeResponse(ArticleLike like) {
        try {
            Article article = articleService.selectArticleByArticleId(like.getArticleId());
            User user = userService.queryUserByUserId(article.getUserId());
            ArticleListByLikeResponse item = new ArticleListByLikeResponse();
            item.setArticle(article);
            item.setUser(new UserBriefVO(user));
            return item;
        } catch (ApplicationException e) {
            log.warn("点赞列表中帖子 {} 已不可用，跳过", like.getArticleId());
            return null;
        }
    }

}
