package org.pluchon.forum.service.impl.driftbottle;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.DriftBottleReportStatus;
import org.pluchon.forum.common.enums.DriftBottleReportTargetType;
import org.pluchon.forum.common.enums.DriftBottleStatus;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.ForumDateTimes;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.converter.DriftBottleConverter;
import org.pluchon.forum.entity.db.DriftBottle;
import org.pluchon.forum.entity.db.DriftBottleComment;
import org.pluchon.forum.entity.db.DriftBottlePickLog;
import org.pluchon.forum.entity.db.DriftBottleReport;
import org.pluchon.forum.entity.dto.driftbottle.CreateDriftBottleCommentRequest;
import org.pluchon.forum.entity.dto.driftbottle.CreateDriftBottleRequest;
import org.pluchon.forum.entity.dto.driftbottle.ReportDriftBottleRequest;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.driftbottle.DriftBottleCommentVO;
import org.pluchon.forum.entity.vo.driftbottle.DriftBottleDetailVO;
import org.pluchon.forum.entity.vo.driftbottle.DriftBottleListItemVO;
import org.pluchon.forum.entity.vo.driftbottle.DriftBottleQuotaVO;
import org.pluchon.forum.mapper.DriftBottleCommentMapper;
import org.pluchon.forum.mapper.DriftBottleMapper;
import org.pluchon.forum.mapper.DriftBottlePickLogMapper;
import org.pluchon.forum.mapper.DriftBottleReportMapper;
import org.pluchon.forum.service.interfaces.driftbottle.DriftBottleService;
import org.pluchon.forum.service.interfaces.ai.AiHubService;
import org.pluchon.forum.service.security.AiUserContext;
import org.pluchon.forum.service.security.AiUserLookupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
// 漂流瓶业务实现
public class DriftBottleServiceImpl implements DriftBottleService {

    private static final Logger log = LoggerFactory.getLogger(DriftBottleServiceImpl.class);
    private static final int CREATE_DAILY_LIMIT = 5;
    private static final int PICK_DAILY_LIMIT = 20;
    private static final int COMMENT_DAILY_LIMIT = 50;
    private static final int REPORT_HIDE_THRESHOLD = 3;
    private static final int PICK_EXCLUDE_RECENT_SIZE = 100;
    private static final Set<String> MOOD_TYPES = Set.of("开心", "难过", "迷茫", "压力", "秘密", "求安慰", "随便说说");

    // 漂流瓶 Mapper
    @Autowired
    private DriftBottleMapper driftBottleMapper;

    // 漂流瓶评论 Mapper
    @Autowired
    private DriftBottleCommentMapper driftBottleCommentMapper;

    // 漂流瓶打捞记录 Mapper
    @Autowired
    private DriftBottlePickLogMapper driftBottlePickLogMapper;

    // 漂流瓶举报 Mapper
    @Autowired
    private DriftBottleReportMapper driftBottleReportMapper;

    // AI 域用户读取服务
    @Autowired
    private AiUserLookupService aiUserLookupService;

    // AI 内容审核服务
    @Autowired
    private AiHubService aiHubService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DriftBottleDetailVO createBottle(CreateDriftBottleRequest request, Long loginUserId) {
        AiUserContext user = aiUserLookupService.getById(loginUserId);
        assertCanPost(user);
        assertDailyLimit(countTodayBottles(loginUserId), CREATE_DAILY_LIMIT, "今日扔瓶次数已用完");
        String content = normalizeContent(request.getContent(), 20, 500);
        assertAiContentAllowed("漂流瓶内容：" + content);
        String moodType = normalizeMood(request.getMoodType());
        Date now = ForumDateTimes.now();

        DriftBottle bottle = new DriftBottle();
        bottle.setUserId(loginUserId);
        bottle.setContent(content);
        bottle.setMoodType(moodType);
        bottle.setStatus(DriftBottleStatus.VISIBLE.getCode());
        bottle.setCommentCount(0);
        bottle.setPickedCount(0);
        bottle.setCreateTime(now);
        bottle.setUpdateTime(now);
        bottle.setDeleteState(Constant.DELETE_STATE_FALSE);
        if (driftBottleMapper.insert(bottle) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
        }
        return buildDetail(bottle, loginUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DriftBottleDetailVO pickBottle(Long loginUserId) {
        aiUserLookupService.getById(loginUserId);
        assertDailyLimit(countTodayPicks(loginUserId), PICK_DAILY_LIMIT, "今日捞瓶次数已用完");
        DriftBottle bottle = pickCandidate(loginUserId, true);
        if (bottle == null) {
            bottle = pickCandidate(loginUserId, false);
        }
        if (bottle == null) {
            return null;
        }
        Date now = ForumDateTimes.now();
        DriftBottlePickLog log = new DriftBottlePickLog();
        log.setBottleId(bottle.getId());
        log.setUserId(loginUserId);
        log.setCreateTime(now);
        log.setUpdateTime(now);
        log.setDeleteState(Constant.DELETE_STATE_FALSE);
        if (driftBottlePickLogMapper.insert(log) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
        }
        driftBottleMapper.update(null, new LambdaUpdateWrapper<DriftBottle>()
                .eq(DriftBottle::getId, bottle.getId())
                .setSql("picked_count = picked_count + 1")
                .set(DriftBottle::getUpdateTime, now));
        bottle.setPickedCount(safeInt(bottle.getPickedCount()) + 1);
        return buildDetail(bottle, loginUserId);
    }

    @Override
    public DriftBottleDetailVO queryDetail(Long bottleId, Long loginUserId) {
        aiUserLookupService.getById(loginUserId);
        return buildDetail(queryVisibleOrOwnedBottle(bottleId, loginUserId), loginUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DriftBottleDetailVO commentBottle(Long bottleId, CreateDriftBottleCommentRequest request, Long loginUserId) {
        AiUserContext user = aiUserLookupService.getById(loginUserId);
        assertCanPost(user);
        assertDailyLimit(countTodayComments(loginUserId), COMMENT_DAILY_LIMIT, "今日评论次数已用完");
        DriftBottle bottle = queryVisibleBottle(bottleId);
        assertNotLatestCommenter(bottleId, loginUserId);
        String content = normalizeContent(request.getContent(), 1, 200);
        assertAiContentAllowed("漂流瓶评论：" + content);
        Date now = ForumDateTimes.now();

        DriftBottleComment comment = new DriftBottleComment();
        comment.setBottleId(bottleId);
        comment.setUserId(loginUserId);
        comment.setContent(content);
        comment.setStatus(DriftBottleStatus.VISIBLE.getCode());
        comment.setCreateTime(now);
        comment.setUpdateTime(now);
        comment.setDeleteState(Constant.DELETE_STATE_FALSE);
        if (driftBottleCommentMapper.insert(comment) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
        }
        driftBottleMapper.update(null, new LambdaUpdateWrapper<DriftBottle>()
                .eq(DriftBottle::getId, bottleId)
                .setSql("comment_count = comment_count + 1")
                .set(DriftBottle::getUpdateTime, now));
        bottle.setCommentCount(safeInt(bottle.getCommentCount()) + 1);
        return buildDetail(bottle, loginUserId);
    }

    @Override
    public PageResult<DriftBottleListItemVO> queryMine(Long loginUserId, Integer pageNum, Integer pageSize) {
        aiUserLookupService.getById(loginUserId);
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<DriftBottle> page = PageUtils.getPage(validPageNum, validPageSize);
        Page<DriftBottle> result = driftBottleMapper.selectPage(page, new LambdaQueryWrapper<DriftBottle>()
                .eq(DriftBottle::getUserId, loginUserId)
                .ne(DriftBottle::getDeleteState, Constant.DELETE_STATE_TRUE)
                .orderByDesc(DriftBottle::getCreateTime));
        List<DriftBottleListItemVO> records = result.getRecords().stream()
                .map(bottle -> DriftBottleConverter.toListItemVO(bottle, latestCommentContent(bottle.getId())))
                .collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), validPageNum, validPageSize,
                result.getPages(), result.hasNext());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBottle(Long bottleId, Long loginUserId) {
        DriftBottle bottle = queryBottle(bottleId);
        if (!Objects.equals(bottle.getUserId(), loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "只能删除自己的瓶子"));
        }
        int affected = driftBottleMapper.update(null, new LambdaUpdateWrapper<DriftBottle>()
                .eq(DriftBottle::getId, bottleId)
                .eq(DriftBottle::getUserId, loginUserId)
                .set(DriftBottle::getStatus, DriftBottleStatus.DELETED.getCode())
                .set(DriftBottle::getDeleteState, Constant.DELETE_STATE_TRUE)
                .set(DriftBottle::getUpdateTime, ForumDateTimes.now()));
        if (affected <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reportBottle(Long bottleId, ReportDriftBottleRequest request, Long loginUserId) {
        DriftBottle bottle = queryVisibleBottle(bottleId);
        if (Objects.equals(bottle.getUserId(), loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "不能举报自己的瓶子"));
        }
        createReport(DriftBottleReportTargetType.BOTTLE.getCode(), bottleId, request, loginUserId);
        hideTargetIfAiRejected(DriftBottleReportTargetType.BOTTLE.getCode(), bottleId, "漂流瓶内容：" + bottle.getContent());
        hideTargetIfNeeded(DriftBottleReportTargetType.BOTTLE.getCode(), bottleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reportComment(Long commentId, ReportDriftBottleRequest request, Long loginUserId) {
        DriftBottleComment comment = queryVisibleComment(commentId);
        if (Objects.equals(comment.getUserId(), loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "不能举报自己的评论"));
        }
        createReport(DriftBottleReportTargetType.COMMENT.getCode(), commentId, request, loginUserId);
        hideTargetIfAiRejected(DriftBottleReportTargetType.COMMENT.getCode(), commentId, "漂流瓶评论：" + comment.getContent());
        hideTargetIfNeeded(DriftBottleReportTargetType.COMMENT.getCode(), commentId);
    }

    @Override
    public DriftBottleQuotaVO queryQuota(Long loginUserId) {
        aiUserLookupService.getById(loginUserId);
        DriftBottleQuotaVO vo = new DriftBottleQuotaVO();
        vo.setCreateRemaining(Math.max(0, CREATE_DAILY_LIMIT - countTodayBottles(loginUserId).intValue()));
        vo.setPickRemaining(Math.max(0, PICK_DAILY_LIMIT - countTodayPicks(loginUserId).intValue()));
        vo.setCommentRemaining(Math.max(0, COMMENT_DAILY_LIMIT - countTodayComments(loginUserId).intValue()));
        return vo;
    }

    private DriftBottle pickCandidate(Long loginUserId, boolean excludePicked) {
        LambdaQueryWrapper<DriftBottle> wrapper = visibleBottleWrapper()
                .ne(DriftBottle::getUserId, loginUserId);
        if (excludePicked) {
            List<Long> pickedIds = recentPickedBottleIds(loginUserId);
            if (!pickedIds.isEmpty()) {
                wrapper.notIn(DriftBottle::getId, pickedIds);
            }
        }
        Long count = driftBottleMapper.selectCount(wrapper);
        if (count == null || count <= 0) {
            return null;
        }
        long randomPage = ThreadLocalRandom.current().nextLong(count) + 1;
        Page<DriftBottle> page = new Page<>(randomPage, 1);
        Page<DriftBottle> result = driftBottleMapper.selectPage(page, wrapper.orderByDesc(DriftBottle::getCreateTime));
        return result.getRecords().isEmpty() ? null : result.getRecords().get(0);
    }

    private DriftBottleDetailVO buildDetail(DriftBottle bottle, Long loginUserId) {
        List<DriftBottleComment> comments = driftBottleCommentMapper.selectList(new LambdaQueryWrapper<DriftBottleComment>()
                .eq(DriftBottleComment::getBottleId, bottle.getId())
                .eq(DriftBottleComment::getStatus, DriftBottleStatus.VISIBLE.getCode())
                .ne(DriftBottleComment::getDeleteState, Constant.DELETE_STATE_TRUE)
                .orderByAsc(DriftBottleComment::getCreateTime)
                .orderByAsc(DriftBottleComment::getId));
        Map<Long, String> aliases = new LinkedHashMap<>();
        List<DriftBottleCommentVO> commentVos = new ArrayList<>();
        for (DriftBottleComment comment : comments) {
            String alias = aliases.computeIfAbsent(comment.getUserId(), key -> anonymousName(aliases.size()));
            commentVos.add(DriftBottleConverter.toCommentVO(comment, alias, loginUserId));
        }
        return DriftBottleConverter.toDetailVO(bottle, loginUserId, commentVos);
    }

    private DriftBottle queryBottle(Long bottleId) {
        if (bottleId == null || bottleId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        DriftBottle bottle = driftBottleMapper.selectOne(new LambdaQueryWrapper<DriftBottle>()
                .eq(DriftBottle::getId, bottleId)
                .ne(DriftBottle::getDeleteState, Constant.DELETE_STATE_TRUE));
        if (bottle == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        return bottle;
    }

    private DriftBottle queryVisibleBottle(Long bottleId) {
        DriftBottle bottle = queryBottle(bottleId);
        if (!DriftBottleStatus.VISIBLE.getCode().equals(bottle.getStatus())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        return bottle;
    }

    private DriftBottle queryVisibleOrOwnedBottle(Long bottleId, Long loginUserId) {
        DriftBottle bottle = queryBottle(bottleId);
        if (!DriftBottleStatus.VISIBLE.getCode().equals(bottle.getStatus())
                && !Objects.equals(bottle.getUserId(), loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        return bottle;
    }

    private DriftBottleComment queryVisibleComment(Long commentId) {
        if (commentId == null || commentId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        DriftBottleComment comment = driftBottleCommentMapper.selectOne(new LambdaQueryWrapper<DriftBottleComment>()
                .eq(DriftBottleComment::getId, commentId)
                .eq(DriftBottleComment::getStatus, DriftBottleStatus.VISIBLE.getCode())
                .ne(DriftBottleComment::getDeleteState, Constant.DELETE_STATE_TRUE));
        if (comment == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        return comment;
    }

    private LambdaQueryWrapper<DriftBottle> visibleBottleWrapper() {
        return new LambdaQueryWrapper<DriftBottle>()
                .eq(DriftBottle::getStatus, DriftBottleStatus.VISIBLE.getCode())
                .ne(DriftBottle::getDeleteState, Constant.DELETE_STATE_TRUE);
    }

    private void assertNotLatestCommenter(Long bottleId, Long loginUserId) {
        List<DriftBottleComment> latest = driftBottleCommentMapper.selectList(new LambdaQueryWrapper<DriftBottleComment>()
                .eq(DriftBottleComment::getBottleId, bottleId)
                .eq(DriftBottleComment::getStatus, DriftBottleStatus.VISIBLE.getCode())
                .ne(DriftBottleComment::getDeleteState, Constant.DELETE_STATE_TRUE)
                .orderByDesc(DriftBottleComment::getId)
                .last("limit 1"));
        if (!latest.isEmpty() && Objects.equals(latest.get(0).getUserId(), loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "请等别人回应后再继续评论"));
        }
    }

    private void createReport(Byte targetType, Long targetId, ReportDriftBottleRequest request, Long loginUserId) {
        Long existed = driftBottleReportMapper.selectCount(new LambdaQueryWrapper<DriftBottleReport>()
                .eq(DriftBottleReport::getTargetType, targetType)
                .eq(DriftBottleReport::getTargetId, targetId)
                .eq(DriftBottleReport::getReportUserId, loginUserId)
                .ne(DriftBottleReport::getDeleteState, Constant.DELETE_STATE_TRUE));
        if (existed != null && existed > 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "你已经举报过该内容"));
        }
        Date now = ForumDateTimes.now();
        DriftBottleReport report = new DriftBottleReport();
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReportUserId(loginUserId);
        report.setReasonType(normalizeReasonType(request.getReasonType()));
        report.setReasonDetail(normalizeOptional(request.getReasonDetail(), 200));
        report.setStatus(DriftBottleReportStatus.PENDING.getCode());
        report.setCreateTime(now);
        report.setUpdateTime(now);
        report.setDeleteState(Constant.DELETE_STATE_FALSE);
        if (driftBottleReportMapper.insert(report) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
        }
    }

    private void hideTargetIfNeeded(Byte targetType, Long targetId) {
        Long count = driftBottleReportMapper.selectCount(new LambdaQueryWrapper<DriftBottleReport>()
                .eq(DriftBottleReport::getTargetType, targetType)
                .eq(DriftBottleReport::getTargetId, targetId)
                .eq(DriftBottleReport::getStatus, DriftBottleReportStatus.PENDING.getCode())
                .ne(DriftBottleReport::getDeleteState, Constant.DELETE_STATE_TRUE));
        if (count == null || count < REPORT_HIDE_THRESHOLD) {
            return;
        }
        hideTarget(targetType, targetId);
    }

    private void hideTargetIfAiRejected(Byte targetType, Long targetId, String content) {
        try {
            if (StringUtils.hasText(aiHubService.validateText(content))) {
                hideTarget(targetType, targetId);
            }
        } catch (RuntimeException exception) {
            log.warn("漂流瓶举报 AI 复核失败 targetType={}, targetId={}", targetType, targetId, exception);
        }
    }

    private void hideTarget(Byte targetType, Long targetId) {
        Date now = ForumDateTimes.now();
        if (DriftBottleReportTargetType.BOTTLE.getCode().equals(targetType)) {
            driftBottleMapper.update(null, new LambdaUpdateWrapper<DriftBottle>()
                    .eq(DriftBottle::getId, targetId)
                    .set(DriftBottle::getStatus, DriftBottleStatus.HIDDEN.getCode())
                    .set(DriftBottle::getUpdateTime, now));
            return;
        }
        driftBottleCommentMapper.update(null, new LambdaUpdateWrapper<DriftBottleComment>()
                .eq(DriftBottleComment::getId, targetId)
                .set(DriftBottleComment::getStatus, DriftBottleStatus.HIDDEN.getCode())
                .set(DriftBottleComment::getUpdateTime, now));
    }

    private List<Long> recentPickedBottleIds(Long loginUserId) {
        return driftBottlePickLogMapper.selectList(new LambdaQueryWrapper<DriftBottlePickLog>()
                        .eq(DriftBottlePickLog::getUserId, loginUserId)
                        .ne(DriftBottlePickLog::getDeleteState, Constant.DELETE_STATE_TRUE)
                        .orderByDesc(DriftBottlePickLog::getId)
                        .last("limit " + PICK_EXCLUDE_RECENT_SIZE))
                .stream()
                .map(DriftBottlePickLog::getBottleId)
                .distinct()
                .collect(Collectors.toList());
    }

    private String latestCommentContent(Long bottleId) {
        List<DriftBottleComment> latest = driftBottleCommentMapper.selectList(new LambdaQueryWrapper<DriftBottleComment>()
                .eq(DriftBottleComment::getBottleId, bottleId)
                .eq(DriftBottleComment::getStatus, DriftBottleStatus.VISIBLE.getCode())
                .ne(DriftBottleComment::getDeleteState, Constant.DELETE_STATE_TRUE)
                .orderByDesc(DriftBottleComment::getId)
                .last("limit 1"));
        return latest.isEmpty() ? null : latest.get(0).getContent();
    }

    private Long countTodayBottles(Long userId) {
        return driftBottleMapper.selectCount(new LambdaQueryWrapper<DriftBottle>()
                .eq(DriftBottle::getUserId, userId)
                .ge(DriftBottle::getCreateTime, todayStart())
                .lt(DriftBottle::getCreateTime, tomorrowStart()));
    }

    private Long countTodayPicks(Long userId) {
        return driftBottlePickLogMapper.selectCount(new LambdaQueryWrapper<DriftBottlePickLog>()
                .eq(DriftBottlePickLog::getUserId, userId)
                .ge(DriftBottlePickLog::getCreateTime, todayStart())
                .lt(DriftBottlePickLog::getCreateTime, tomorrowStart())
                .ne(DriftBottlePickLog::getDeleteState, Constant.DELETE_STATE_TRUE));
    }

    private Long countTodayComments(Long userId) {
        return driftBottleCommentMapper.selectCount(new LambdaQueryWrapper<DriftBottleComment>()
                .eq(DriftBottleComment::getUserId, userId)
                .ge(DriftBottleComment::getCreateTime, todayStart())
                .lt(DriftBottleComment::getCreateTime, tomorrowStart())
                .ne(DriftBottleComment::getDeleteState, Constant.DELETE_STATE_TRUE));
    }

    private Date todayStart() {
        return Date.from(LocalDate.now(ForumDateTimes.ZONE_SHANGHAI)
                .atStartOfDay(ForumDateTimes.ZONE_SHANGHAI)
                .toInstant());
    }

    private Date tomorrowStart() {
        return Date.from(LocalDate.now(ForumDateTimes.ZONE_SHANGHAI)
                .plusDays(1)
                .atStartOfDay(ZoneId.of("Asia/Shanghai"))
                .toInstant());
    }

    private void assertDailyLimit(Long used, int limit, String message) {
        if (used != null && used >= limit) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, message));
        }
    }

    private void assertCanPost(AiUserContext user) {
        if (user != null && user.getState() != null && user.getState() == 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_BANNED));
        }
    }

    private String normalizeContent(String raw, int minLen, int maxLen) {
        String value = normalizeOptional(raw, maxLen);
        if (!StringUtils.hasText(value) || value.length() < minLen) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "内容长度不合法"));
        }
        String lower = value.toLowerCase();
        if (lower.contains("http://") || lower.contains("https://") || lower.contains("www.")) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "漂流瓶暂不支持外链"));
        }
        return value;
    }

    private String normalizeMood(String raw) {
        String value = normalizeOptional(raw, 20);
        if (!MOOD_TYPES.contains(value)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "心情标签不合法"));
        }
        return value;
    }

    private String normalizeReasonType(String raw) {
        String value = normalizeOptional(raw, 30);
        if (!StringUtils.hasText(value)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "举报原因不能为空"));
        }
        return value;
    }

    private void assertAiContentAllowed(String content) {
        String rejectReason = aiHubService.validateText(content);
        if (StringUtils.hasText(rejectReason)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CONTENT_VIOLATION, rejectReason));
        }
    }

    private String normalizeOptional(String raw, int maxLen) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.length() > maxLen) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "内容过长"));
        }
        return value.isEmpty() ? null : value;
    }

    private String anonymousName(int index) {
        int value = index;
        StringBuilder suffix = new StringBuilder();
        do {
            suffix.insert(0, (char) ('A' + value % 26));
            value = value / 26 - 1;
        } while (value >= 0);
        return "路过的人 " + suffix;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
