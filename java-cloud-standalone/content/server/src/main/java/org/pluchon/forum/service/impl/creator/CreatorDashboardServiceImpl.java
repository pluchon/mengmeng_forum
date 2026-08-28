package org.pluchon.forum.service.impl.creator;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.pluchon.forum.common.enums.ArticleStatus;
import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.db.CreatorDailyMetric;
import org.pluchon.forum.entity.vo.creator.CreatorDailyTrendVO;
import org.pluchon.forum.entity.vo.creator.CreatorDashboardVO;
import org.pluchon.forum.mapper.ArticleMapper;
import org.pluchon.forum.mapper.CreatorDailyMetricMapper;
import org.pluchon.forum.service.interfaces.creator.CreatorDashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CreatorDashboardServiceImpl implements CreatorDashboardService {

    private static final ZoneId ZONE_TAIPEI = ZoneId.of("Asia/Taipei");
    private static final int MIN_WEEK_OFFSET = -104;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private CreatorDailyMetricMapper creatorDailyMetricMapper;

    @Override
    public CreatorDashboardVO getDashboard(Long userId, Integer weekOffset) {
        LocalDate today = LocalDate.now(ZONE_TAIPEI);
        int validOffset = normalizeWeekOffset(weekOffset);
        LocalDate weekStart = today.minusWeeks(-validOffset)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        if (weekEnd.isAfter(today)) {
            weekEnd = today;
        }
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate metricStart = weekStart.isBefore(monthStart) ? weekStart : monthStart;

        List<Article> articles = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .eq(Article::getUserId, userId)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .ne(Article::getDeleteState, (byte) 1)
                .ne(Article::getState, (byte) 1)
                .select(Article::getVisitCount, Article::getLikeCount));
        List<CreatorDailyMetric> metrics = creatorDailyMetricMapper.selectList(new LambdaQueryWrapper<CreatorDailyMetric>()
                .eq(CreatorDailyMetric::getUserId, userId)
                .eq(CreatorDailyMetric::getDeleteState, (byte) 0)
                .ge(CreatorDailyMetric::getStatDate, startOfDay(metricStart))
                .le(CreatorDailyMetric::getStatDate, endOfDay(today))
                .orderByAsc(CreatorDailyMetric::getStatDate));

        CreatorDashboardVO dashboard = new CreatorDashboardVO();
        dashboard.setTotalLikeCount(sumArticleMetric(articles, false));
        dashboard.setTotalWorkCount(articles.size());
        dashboard.setMonthNewReadCount(sumMetric(metrics, monthStart, today, MetricType.READ));
        dashboard.setMonthNewLikeCount(sumMetric(metrics, monthStart, today, MetricType.LIKE));
        dashboard.setMonthNewWorkCount(sumMetric(metrics, monthStart, today, MetricType.PUBLISH));
        dashboard.setWeekStart(weekStart.toString());
        dashboard.setTrendDays(buildTrendDays(metrics, weekStart, weekEnd));
        return dashboard;
    }

    @Override
    public void recordRead(Long userId) {
        increment(userId, 1, 0, 0);
    }

    @Override
    public void recordLike(Long userId, int delta) {
        increment(userId, 0, delta, 0);
    }

    @Override
    public void recordPublished(Long userId) {
        increment(userId, 0, 0, 1);
    }

    private void increment(Long userId, int readDelta, int likeDelta, int publishDelta) {
        if (userId == null || userId <= 0) {
            return;
        }
        creatorDailyMetricMapper.increment(userId, startOfDay(LocalDate.now(ZONE_TAIPEI)),
                readDelta, likeDelta, publishDelta);
    }

    private List<CreatorDailyTrendVO> buildTrendDays(List<CreatorDailyMetric> metrics,
                                                      LocalDate weekStart, LocalDate weekEnd) {
        Map<LocalDate, CreatorDailyMetric> metricByDate = new HashMap<>();
        for (CreatorDailyMetric metric : metrics) {
            metricByDate.put(toLocalDate(metric.getStatDate()), metric);
        }
        return weekStart.datesUntil(weekEnd.plusDays(1))
                .map(date -> {
                    CreatorDailyMetric metric = metricByDate.get(date);
                    return new CreatorDailyTrendVO(date.toString(), metric == null ? 0 : safe(metric.getReadCount()),
                            metric == null ? 0 : safe(metric.getLikeCount()));
                })
                .toList();
    }

    private int sumArticleMetric(List<Article> articles, boolean reads) {
        return articles.stream().mapToInt(article -> reads ? safe(article.getVisitCount()) : safe(article.getLikeCount())).sum();
    }

    private int sumMetric(List<CreatorDailyMetric> metrics, LocalDate start, LocalDate end, MetricType type) {
        return metrics.stream()
                .filter(metric -> {
                    LocalDate statDate = toLocalDate(metric.getStatDate());
                    return !statDate.isBefore(start) && !statDate.isAfter(end);
                })
                .mapToInt(metric -> switch (type) {
                    case READ -> safe(metric.getReadCount());
                    case LIKE -> safe(metric.getLikeCount());
                    case PUBLISH -> safe(metric.getPublishCount());
                })
                .sum();
    }

    private int normalizeWeekOffset(Integer weekOffset) {
        if (weekOffset == null) {
            return 0;
        }
        return Math.max(MIN_WEEK_OFFSET, Math.min(0, weekOffset));
    }

    private Date startOfDay(LocalDate date) {
        return Date.from(date.atStartOfDay(ZONE_TAIPEI).toInstant());
    }

    private Date endOfDay(LocalDate date) {
        ZonedDateTime end = date.atTime(LocalTime.MAX).atZone(ZONE_TAIPEI);
        return Date.from(end.toInstant());
    }

    private LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ZONE_TAIPEI).toLocalDate();
    }

    private int safe(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private enum MetricType {
        READ,
        LIKE,
        PUBLISH
    }
}
