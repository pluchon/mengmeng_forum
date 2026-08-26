package org.pluchon.forum.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.constant.ForumBusinessConstants;
import org.pluchon.forum.common.enums.ArticleStatus;
import org.pluchon.forum.common.enums.HotArticleTrendDirection;
import org.pluchon.forum.common.utils.ArticleHotScoreUtils;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.mapper.ArticleMapper;
import org.pluchon.forum.service.interfaces.article.ArticleHotRankingService;
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

    @Override
    public PageResult<Long> getHotArticlePage(Integer pageNum, Integer pageSize) {
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = Math.min(PageUtils.getValidPageSize(pageSize), ForumBusinessConstants.HOT_RANK_PAGE_SIZE_MAX);
        List<Long> validTopIds = getHotArticleList(ForumBusinessConstants.HOT_RANK_LIST_MAX);
        int fromIndex = Math.min((validPageNum - 1) * validPageSize, validTopIds.size());
        int toIndex = Math.min(fromIndex + validPageSize, validTopIds.size());
        List<Long> records = new ArrayList<>(validTopIds.subList(fromIndex, toIndex));
        long total = validTopIds.size();
        long pages = total == 0 ? 0 : (total + validPageSize - 1) / validPageSize;
        return new PageResult<>(records, total, validPageNum, validPageSize, pages, validPageNum < pages);
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
        updateDailyTrendSnapshots(articles);
        log.info("热帖榜蓝绿重算与趋势快照完成: 共写入 {} 篇", scores.size());
    }

    @Override
    public double computeHotScore(Article article) {
        return ArticleHotScoreUtils.computeHotScore(article);
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

    @Override
    public Map<Long, HotArticleTrendDirection> getTrendDirections(List<Long> articleIds) {
        return hotArticleRedisOps.readTrendDirections(articleIds);
    }

    @Override
    public Map<Long, Double> getHotScores(List<Long> articleIds) {
        return hotArticleRedisOps.readScores(articleIds);
    }

    void updateDailyTrendSnapshots(List<Article> articles) {
        List<Long> articleIds = articles.stream().map(Article::getId).toList();
        boolean initialized = hotArticleRedisOps.isDailyTrendInitialized();
        Map<Long, String> previousBaselines = hotArticleRedisOps.readDailyMetricBaselines(articleIds);
        Map<Long, Double> previousScores = hotArticleRedisOps.readPreviousPeriodScores(articleIds);
        Map<Long, String> nextBaselines = new HashMap<>();
        Map<Long, Double> nextScores = new HashMap<>();
        Map<Long, HotArticleTrendDirection> directions = new HashMap<>();
        for (Article article : articles) {
            int visit = safeMetric(article.getVisitCount());
            int like = safeMetric(article.getLikeCount());
            int favorite = safeMetric(article.getFavoriteCount());
            int[] previous = parseMetricBaseline(previousBaselines.get(article.getId()));
            double currentScore = initialized
                    ? computePeriodScore(
                            Math.max(0, visit - previous[0]),
                            Math.max(0, like - previous[1]),
                            Math.max(0, favorite - previous[2]))
                    : 0;
            HotArticleTrendDirection direction = initialized
                    ? comparePeriodScores(currentScore, previousScores.getOrDefault(article.getId(), 0D))
                    : HotArticleTrendDirection.STABLE;
            nextBaselines.put(article.getId(), encodeMetricBaseline(visit, like, favorite));
            nextScores.put(article.getId(), currentScore);
            directions.put(article.getId(), direction);
        }
        hotArticleRedisOps.replaceDailyTrendSnapshots(nextBaselines, nextScores, directions);
    }

    static double computePeriodScore(int visitDelta, int likeDelta, int favoriteDelta) {
        return Math.max(0, visitDelta) * Constant.HOT_SCORE_WEIGHT_VISIT
                + Math.max(0, likeDelta) * Constant.HOT_SCORE_WEIGHT_LIKE
                + Math.max(0, favoriteDelta) * Constant.HOT_SCORE_WEIGHT_FAVORITE;
    }

    static HotArticleTrendDirection comparePeriodScores(double currentScore, double previousScore) {
        int comparison = Double.compare(currentScore, previousScore);
        if (comparison > 0) {
            return HotArticleTrendDirection.UP;
        }
        if (comparison < 0) {
            return HotArticleTrendDirection.DOWN;
        }
        return HotArticleTrendDirection.STABLE;
    }

    private static int safeMetric(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private static String encodeMetricBaseline(int visit, int like, int favorite) {
        return visit + "," + like + "," + favorite;
    }

    private static int[] parseMetricBaseline(String value) {
        int[] empty = new int[]{0, 0, 0};
        if (value == null || value.isBlank()) {
            return empty;
        }
        String[] parts = value.split(",", -1);
        if (parts.length != 3) {
            return empty;
        }
        try {
            return new int[]{
                    Math.max(0, Integer.parseInt(parts[0])),
                    Math.max(0, Integer.parseInt(parts[1])),
                    Math.max(0, Integer.parseInt(parts[2]))
            };
        } catch (NumberFormatException ignored) {
            return empty;
        }
    }

    private List<Long> loadTopHotFromDb(int topN) {
        Date windowStart = Date.from(Instant.now().minus(Duration.ofDays(ForumBusinessConstants.HOT_RANK_WINDOW_DAYS)));
        List<Article> articles = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .ge(Article::getCreateTime, windowStart));
        // 时间窗内无帖时退化为全量已发布帖，按互动热度排序，避免 Redis 空榜时接口长期返回空
        if (articles.isEmpty()) {
            int fetchSize = Math.max(topN * 5, 50);
            Page<Article> fallbackPage = articleMapper.selectPage(
                    new Page<>(1, fetchSize),
                    new LambdaQueryWrapper<Article>()
                            .ne(Article::getDeleteState, DELETE_TRUE)
                            .ne(Article::getState, STATE_FORBIDDEN)
                            .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                            .orderByDesc(Article::getLikeCount)
                            .orderByDesc(Article::getVisitCount)
                            .orderByDesc(Article::getUpdateTime));
            return fallbackPage.getRecords().stream()
                    .limit(topN)
                    .map(Article::getId)
                    .collect(Collectors.toList());
        }
        return articles.stream()
                .sorted(Comparator.comparingDouble(this::computeHotScore).reversed())
                .limit(topN)
                .map(Article::getId)
                .collect(Collectors.toList());
    }
}
