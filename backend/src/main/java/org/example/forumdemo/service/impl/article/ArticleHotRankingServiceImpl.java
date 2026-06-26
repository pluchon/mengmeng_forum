package org.example.forumdemo.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ArticleStatus;
import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.mapper.ArticleMapper;
import org.example.forumdemo.service.interfaces.article.ArticleHotRankingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// 热帖榜 ZSet 查询、重算与综合分计算
@Service
@Slf4j
public class ArticleHotRankingServiceImpl implements ArticleHotRankingService {

    private static final byte STATE_FORBIDDEN = 1;
    private static final byte DELETE_TRUE = 1;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<Long> getHotArticleList(Integer topN) {
        int n = (topN == null || topN < 1) ? 10 : topN;
        int overFetch = Math.min(Math.max(n * 4, n + 24), 200);
        Set<String> set = stringRedisTemplate.opsForZSet().reverseRange(Constant.REDIS_KEY_HOT_ARTICLES, 0, overFetch - 1);
        if (set != null && !set.isEmpty()) {
            return filterPublishedHotIdsOrderPreserving(set, n);
        }
        rebuildHotArticleRanking();
        Set<String> reload = stringRedisTemplate.opsForZSet().reverseRange(Constant.REDIS_KEY_HOT_ARTICLES, 0, overFetch - 1);
        if (reload == null || reload.isEmpty()) {
            return Collections.emptyList();
        }
        return filterPublishedHotIdsOrderPreserving(reload, n);
    }

    // ZSet 可能含脏成员；按 Redis 分数顺序只保留当前仍「已发布且未删未封禁」的 id
    private List<Long> filterPublishedHotIdsOrderPreserving(Set<String> memberStrings, int topN) {
        List<Long> ordered = memberStrings.stream().map(Long::valueOf).collect(Collectors.toList());
        if (ordered.isEmpty()) {
            return Collections.emptyList();
        }
        List<Article> rows = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .select(Article::getId)
                .in(Article::getId, ordered)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN));
        Set<Long> publishedIds = rows.stream().map(Article::getId).collect(Collectors.toSet());
        List<Long> out = new ArrayList<>();
        for (Long id : ordered) {
            if (publishedIds.contains(id)) {
                out.add(id);
                if (out.size() >= topN) {
                    break;
                }
            }
        }
        return out;
    }

    @Override
    public void rebuildHotArticleRanking() {
        List<Article> articles = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode()));
        stringRedisTemplate.delete(Constant.REDIS_KEY_HOT_ARTICLES);
        if (articles.isEmpty()) {
            log.info("热帖榜重算: 无可用帖子");
            return;
        }
        for (Article a : articles) {
            double score = computeHotScore(a);
            stringRedisTemplate.opsForZSet().add(Constant.REDIS_KEY_HOT_ARTICLES,
                    String.valueOf(a.getId()), score);
        }
        log.info("热帖榜重算完成: 共写入 {} 篇", articles.size());
    }

    @Override
    public double computeHotScore(Article a) {
        int like = a.getLikeCount() == null ? 0 : a.getLikeCount();
        int visit = a.getVisitCount() == null ? 0 : a.getVisitCount();
        int favorite = a.getFavoriteCount() == null ? 0 : a.getFavoriteCount();
        int reply = a.getReplyCount() == null ? 0 : a.getReplyCount();
        int sub = a.getSubReplyCount() == null ? 0 : a.getSubReplyCount();
        return like     * Constant.HOT_SCORE_WEIGHT_LIKE
             + visit    * Constant.HOT_SCORE_WEIGHT_VISIT
             + favorite * Constant.HOT_SCORE_WEIGHT_FAVORITE
             + (reply + sub) * Constant.HOT_SCORE_WEIGHT_REPLY;
    }

    @Override
    public void addToHotRanking(Long articleId) {
        if (articleId == null || articleId <= 0) {
            return;
        }
        Article latest = articleMapper.selectById(articleId);
        if (latest != null) {
            stringRedisTemplate.opsForZSet().add(Constant.REDIS_KEY_HOT_ARTICLES,
                    String.valueOf(articleId), computeHotScore(latest));
        }
    }
}
