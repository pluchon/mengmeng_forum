package org.example.forumdemo.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ArticleStatus;
import org.example.forumdemo.common.enums.DanmakuColorCode;
import org.example.forumdemo.common.enums.DanmakuFontSize;
import org.example.forumdemo.common.enums.DanmakuMode;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.utils.AiAuditUtils;
import org.example.forumdemo.common.utils.UserMuteGuard;
import org.example.forumdemo.converter.DanmakuConverter;
import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.entity.db.ArticleVideoDanmaku;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.article.SendDanmakuRequest;
import org.example.forumdemo.entity.vo.article.DanmakuItemVO;
import org.example.forumdemo.mapper.ArticleVideoDanmakuMapper;
import org.example.forumdemo.service.impl.remote.UserInternalLookupService;
import org.example.forumdemo.service.interfaces.article.ArticleService;
import org.example.forumdemo.service.interfaces.article.ArticleVideoDanmakuService;
import org.example.forumdemo.service.interfaces.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ArticleVideoDanmakuServiceImpl implements ArticleVideoDanmakuService {

    private static final byte DELETE_TRUE = 1;
    private static final byte MEDIA_TYPE_VIDEO = 1;

    @Autowired
    private ArticleVideoDanmakuMapper articleVideoDanmakuMapper;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserInternalLookupService userInternalLookupService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DanmakuItemVO sendDanmaku(SendDanmakuRequest req, Long loginUserId) {
        User loginUser = userService.queryUserByUserId(loginUserId);
        UserMuteGuard.assertCanPost(loginUser);
        if (req == null || req.getArticleId() == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        String content = req.getContent() == null ? "" : req.getContent().trim();
        if (!StringUtils.hasText(content)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_DANMAKU_CONTENT_EMPTY));
        }
        if (content.length() > Constant.DANMAKU_MAX_CONTENT_LEN) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_DANMAKU_CONTENT_TOO_LONG));
        }
        Byte colorCode = req.getColorCode() == null ? DanmakuColorCode.WHITE.getCode() : req.getColorCode();
        if (!DanmakuColorCode.isValid(colorCode)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_DANMAKU_COLOR_INVALID));
        }
        Byte mode = req.getMode() == null ? DanmakuMode.SCROLL.getCode() : req.getMode();
        if (!DanmakuMode.isValid(mode)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_DANMAKU_MODE_INVALID));
        }
        Byte fontSize = req.getFontSize() == null ? DanmakuFontSize.STANDARD.getCode() : req.getFontSize();
        if (!DanmakuFontSize.isValid(fontSize)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_DANMAKU_FONT_SIZE_INVALID));
        }
        Integer videoTimeMs = req.getVideoTimeMs();
        if (videoTimeMs == null || videoTimeMs < 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_DANMAKU_TIME_INVALID));
        }
        Article article = articleService.selectArticleByArticleId(req.getArticleId());
        assertPublishedVideoArticle(article);
        assertDanmakuRateLimit(loginUserId, req.getArticleId());
        assertDanmakuMinuteLimit(loginUserId, req.getArticleId());
        assertNotDuplicateContent(loginUserId, req.getArticleId(), content);
        assertTextAllowed(content);
        ArticleVideoDanmaku row = new ArticleVideoDanmaku();
        row.setArticleId(req.getArticleId());
        row.setUserId(loginUserId);
        row.setVideoTimeMs(videoTimeMs);
        row.setContent(content);
        row.setColorCode(colorCode);
        row.setMode(mode);
        row.setFontSize(fontSize);
        row.setDeleteState((byte) 0);
        if (articleVideoDanmakuMapper.insert(row) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
        }
        return DanmakuConverter.toItemVO(row, loginUser);
    }

    @Override
    public List<DanmakuItemVO> listByTimeWindow(Long articleId, Integer fromMs, Integer toMs) {
        if (articleId == null || fromMs == null || toMs == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (fromMs < 0 || toMs < 0 || toMs < fromMs) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (toMs - fromMs > Constant.DANMAKU_QUERY_MAX_WINDOW_MS) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_DANMAKU_WINDOW_TOO_LARGE));
        }
        Article article = articleService.selectArticleByArticleId(articleId);
        assertPublishedVideoArticle(article);
        List<ArticleVideoDanmaku> rows = articleVideoDanmakuMapper.selectList(
                new LambdaQueryWrapper<ArticleVideoDanmaku>()
                        .eq(ArticleVideoDanmaku::getArticleId, articleId)
                        .ne(ArticleVideoDanmaku::getDeleteState, DELETE_TRUE)
                        .ge(ArticleVideoDanmaku::getVideoTimeMs, fromMs)
                        .le(ArticleVideoDanmaku::getVideoTimeMs, toMs)
                        .orderByAsc(ArticleVideoDanmaku::getVideoTimeMs)
                        .orderByAsc(ArticleVideoDanmaku::getId));
        return toItemVOListWithUsers(rows);
    }

    private List<DanmakuItemVO> toItemVOListWithUsers(List<ArticleVideoDanmaku> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = rows.stream().map(ArticleVideoDanmaku::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of() : userInternalLookupService.loadActiveUsers(userIds);
        return DanmakuConverter.toItemVOList(rows, userMap);
    }

    // 全部长度走文本审核；审核服务异常时降级放行
    private void assertTextAllowed(String content) {
        try {
            String violation = AiAuditUtils.isTextAllowed(content);
            if (violation != null) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_CONTENT_VIOLATION, violation));
            }
        } catch (ApplicationException ex) {
            if (ex.getErrorResult() != null
                    && ResultCode.FAILED_CONTENT_VIOLATION.getCode() == ex.getErrorResult().getCode()) {
                throw ex;
            }
            log.warn("弹幕文本审核服务不可用，降级放行: {}", ex.getMessage());
        }
    }

    // 仅已发布视频帖允许弹幕
    private void assertPublishedVideoArticle(Article article) {
        if (article.getMediaType() == null || article.getMediaType() != MEDIA_TYPE_VIDEO) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_DANMAKU_NOT_VIDEO));
        }
        if (!StringUtils.hasText(article.getVideoUrl())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_DANMAKU_NOT_VIDEO));
        }
        if (!ArticleStatus.isPublished(article.getStatus())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_DANMAKU_NOT_PUBLISHED));
        }
    }

    // 同一用户同一视频 2 秒 1 条
    private void assertDanmakuRateLimit(Long userId, Long articleId) {
        String key = Constant.REDIS_KEY_DANMAKU_RATE + userId + ":" + articleId;
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", Constant.REDIS_TTL_DANMAKU_RATE, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(ok)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_DANMAKU_RATE_LIMIT));
        }
    }

    // 同一用户同一视频每分钟上限
    private void assertDanmakuMinuteLimit(Long userId, Long articleId) {
        String key = Constant.REDIS_KEY_DANMAKU_MINUTE + userId + ":" + articleId;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, Constant.REDIS_TTL_DANMAKU_MINUTE, TimeUnit.SECONDS);
        }
        if (count != null && count > Constant.DANMAKU_MAX_PER_MINUTE) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_DANMAKU_MINUTE_LIMIT));
        }
    }

    // 短时间重复相同内容拦截
    private void assertNotDuplicateContent(Long userId, Long articleId, String content) {
        String key = Constant.REDIS_KEY_DANMAKU_DUP + userId + ":" + articleId;
        String normalized = content.trim();
        String last = stringRedisTemplate.opsForValue().get(key);
        if (normalized.equals(last)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_DANMAKU_DUPLICATE));
        }
        stringRedisTemplate.opsForValue().set(key, normalized, Constant.REDIS_TTL_DANMAKU_DUP, TimeUnit.SECONDS);
    }
}
