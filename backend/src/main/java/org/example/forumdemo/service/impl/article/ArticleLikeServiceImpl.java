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
import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.entity.db.ArticleLike;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.article.ArticleListByLikeResponse;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.user.UserBriefVO;
import org.example.forumdemo.mapper.ArticleLikeMapper;
import org.example.forumdemo.mapper.ArticleMapper;
import org.example.forumdemo.service.interfaces.article.ArticleLikeService;
import org.example.forumdemo.service.interfaces.article.ArticleService;
import org.example.forumdemo.service.interfaces.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

    // ============================================================
    // 点赞 / 取消
    // ============================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void likeArticle(Long articleId, Long userId) {
        if (articleId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        articleService.selectArticleByArticleId(articleId);
        ArticleLike existing = articleLikeMapper.selectOne(new LambdaQueryWrapper<ArticleLike>()
                .eq(ArticleLike::getArticleId, articleId).eq(ArticleLike::getUserId, userId));
        if (existing != null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "您已经点赞过了"));
        }
        ArticleLike newLike = new ArticleLike();
        newLike.setArticleId(articleId);
        newLike.setUserId(userId);
        articleLikeMapper.insert(newLike);
        updateLikeCount(articleId, 1);
        syncLikeCacheOnLike(articleId, userId);
        // 维护热帖榜分数：likeCount +1
        stringRedisTemplate.opsForZSet().incrementScore(Constant.REDIS_KEY_HOT_ARTICLES, String.valueOf(articleId), 1D);
        log.info("用户 {} 点赞帖子 {}", userId, articleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlikeArticle(Long articleId, Long userId) {
        if (articleId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        ArticleLike existing = articleLikeMapper.selectOne(new LambdaQueryWrapper<ArticleLike>()
                .eq(ArticleLike::getArticleId, articleId).eq(ArticleLike::getUserId, userId));
        if (existing == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "未点赞，无法取消"));
        }
        articleLikeMapper.delete(new LambdaQueryWrapper<ArticleLike>()
                .eq(ArticleLike::getArticleId, articleId).eq(ArticleLike::getUserId, userId));
        updateLikeCount(articleId, -1);
        syncLikeCacheOnUnlike(articleId, userId);
        stringRedisTemplate.opsForZSet().incrementScore(Constant.REDIS_KEY_HOT_ARTICLES, String.valueOf(articleId), -1D);
        log.info("用户 {} 取消点赞帖子 {}", userId, articleId);
    }

    /** 帖子点赞数 +1 / -1 */
    private void updateLikeCount(Long articleId, int delta) {
        String sql = delta > 0 ? "like_count = like_count + 1" : "like_count = like_count - 1";
        int result = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId).ne(Article::getDeleteState, 1).ne(Article::getState, 1).setSql(sql));
        if (result <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
    }

    /** 点赞成功：同步用户点赞 Set + 帖子被点赞用户 Set */
    private void syncLikeCacheOnLike(Long articleId, Long userId) {
        String userLikesKey = Constant.REDIS_KEY_USER_LIKES + userId;
        stringRedisTemplate.opsForSet().add(userLikesKey, String.valueOf(articleId));
        stringRedisTemplate.expire(userLikesKey, Constant.REDIS_TTL_USER_LIKES, TimeUnit.SECONDS);
        String articleLikersKey = Constant.REDIS_KEY_ARTICLE_LIKERS + articleId;
        stringRedisTemplate.opsForSet().add(articleLikersKey, String.valueOf(userId));
        stringRedisTemplate.expire(articleLikersKey, Constant.REDIS_TTL_ARTICLE_LIKERS, TimeUnit.SECONDS);
    }

    /** 取消点赞：同步移除两个 Set 中的对应成员 */
    private void syncLikeCacheOnUnlike(Long articleId, Long userId) {
        stringRedisTemplate.opsForSet().remove(Constant.REDIS_KEY_USER_LIKES + userId, String.valueOf(articleId));
        stringRedisTemplate.opsForSet().remove(Constant.REDIS_KEY_ARTICLE_LIKERS + articleId, String.valueOf(userId));
    }

    // ============================================================
    // 我点赞过的帖子（分页）
    // 走 DB 分页：article_like 与 article 联合按时间倒序，避免一次性把所有点赞拉到内存
    // ============================================================
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

    /** 单条点赞 -> 列表项的装配；帖子已删除时返回 null（由调用方过滤） */
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

    // ============================================================
    // 谁点赞了我的帖子（仅作者本人）
    // ============================================================
    @Override
    public List<User> queryWhoLikedArticle(Long articleId, Long loginUserId) {
        Article article = articleService.selectArticleByArticleId(articleId);
        if (!article.getUserId().equals(loginUserId)) {
            log.warn("用户 {} 尝试查看非自己的帖子 {} 的点赞用户列表，已拒绝", loginUserId, articleId);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
        String cacheKey = Constant.REDIS_KEY_ARTICLE_LIKERS + articleId;
        Set<String> cachedIds = stringRedisTemplate.opsForSet().members(cacheKey);
        if (cachedIds != null && !cachedIds.isEmpty()) {
            return loadUsersByIdStrings(cachedIds, cacheKey);
        }
        // 缓存未命中 -> DB 回源 + 回填
        List<ArticleLike> likes = articleLikeMapper.selectList(new LambdaQueryWrapper<ArticleLike>()
                .eq(ArticleLike::getArticleId, articleId));
        if (likes.isEmpty()) {
            return new ArrayList<>();
        }
        List<User> result = new ArrayList<>();
        for (ArticleLike like : likes) {
            stringRedisTemplate.opsForSet().add(cacheKey, String.valueOf(like.getUserId()));
            try {
                result.add(userService.queryUserByUserId(like.getUserId()));
            } catch (ApplicationException e) {
                log.warn("DB 点赞记录中用户 {} 已不存在，跳过", like.getUserId());
            }
        }
        stringRedisTemplate.expire(cacheKey, Constant.REDIS_TTL_ARTICLE_LIKERS, TimeUnit.SECONDS);
        return result;
    }

    /** 缓存命中场景：把 userId 字符串集合还原为 User 对象，遇到无效用户顺手清理 */
    private List<User> loadUsersByIdStrings(Set<String> userIdStrings, String cacheKey) {
        List<User> result = new ArrayList<>();
        for (String idStr : userIdStrings) {
            try {
                result.add(userService.queryUserByUserId(Long.valueOf(idStr)));
            } catch (ApplicationException e) {
                log.warn("缓存点赞记录中用户 {} 已不存在，从缓存移除", idStr);
                stringRedisTemplate.opsForSet().remove(cacheKey, idStr);
            }
        }
        return result;
    }

    // ============================================================
    // 最新 N 位点赞用户（仅作者本人）
    // 直接走 DB 排序，缓存对"最新"敏感不太适合 Set
    // ============================================================
    @Override
    public List<User> getLatestLikerUsers(Long articleId, Long loginUserId, Integer count) {
        Article article = articleService.selectArticleByArticleId(articleId);
        if (!article.getUserId().equals(loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
        List<ArticleLike> latest = articleLikeMapper.selectPage(new Page<>(1, count, false),
                new LambdaQueryWrapper<ArticleLike>()
                .eq(ArticleLike::getArticleId, articleId)
                .orderByDesc(ArticleLike::getCreateTime)).getRecords();
        List<User> users = new ArrayList<>();
        for (ArticleLike like : latest) {
            try {
                users.add(userService.queryUserByUserId(like.getUserId()));
            } catch (ApplicationException e) {
                log.warn("DB 点赞记录中用户 {} 已不存在，跳过", like.getUserId());
            }
        }
        return users;
    }
}
