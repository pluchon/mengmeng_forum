package org.pluchon.forum.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ArticleStatus;
import org.pluchon.forum.common.enums.DanmakuColorCode;
import org.pluchon.forum.common.enums.DanmakuFontSize;
import org.pluchon.forum.common.enums.DanmakuMode;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.service.impl.remote.ContentUserMuteGuard;
import org.pluchon.forum.converter.DanmakuConverter;
import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.db.ArticleVideoDanmaku;
import org.pluchon.forum.entity.db.ArticleVideoDanmakuLike;
import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.entity.dto.article.SendDanmakuRequest;
import org.pluchon.forum.entity.vo.article.DanmakuItemVO;
import org.pluchon.forum.mapper.ArticleVideoDanmakuMapper;
import org.pluchon.forum.mapper.ArticleVideoDanmakuLikeMapper;
import org.pluchon.forum.service.impl.remote.ContentUserLookupService;
import org.pluchon.forum.service.interfaces.article.ArticleService;
import org.pluchon.forum.service.interfaces.article.ArticleVideoDanmakuService;
import org.pluchon.forum.service.interfaces.moderation.ContentModerationTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ArticleVideoDanmakuServiceImpl implements ArticleVideoDanmakuService {

    private static final byte DELETE_TRUE = 1;
    private static final byte MEDIA_TYPE_VIDEO = 1;

    @Autowired
    private ArticleVideoDanmakuMapper articleVideoDanmakuMapper;

    @Autowired
    private ArticleVideoDanmakuLikeMapper articleVideoDanmakuLikeMapper;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private ContentUserLookupService userInternalLookupService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ContentModerationTaskService contentModerationTaskService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DanmakuItemVO sendDanmaku(SendDanmakuRequest req, Long loginUserId) {
        UserInternalVO loginUser = userInternalLookupService.queryUserByUserId(loginUserId);
        ContentUserMuteGuard.assertCanPost(loginUser);
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
        contentModerationTaskService.scheduleDanmaku(row.getId(), content);
        return DanmakuConverter.toItemVO(row, loginUser);
    }

    @Override
    public List<DanmakuItemVO> listByTimeWindow(Long articleId, Integer fromMs, Integer toMs, Long loginUserId) {
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
        return toItemVOListWithUsers(rows, loginUserId);
    }

    private List<DanmakuItemVO> toItemVOListWithUsers(List<ArticleVideoDanmaku> rows, Long loginUserId) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = rows.stream().map(ArticleVideoDanmaku::getUserId).collect(Collectors.toSet());
        Map<Long, UserInternalVO> userMap = userIds.isEmpty() ? Map.of() : userInternalLookupService.loadActiveUsers(userIds);
        Set<Long> likedIds = loadLikedDanmakuIds(loginUserId, rows);
        List<DanmakuItemVO> list = DanmakuConverter.toItemVOList(rows, userMap);
        for (int i = 0; i < rows.size(); i++) {
            DanmakuItemVO vo = list.get(i);
            ArticleVideoDanmaku row = rows.get(i);
            if (vo == null || row == null || row.getId() == null) {
                continue;
            }
            vo.setLiked(likedIds.contains(row.getId()));
        }
        return list;
    }

    private Set<Long> loadLikedDanmakuIds(Long loginUserId, List<ArticleVideoDanmaku> rows) {
        if (loginUserId == null || rows == null || rows.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = rows.stream().map(ArticleVideoDanmaku::getId).filter(id -> id != null && id > 0).toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        List<ArticleVideoDanmakuLike> likes = articleVideoDanmakuLikeMapper.selectList(
                new LambdaQueryWrapper<ArticleVideoDanmakuLike>()
                        .eq(ArticleVideoDanmakuLike::getUserId, loginUserId)
                        .in(ArticleVideoDanmakuLike::getDanmakuId, ids));
        return likes.stream().map(ArticleVideoDanmakuLike::getDanmakuId).collect(Collectors.toSet());
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
