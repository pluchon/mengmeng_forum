package org.example.forumdemo.service.impl.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.entity.db.ArticleReply;
import org.example.forumdemo.entity.db.ArticleSubReply;
import org.example.forumdemo.entity.db.ForumAiModelUsageDaily;
import org.example.forumdemo.entity.db.ForumNotice;
import org.example.forumdemo.entity.db.LotteryActivity;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.admin.AdminAiChartVO;
import org.example.forumdemo.entity.vo.admin.AdminAiSeriesVO;
import org.example.forumdemo.entity.vo.admin.AdminAiTrendsBundleVO;
import org.example.forumdemo.entity.vo.admin.AdminAiTrendsVO;
import org.example.forumdemo.entity.vo.admin.AdminDashboardNoticePreviewVO;
import org.example.forumdemo.entity.vo.admin.AdminLotteryTrendVO;
import org.example.forumdemo.entity.vo.admin.AdminWorkbenchVO;
import org.example.forumdemo.entity.vo.lottery.LotteryHourStatRow;
import org.example.forumdemo.mapper.ArticleMapper;
import org.example.forumdemo.mapper.ArticleReplyMapper;
import org.example.forumdemo.mapper.ArticleSubReplyMapper;
import org.example.forumdemo.mapper.ForumAiModelUsageDailyMapper;
import org.example.forumdemo.mapper.ForumNoticeMapper;
import org.example.forumdemo.mapper.LotteryActivityMapper;
import org.example.forumdemo.mapper.LotteryDrawHourlyStatMapper;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.service.interfaces.admin.AdminDashboardService;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZONE);
    private static final DateTimeFormatter DAY_AXIS = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter WEEK_AXIS = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter MONTH_AXIS = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter LOTTERY_HOUR_AXIS = DateTimeFormatter.ofPattern("HH:mm").withZone(ZONE);

    /** 生图模型（与 ai-server config.yaml / forum_ai_model_price 一致） */
    private static final Set<String> IMAGE_MODEL_CODES = Set.of(
            "z-image-turbo",
            "gpt-image-2"
    );

    @Resource
    private ArticleMapper articleMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private ForumNoticeMapper forumNoticeMapper;

    @Resource
    private ForumAiModelUsageDailyMapper forumAiModelUsageDailyMapper;

    @Resource
    private ArticleReplyMapper articleReplyMapper;

    @Resource
    private ArticleSubReplyMapper articleSubReplyMapper;

    @Resource
    private LotteryActivityMapper lotteryActivityMapper;

    @Resource
    private LotteryDrawHourlyStatMapper lotteryDrawHourlyStatMapper;

    private record DayPoint(LocalDate d, String model, int c) {
    }

    @Override
    public AdminWorkbenchVO workbench(Long loginUserId) {
        long articleCount = articleMapper.selectCount(Wrappers.lambdaQuery(Article.class)
                .ne(Article::getDeleteState, (byte) 1));
        long totalUserCount = userMapper.selectCount(Wrappers.lambdaQuery(User.class)
                .eq(User::getDeleteState, (byte) 0));
        long memberUserCount = userMapper.selectCount(Wrappers.lambdaQuery(User.class)
                .eq(User::getDeleteState, (byte) 0)
                .eq(User::getIsAdmin, (byte) 0));
        long replyCount = articleReplyMapper.selectCount(Wrappers.lambdaQuery(ArticleReply.class)
                .ne(ArticleReply::getDeleteState, (byte) 1));
        long subReplyCount = articleSubReplyMapper.selectCount(Wrappers.lambdaQuery(ArticleSubReply.class)
                .ne(ArticleSubReply::getDeleteState, (byte) 1));
        long commentCount = replyCount + subReplyCount;
        long visitSum = 0L;
        long likeSum = 0L;
        long favoriteSum = 0L;
        Map<String, Object> engagement = articleMapper.sumEngagementForWorkbench();
        if (engagement != null) {
            visitSum = toLong(engagement.get("visitSum"));
            likeSum = toLong(engagement.get("likeSum"));
            favoriteSum = toLong(engagement.get("favoriteSum"));
        }
        long interactionCount = Math.round(
                visitSum * 0.30 + likeSum * 0.40 + commentCount * 0.10 + favoriteSum * 0.20);

        AdminWorkbenchVO vo = new AdminWorkbenchVO();
        vo.setArticleCount(articleCount);
        vo.setTotalUserCount(totalUserCount);
        vo.setMemberUserCount(memberUserCount);
        vo.setInteractionCount(interactionCount);

        User u = userMapper.selectById(loginUserId);
        if (u != null) {
            vo.setNickname(u.getNickname() != null ? u.getNickname() : "");
            vo.setAvatar(u.getAvatarUrl() != null ? u.getAvatarUrl() : "");
        } else {
            vo.setNickname("");
            vo.setAvatar("");
        }

        List<ForumNotice> notices = forumNoticeMapper.selectList(Wrappers.lambdaQuery(ForumNotice.class)
                .eq(ForumNotice::getPublishState, (byte) 1)
                .eq(ForumNotice::getDeleteState, (byte) 0)
                .orderByDesc(ForumNotice::getPinTop)
                .orderByDesc(ForumNotice::getUpdateTime)
                .last("LIMIT 8"));
        List<AdminDashboardNoticePreviewVO> preview = new ArrayList<>();
        for (ForumNotice n : notices) {
            preview.add(toNoticePreview(n));
        }
        vo.setNoticePreview(preview);

        vo.setAiUsageTrends(buildAiTrendsBundle());
        vo.setLotteryDrawTrend(buildLotteryTrend());
        return vo;
    }

    private static long toLong(Object v) {
        if (v == null) {
            return 0L;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private AdminLotteryTrendVO buildLotteryTrend() {
        Long activityId = lotteryActivityMapper.selectMaxActivityId();
        AdminLotteryTrendVO vo = new AdminLotteryTrendVO();
        if (activityId == null || activityId <= 0) {
            vo.setActivityTitle("");
            return vo;
        }
        LotteryActivity act = lotteryActivityMapper.selectById(activityId);
        vo.setActivityId(activityId);
        vo.setActivityTitle(act != null && act.getTitle() != null ? act.getTitle() : "");

        Timestamp fromHour = Timestamp.from(
                ZonedDateTime.now(ZONE).withMinute(0).withSecond(0).withNano(0).minusHours(23).toInstant());
        List<LotteryHourStatRow> rows = lotteryDrawHourlyStatMapper.selectSince(activityId, fromHour);

        Map<Long, Integer> bucket = new HashMap<>();
        for (LotteryHourStatRow r : rows) {
            if (r.getStatHour() == null || r.getDrawCount() == null) {
                continue;
            }
            long key = r.getStatHour().toInstant().atZone(ZONE).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli();
            bucket.put(key, r.getDrawCount());
        }

        List<String> cats = new ArrayList<>();
        List<Integer> vals = new ArrayList<>();
        ZonedDateTime start = ZonedDateTime.now(ZONE).withMinute(0).withSecond(0).withNano(0).minusHours(23);
        for (int i = 0; i < 24; i++) {
            ZonedDateTime t = start.plusHours(i);
            cats.add(LOTTERY_HOUR_AXIS.format(t.toInstant()));
            long ms = t.toInstant().toEpochMilli();
            vals.add(bucket.getOrDefault(ms, 0));
        }
        vo.setCategories(cats);
        vo.setDraws(vals);
        return vo;
    }

    private AdminDashboardNoticePreviewVO toNoticePreview(ForumNotice n) {
        AdminDashboardNoticePreviewVO vo = new AdminDashboardNoticePreviewVO();
        vo.setId(n.getId());
        vo.setTitle(n.getTitle());
        vo.setSubtitle(n.getSubtitle());
        vo.setPinTop(n.getPinTop() != null && n.getPinTop() == 1 ? 1 : 0);
        vo.setNoticeKind(n.getNoticeKind() == null ? 0 : n.getNoticeKind().intValue());
        if (n.getUpdateTime() != null) {
            vo.setUpdateTime(Instant.ofEpochMilli(n.getUpdateTime().getTime()).atZone(ZONE).format(TS));
        } else {
            vo.setUpdateTime("");
        }
        return vo;
    }

    private AdminAiTrendsBundleVO buildAiTrendsBundle() {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate minDay = today.minusDays(400);
        List<ForumAiModelUsageDaily> raw = forumAiModelUsageDailyMapper.selectList(
                Wrappers.lambdaQuery(ForumAiModelUsageDaily.class)
                        .ge(ForumAiModelUsageDaily::getStatDate, java.sql.Date.valueOf(minDay))
                        .le(ForumAiModelUsageDaily::getStatDate, java.sql.Date.valueOf(today)));
        List<DayPoint> points = new ArrayList<>();
        for (ForumAiModelUsageDaily r : raw) {
            if (r.getStatDate() == null || r.getModelCode() == null || r.getModelCode().isBlank()) {
                continue;
            }
            LocalDate d = toLocalDate(r.getStatDate());
            int c = r.getCallCount() == null ? 0 : r.getCallCount();
            points.add(new DayPoint(d, r.getModelCode().trim(), c));
        }
        Map<String, Map<LocalDate, Integer>> byModelDay = new HashMap<>();
        for (DayPoint p : points) {
            byModelDay.computeIfAbsent(p.model, k -> new HashMap<>())
                    .merge(p.d, p.c, Integer::sum);
        }

        AdminAiTrendsBundleVO bundle = new AdminAiTrendsBundleVO();
        bundle.setText(buildAiTrendsForModels(byModelDay, points, today, model -> !isImageModel(model)));
        bundle.setImage(buildAiTrendsForModels(byModelDay, points, today, AdminDashboardServiceImpl::isImageModel));
        return bundle;
    }

    private static boolean isImageModel(String model) {
        return model != null && IMAGE_MODEL_CODES.contains(model.trim());
    }

    private AdminAiTrendsVO buildAiTrendsForModels(Map<String, Map<LocalDate, Integer>> byModelDay,
                                                   List<DayPoint> points,
                                                   LocalDate today,
                                                   Predicate<String> modelFilter) {
        List<DayPoint> filtered = points.stream()
                .filter(p -> modelFilter.test(p.model))
                .toList();

        LocalDate dayStart = today.minusDays(13);
        Set<String> modelsDay = new TreeSet<>();
        for (DayPoint p : filtered) {
            if (!p.d.isBefore(dayStart) && !p.d.isAfter(today)) {
                modelsDay.add(p.model);
            }
        }

        LocalDate monThis = today.with(DayOfWeek.MONDAY);
        LocalDate weekDataFrom = monThis.minusWeeks(7);
        Set<String> modelsWeek = new TreeSet<>();
        for (DayPoint p : filtered) {
            if (!p.d.isBefore(weekDataFrom) && !p.d.isAfter(today)) {
                modelsWeek.add(p.model);
            }
        }

        YearMonth ymEnd = YearMonth.from(today);
        LocalDate monthDataFrom = ymEnd.minusMonths(11).atDay(1);
        Set<String> modelsMonth = new TreeSet<>();
        for (DayPoint p : filtered) {
            if (!p.d.isBefore(monthDataFrom) && !p.d.isAfter(today)) {
                modelsMonth.add(p.model);
            }
        }

        AdminAiTrendsVO trends = new AdminAiTrendsVO();
        trends.setDay(buildDayChart(byModelDay, modelsDay, today));
        trends.setWeek(buildWeekChart(byModelDay, modelsWeek, today));
        trends.setMonth(buildMonthChart(byModelDay, modelsMonth, today));
        return trends;
    }

    private static LocalDate toLocalDate(java.util.Date date) {
        if (date instanceof java.sql.Date) {
            return ((java.sql.Date) date).toLocalDate();
        }
        return date.toInstant().atZone(ZONE).toLocalDate();
    }

    private AdminAiChartVO buildDayChart(Map<String, Map<LocalDate, Integer>> byModelDay,
                                         Set<String> models,
                                         LocalDate today) {
        LocalDate start = today.minusDays(13);
        List<String> categories = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            categories.add(d.format(DAY_AXIS));
        }
        List<AdminAiSeriesVO> series = new ArrayList<>();
        for (String model : models) {
            Map<LocalDate, Integer> dayMap = byModelDay.getOrDefault(model, Map.of());
            List<Integer> data = new ArrayList<>();
            for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
                data.add(dayMap.getOrDefault(d, 0));
            }
            series.add(new AdminAiSeriesVO(modelDisplayName(model), data));
        }
        return new AdminAiChartVO(categories, series);
    }

    private AdminAiChartVO buildWeekChart(Map<String, Map<LocalDate, Integer>> byModelDay,
                                          Set<String> models,
                                          LocalDate today) {
        LocalDate monThis = today.with(DayOfWeek.MONDAY);
        List<LocalDate> weekStarts = new ArrayList<>();
        for (int i = 7; i >= 0; i--) {
            weekStarts.add(monThis.minusWeeks(i));
        }
        List<String> categories = new ArrayList<>();
        for (LocalDate ws : weekStarts) {
            categories.add(ws.format(WEEK_AXIS) + " 当周");
        }
        Map<String, Map<LocalDate, Integer>> byModelWeek = aggregateByWeekStart(byModelDay, weekStarts);
        List<AdminAiSeriesVO> series = new ArrayList<>();
        for (String model : models) {
            Map<LocalDate, Integer> wk = byModelWeek.getOrDefault(model, Map.of());
            List<Integer> data = new ArrayList<>();
            for (LocalDate ws : weekStarts) {
                data.add(wk.getOrDefault(ws, 0));
            }
            series.add(new AdminAiSeriesVO(modelDisplayName(model), data));
        }
        return new AdminAiChartVO(categories, series);
    }

    /**
     * 将按日数据汇总到「当周周一」为键的一周桶内（与 ISO 周对齐，周一为周首）。
     */
    private Map<String, Map<LocalDate, Integer>> aggregateByWeekStart(Map<String, Map<LocalDate, Integer>> byModelDay,
                                                                     List<LocalDate> orderedWeekStarts) {
        LocalDate minWs = orderedWeekStarts.get(0);
        LocalDate maxEnd = orderedWeekStarts.get(orderedWeekStarts.size() - 1).plusWeeks(1);
        Map<String, Map<LocalDate, Integer>> out = new HashMap<>();
        for (Map.Entry<String, Map<LocalDate, Integer>> e : byModelDay.entrySet()) {
            String model = e.getKey();
            for (Map.Entry<LocalDate, Integer> de : e.getValue().entrySet()) {
                LocalDate d = de.getKey();
                if (d.isBefore(minWs) || !d.isBefore(maxEnd)) {
                    continue;
                }
                LocalDate ws = d.with(DayOfWeek.MONDAY);
                out.computeIfAbsent(model, k -> new HashMap<>()).merge(ws, de.getValue(), Integer::sum);
            }
        }
        return out;
    }

    private AdminAiChartVO buildMonthChart(Map<String, Map<LocalDate, Integer>> byModelDay,
                                           Set<String> models,
                                           LocalDate today) {
        YearMonth endYm = YearMonth.from(today);
        List<YearMonth> yms = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            yms.add(endYm.minusMonths(i));
        }
        List<String> categories = new ArrayList<>();
        for (YearMonth ym : yms) {
            categories.add(ym.format(MONTH_AXIS));
        }
        Map<String, Map<YearMonth, Integer>> byModelMonth = new HashMap<>();
        for (Map.Entry<String, Map<LocalDate, Integer>> e : byModelDay.entrySet()) {
            String model = e.getKey();
            for (Map.Entry<LocalDate, Integer> de : e.getValue().entrySet()) {
                YearMonth ym = YearMonth.from(de.getKey());
                byModelMonth.computeIfAbsent(model, k -> new HashMap<>()).merge(ym, de.getValue(), Integer::sum);
            }
        }
        List<AdminAiSeriesVO> series = new ArrayList<>();
        for (String model : models) {
            Map<YearMonth, Integer> mm = byModelMonth.getOrDefault(model, Map.of());
            List<Integer> data = new ArrayList<>();
            for (YearMonth ym : yms) {
                data.add(mm.getOrDefault(ym, 0));
            }
            series.add(new AdminAiSeriesVO(modelDisplayName(model), data));
        }
        return new AdminAiChartVO(categories, series);
    }

    private static String modelDisplayName(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        String c = code.trim();
        return switch (c) {
            case "qwen3.6-flash" -> "Qwen3.6 Flash";
            case "qwen3.6-max-preview" -> "Qwen3.6 Max";
            case "qwen3-vl-flash" -> "Qwen3-VL Flash";
            case "qwen3-vl-plus" -> "Qwen3-VL Plus";
            case "qwen3-vl-rerank" -> "Qwen3-VL Rerank";
            case "tongyi-embedding-vision-flash" -> "Embedding Vision";
            case "deepseek-v4-flash" -> "DeepSeek V4 Flash";
            case "deepseek-v4-pro" -> "DeepSeek V4 Pro";
            case "gemini-3-flash" -> "Gemini 3 Flash";
            case "gemini-3.1-pro" -> "Gemini 3.1 Pro";
            case "z-image-turbo" -> "Z-Image Turbo";
            case "gpt-image-2" -> "GPT Image 2";
            default -> c;
        };
    }
}
