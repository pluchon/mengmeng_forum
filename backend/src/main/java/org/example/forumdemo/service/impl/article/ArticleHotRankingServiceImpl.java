package org.example.forumdemo.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.constant.ForumBusinessConstants;
import org.example.forumdemo.common.enums.ArticleStatus;
import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.mapper.ArticleMapper;
import org.example.forumdemo.service.interfaces.article.ArticleHotRankingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// 热帖榜 ZSet 查询、蓝绿重算与综合分计算
@Service
@Slf4j
public class ArticleHotRankingServiceImpl implements ArticleHotRankingService {

    private static final byte STATE_FORBIDDEN = 1;
    private static final byte DELETE_TRUE = 1;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private HotArticleRedisOps hotArticleRedisOps;

    @Override
    public List<Long> getHotArticleList(Integer topN) {
        int n = (topN == null || topN < 1) ? 10 : Math.min(topN, 50);
        int overFetch = Math.min(Math.max(n * 4, n + 24), 200);
        Set<String> set = hotArticleRedisOps.reverseRange(0, overFetch - 1);
        if (set != null && !set.isEmpty()) {
            return filterPublishedHotIdsOrderPreserving(set, n);
        }
        return loadTopHotFromDb(n);
    }

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
        Date windowStart = Date.from(Instant.now().minus(Duration.ofDays(ForumBusinessConstants.HOT_RANK_WINDOW_DAYS)));
        List<Article> articles = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .ge(Article::getCreateTime, windowStart));
        Map<String, Double> scores = new HashMap<>();
        for (Article article : articles) {
            double score = computeHotScore(article);
            if (score > 0) {
                scores.put(String.valueOf(article.getId()), score);
            }
        }
        hotArticleRedisOps.rebuildBlueGreen(scores);
        log.info("热帖榜蓝绿重算完成: 共写入 {} 篇", scores.size());
    }

    @Override
    public double computeHotScore(Article article) {
        if (article == null || article.getCreateTime() == null) {
            return 0;
        }
        long ageHours = Duration.between(article.getCreateTime().toInstant(), Instant.now()).toHours();
        if (ageHours > ForumBusinessConstants.HOT_RANK_WINDOW_DAYS * 24L) {
            return 0;
        }
        int like = article.getLikeCount() == null ? 0 : article.getLikeCount();
        int visit = article.getVisitCount() == null ? 0 : article.getVisitCount();
        int favorite = article.getFavoriteCount() == null ? 0 : article.getFavoriteCount();
        int reply = article.getReplyCount() == null ? 0 : article.getReplyCount();
        int sub = article.getSubReplyCount() == null ? 0 : article.getSubReplyCount();
        double base = like * Constant.HOT_SCORE_WEIGHT_LIKE
                + visit * Constant.HOT_SCORE_WEIGHT_VISIT
                + favorite * Constant.HOT_SCORE_WEIGHT_FAVORITE
                + (reply + sub) * Constant.HOT_SCORE_WEIGHT_REPLY;
        double decay = 1.0 / (1.0 + ageHours / 24.0);
        double boost = ageHours <= ForumBusinessConstants.HOT_RANK_NEW_POST_HOURS
                ? ForumBusinessConstants.HOT_RANK_NEW_POST_BOOST
                : 1.0;
        return base * decay * boost;
    }

    @Override
    public void addToHotRanking(Long articleId) {
        if (articleId == null || articleId <= 0) {
            return;
        }
        Article latest = articleMapper.selectById(articleId);
        if (latest == null) {
            return;
        }
        double score = computeHotScore(latest);
        if (score > 0) {
            hotArticleRedisOps.setScore(articleId, score);
        }
    }

    @Override
    public void incrementScore(Long articleId, double delta) {
        hotArticleRedisOps.incrementScore(articleId, delta);
    }

    @Override
    public void removeFromRanking(Long articleId) {
        hotArticleRedisOps.remove(articleId);
    }

    private List<Long> loadTopHotFromDb(int topN) {
        Date windowStart = Date.from(Instant.now().minus(Duration.ofDays(ForumBusinessConstants.HOT_RANK_WINDOW_DAYS)));
        List<Article> articles = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .ge(Article::getCreateTime, windowStart));
        if (articles.isEmpty()) {
            return Collections.emptyList();
        }
        return articles.stream()
                .sorted(Comparator.comparingDouble(this::computeHotScore).reversed())
                .limit(topN)
                .map(Article::getId)
                .collect(Collectors.toList());
    }
}
