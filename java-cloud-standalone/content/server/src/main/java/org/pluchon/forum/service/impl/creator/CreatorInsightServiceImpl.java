package org.pluchon.forum.service.impl.creator;

import org.pluchon.forum.common.constant.ForumTimeZone;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.CreatorDailyMetric;
import org.pluchon.forum.entity.dto.AiCreatorInsightRequest;
import org.pluchon.forum.entity.vo.ai.AiHubCreatorInsightResultVO;
import org.pluchon.forum.entity.vo.creator.CreatorInsightVO;
import org.pluchon.forum.entity.vo.creator.CreatorInsightDataVO;
import org.pluchon.forum.entity.vo.creator.CreatorTrendPointVO;
import org.pluchon.forum.api.FollowDailyCountInternalVO;
import org.pluchon.forum.entity.vo.user.UserFollowStatsVO;
import org.pluchon.forum.mapper.CreatorDailyMetricMapper;
import org.pluchon.forum.service.impl.remote.ContentFollowLookupService;
import org.pluchon.forum.service.interfaces.creator.CreatorInsightService;
import org.pluchon.forum.service.remote.ContentAiGatewayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

// 汇总权威统计数据并调用 AI 生成创作小结
@Slf4j
@Service
public class CreatorInsightServiceImpl implements CreatorInsightService {

    private static final ZoneId ZONE_TAIPEI = ForumTimeZone.ZONE_ID;
    private static final String CACHE_PREFIX = "creator:insight:";

    @Autowired
    private CreatorDailyMetricMapper creatorDailyMetricMapper;

    @Autowired
    private ContentFollowLookupService contentFollowLookupService;

    @Autowired
    private ContentAiGatewayService contentAiGatewayService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public CreatorInsightVO generate(Long userId, String period) {
        InsightPeriod insightPeriod = InsightPeriod.parse(period);
        PeriodRange range = insightPeriod.range(LocalDate.now(ZONE_TAIPEI));
        AiCreatorInsightRequest request = buildRequest(userId, insightPeriod, range);
        String cacheKey = cacheKey(userId, insightPeriod, request);
        CreatorInsightVO cached = readCache(cacheKey);
        if (cached != null) {
            cached.setCached(true);
            return cached;
        }

        AiHubCreatorInsightResultVO ai;
        try {
            ai = contentAiGatewayService.generateCreatorInsight(request);
        } catch (FeignException e) {
            log.warn("创作小结调用 AI 域失败 status={} period={}", e.status(), insightPeriod.name());
            throw new ApplicationException(Result.fail(
                    ResultCode.FAILED_AI_ENGINE, "AI 数据小结暂时不可用，请稍后再试"));
        }
        requireComplete(ai);
        CreatorInsightVO result = toResult(insightPeriod, range, ai);
        writeCache(cacheKey, result);
        return result;
    }

    @Override
    public CreatorInsightDataVO loadData(Long userId, String period) {
        InsightPeriod insightPeriod = InsightPeriod.parse(period);
        PeriodRange range = insightPeriod.range(LocalDate.now(ZONE_TAIPEI));
        AiCreatorInsightRequest request = buildRequest(userId, insightPeriod, range);
        CreatorInsightDataVO data = new CreatorInsightDataVO();
        data.setPeriod(insightPeriod.name());
        data.setPeriodLabel(insightPeriod.label);
        data.setStartDate(range.currentStart().toString());
        data.setEndDate(range.currentEnd().toString());
        data.setTrendPoints(buildTrendPoints(userId, insightPeriod, range));
        CreatorInsightVO cached = readCache(cacheKey(userId, insightPeriod, request));
        if (cached != null) {
            cached.setCached(true);
        }
        data.setInsight(cached);
        return data;
    }

    private List<CreatorTrendPointVO> buildTrendPoints(
            Long userId, InsightPeriod period, PeriodRange range) {
        List<CreatorDailyMetric> metrics = creatorDailyMetricMapper.selectList(
                new LambdaQueryWrapper<CreatorDailyMetric>()
                        .eq(CreatorDailyMetric::getUserId, userId)
                        .eq(CreatorDailyMetric::getDeleteState, (byte) 0)
                        .ge(CreatorDailyMetric::getStatDate, startOfDay(range.currentStart()))
                        .le(CreatorDailyMetric::getStatDate, endOfDay(range.currentEnd()))
                        .orderByAsc(CreatorDailyMetric::getStatDate));
        Map<LocalDate, CreatorDailyMetric> metricByDate = new HashMap<>();
        for (CreatorDailyMetric metric : metrics) {
            metricByDate.put(metric.getStatDate().toInstant().atZone(ZONE_TAIPEI).toLocalDate(), metric);
        }
        Map<LocalDate, Long> followerByDate = new HashMap<>();
        for (FollowDailyCountInternalVO row : contentFollowLookupService.listDailyNewFollowers(
                userId, range.currentStart(), range.currentEnd())) {
            if (row != null && StringUtils.hasText(row.getStatDate())) {
                followerByDate.put(LocalDate.parse(row.getStatDate()), Math.max(0L, row.getCount() == null ? 0L : row.getCount()));
            }
        }
        List<CreatorTrendPointVO> daily = range.currentStart().datesUntil(range.currentEnd().plusDays(1))
                .map(date -> {
                    CreatorDailyMetric metric = metricByDate.get(date);
                    return new CreatorTrendPointVO(
                            date.toString(),
                            metric == null ? 0 : safe(metric.getReadCount()),
                            metric == null ? 0 : safe(metric.getLikeCount()),
                            followerByDate.getOrDefault(date, 0L),
                            metric == null ? 0 : safe(metric.getPublishCount()));
                })
                .toList();
        if (period != InsightPeriod.HALF_YEAR) {
            return daily;
        }
        List<CreatorTrendPointVO> weekly = new ArrayList<>();
        for (int start = 0; start < daily.size(); start += 7) {
            List<CreatorTrendPointVO> bucket = daily.subList(start, Math.min(start + 7, daily.size()));
            weekly.add(new CreatorTrendPointVO(
                    bucket.get(0).getLabel(),
                    bucket.stream().mapToInt(CreatorTrendPointVO::getReadCount).sum(),
                    bucket.stream().mapToInt(CreatorTrendPointVO::getLikeCount).sum(),
                    bucket.stream().mapToLong(CreatorTrendPointVO::getFollowerCount).sum(),
                    bucket.stream().mapToInt(CreatorTrendPointVO::getWorkCount).sum()));
        }
        return weekly;
    }

    private AiCreatorInsightRequest buildRequest(Long userId, InsightPeriod period, PeriodRange range) {
        List<CreatorDailyMetric> metrics = creatorDailyMetricMapper.selectList(
                new LambdaQueryWrapper<CreatorDailyMetric>()
                        .eq(CreatorDailyMetric::getUserId, userId)
                        .eq(CreatorDailyMetric::getDeleteState, (byte) 0)
                        .ge(CreatorDailyMetric::getStatDate, startOfDay(range.previousStart()))
                        .le(CreatorDailyMetric::getStatDate, endOfDay(range.currentEnd()))
                        .orderByAsc(CreatorDailyMetric::getStatDate));
        UserFollowStatsVO followStats = contentFollowLookupService
                .getBatchStats(Set.of(userId), userId)
                .get(userId);

        AiCreatorInsightRequest request = new AiCreatorInsightRequest();
        request.setUserId(userId);
        request.setClientRequestId(UUID.randomUUID().toString());
        request.setPeriodLabel(period.label);
        request.setStartDate(range.currentStart().toString());
        request.setEndDate(range.currentEnd().toString());
        request.setReadCount(sum(metrics, range.currentStart(), range.currentEnd(), MetricType.READ));
        request.setPreviousReadCount(sum(metrics, range.previousStart(), range.previousEnd(), MetricType.READ));
        request.setLikeCount(sum(metrics, range.currentStart(), range.currentEnd(), MetricType.LIKE));
        request.setPreviousLikeCount(sum(metrics, range.previousStart(), range.previousEnd(), MetricType.LIKE));
        request.setWorkCount(sum(metrics, range.currentStart(), range.currentEnd(), MetricType.WORK));
        request.setPreviousWorkCount(sum(metrics, range.previousStart(), range.previousEnd(), MetricType.WORK));
        request.setNewFollowerCount(contentFollowLookupService.countNewFollowers(
                userId, range.currentStart(), range.currentEnd()));
        request.setPreviousNewFollowerCount(contentFollowLookupService.countNewFollowers(
                userId, range.previousStart(), range.previousEnd()));
        request.setTotalFollowerCount(followStats == null || followStats.getFollowerCount() == null
                ? 0L : Math.max(0L, followStats.getFollowerCount()));
        return request;
    }

    private int sum(List<CreatorDailyMetric> metrics, LocalDate start, LocalDate end, MetricType type) {
        return metrics.stream()
                .filter(metric -> {
                    LocalDate date = metric.getStatDate().toInstant().atZone(ZONE_TAIPEI).toLocalDate();
                    return !date.isBefore(start) && !date.isAfter(end);
                })
                .mapToInt(metric -> switch (type) {
                    case READ -> safe(metric.getReadCount());
                    case LIKE -> safe(metric.getLikeCount());
                    case WORK -> safe(metric.getPublishCount());
                })
                .sum();
    }

    private CreatorInsightVO toResult(InsightPeriod period, PeriodRange range,
                                      AiHubCreatorInsightResultVO ai) {
        CreatorInsightVO result = new CreatorInsightVO();
        result.setPeriod(period.name());
        result.setPeriodLabel(period.label);
        result.setStartDate(range.currentStart().toString());
        result.setEndDate(range.currentEnd().toString());
        result.setHeadline(ai.getHeadline().trim());
        result.setOverview(ai.getOverview().trim());
        result.setHighlight(ai.getHighlight().trim());
        result.setHighlights(ai.getHighlights() == null ? List.of(ai.getHighlight().trim())
                : ai.getHighlights().stream().filter(StringUtils::hasText).map(String::trim).limit(3).toList());
        result.setCached(false);
        return result;
    }

    private void requireComplete(AiHubCreatorInsightResultVO result) {
        if (result == null
                || !StringUtils.hasText(result.getHeadline())
                || !StringUtils.hasText(result.getOverview())
                || (!StringUtils.hasText(result.getHighlight())
                && (result.getHighlights() == null || result.getHighlights().isEmpty()))) {
            throw new IllegalStateException("AI 创作小结结果不完整");
        }
    }

    private String cacheKey(Long userId, InsightPeriod period, AiCreatorInsightRequest request) {
        try {
            Map<String, Object> metrics = new java.util.LinkedHashMap<>();
            metrics.put("periodLabel", request.getPeriodLabel());
            metrics.put("startDate", request.getStartDate());
            metrics.put("endDate", request.getEndDate());
            metrics.put("readCount", request.getReadCount());
            metrics.put("previousReadCount", request.getPreviousReadCount());
            metrics.put("likeCount", request.getLikeCount());
            metrics.put("previousLikeCount", request.getPreviousLikeCount());
            metrics.put("workCount", request.getWorkCount());
            metrics.put("previousWorkCount", request.getPreviousWorkCount());
            metrics.put("newFollowerCount", request.getNewFollowerCount());
            metrics.put("previousNewFollowerCount", request.getPreviousNewFollowerCount());
            metrics.put("totalFollowerCount", request.getTotalFollowerCount());
            String hash = DigestUtils.md5DigestAsHex(objectMapper.writeValueAsBytes(metrics));
            return CACHE_PREFIX + userId + ":" + period.name() + ":" + hash;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("创作小结指标序列化失败", e);
        }
    }

    private CreatorInsightVO readCache(String cacheKey) {
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            return StringUtils.hasText(cached)
                    ? objectMapper.readValue(cached, CreatorInsightVO.class) : null;
        } catch (Exception e) {
            log.warn("读取创作小结缓存失败 key={}: {}", cacheKey, e.getMessage());
            return null;
        }
    }

    private void writeCache(String cacheKey, CreatorInsightVO result) {
        try {
            stringRedisTemplate.opsForValue().set(
                    cacheKey, objectMapper.writeValueAsString(result), 24, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("写入创作小结缓存失败 key={}: {}", cacheKey, e.getMessage());
        }
    }

    private Date startOfDay(LocalDate date) {
        return Date.from(date.atStartOfDay(ZONE_TAIPEI).toInstant());
    }

    private Date endOfDay(LocalDate date) {
        return Date.from(date.atTime(LocalTime.MAX).atZone(ZONE_TAIPEI).toInstant());
    }

    private int safe(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private enum MetricType {
        READ,
        LIKE,
        WORK
    }

    private enum InsightPeriod {
        WEEK("近一周", 7),
        HALF_MONTH("近半个月", 15),
        MONTH("近一个月", 30),
        HALF_YEAR("近半年", 180);

        private final String label;
        private final int days;

        InsightPeriod(String label, int days) {
            this.label = label;
            this.days = days;
        }

        private static InsightPeriod parse(String value) {
            if (!StringUtils.hasText(value)) {
                return WEEK;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("不支持的创作小结周期");
            }
        }

        private PeriodRange range(LocalDate today) {
            LocalDate currentStart = today.minusDays(days - 1L);
            LocalDate previousEnd = currentStart.minusDays(1);
            LocalDate previousStart = previousEnd.minusDays(days - 1L);
            return new PeriodRange(currentStart, today, previousStart, previousEnd);
        }
    }

    private record PeriodRange(LocalDate currentStart, LocalDate currentEnd,
                               LocalDate previousStart, LocalDate previousEnd) {
    }
}
