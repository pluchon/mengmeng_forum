package org.example.forumdemo.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ArticleStatus;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.utils.*;
import org.example.forumdemo.converter.ArticleConverter;
import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.entity.db.ArticleImage;
import org.example.forumdemo.entity.db.ArticleLike;
import org.example.forumdemo.entity.db.Board;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.article.PublishArticleRequest;
import org.example.forumdemo.entity.dto.article.UpdateArticleRequest;
import org.example.forumdemo.entity.dto.article.ValidateTextRequest;
import org.example.forumdemo.entity.vo.article.ArticleBriefVO;
import org.example.forumdemo.entity.vo.article.ArticleDetailResponse;
import org.example.forumdemo.entity.vo.article.ArticleListByUserIdPageResponse;
import org.example.forumdemo.entity.vo.article.ArticleValidateTextVO;
import org.example.forumdemo.entity.vo.article.AuditStatusResponse;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.mq.ArticleAuditResultMqVO;
import org.example.forumdemo.entity.vo.user.UserBriefVO;
import org.example.forumdemo.mapper.ArticleImageMapper;
import org.example.forumdemo.mapper.ArticleLikeMapper;
import org.example.forumdemo.mapper.ArticleMapper;
import org.example.forumdemo.service.interfaces.article.ArticleAuditService;
import org.example.forumdemo.service.interfaces.article.ArticleHotRankingService;
import org.example.forumdemo.service.interfaces.article.ArticleMediaService;
import org.example.forumdemo.service.interfaces.article.ArticlePublishSideEffectService;
import org.example.forumdemo.service.interfaces.article.ArticleService;
import org.example.forumdemo.service.interfaces.article.ArticleTagService;
import org.example.forumdemo.service.interfaces.board.BoardService;
import org.example.forumdemo.service.interfaces.common.IpRegionService;
import org.example.forumdemo.service.interfaces.favorite.FavoriteArticleService;
import org.example.forumdemo.service.interfaces.search.ArticleSearchIndexService;
import org.example.forumdemo.service.interfaces.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ArticleServiceImpl implements ArticleService {

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private BoardService boardService;

    @Autowired
    private ArticleLikeMapper articleLikeMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ArticleImageMapper articleImageMapper;

    /**
     * 用 @Lazy 是为了打破 ArticleService ↔ FavoriteArticleService 之间的潜在循环依赖
     * (后者依赖 ArticleService.selectArticleByArticleId 校验帖子合法性)
     */
    @Autowired
    @org.springframework.context.annotation.Lazy
    private FavoriteArticleService favoriteArticleService;

    @Autowired
    private ArticleTagService articleTagService;

    @Autowired
    private ArticleSearchIndexService articleSearchIndexService;

    @Autowired
    private IpRegionService ipRegionService;

    @Autowired
    private ArticleHotRankingService articleHotRankingService;

    @Autowired
    private ArticleMediaService articleMediaService;

    @Autowired
    private ArticlePublishSideEffectService articlePublishSideEffectService;

    @Autowired
    private ArticleAuditService articleAuditService;

    /** 状态 / 删除标记：1 表示禁用 / 已删除 */
    private static final byte STATE_FORBIDDEN = 1;
    private static final byte DELETE_TRUE = 1;

    // ============================================================
    // 草稿 / 发布
    // ============================================================
    /**
     * 创建草稿. 异步审核版本: 此处不再调用同步 AI 文本审核,
     * 内容合规检查统一推迟到 submitForAudit 时由 LangGraph 集中完成,
     * 避免 createDraft -> submitForAudit 走两次 LLM 调用浪费.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDraft(PublishArticleRequest req, Long userId) {
        User user = userService.queryUserByUserId(userId);
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        UserMuteGuard.assertCanPost(user);
        Article add = new Article();
        add.setUserId(userId);
        add.setTitle(req.getTitle());
        add.setContent(req.getContent());
        add.setBoardId(req.getBoardId());
        add.setContentType(req.getContentType());
        add.setStatus(ArticleStatus.DRAFT.getCode());
        if (articleMapper.insert(add) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
        }
        articleTagService.bindArticleTags(add.getId(), req.getBoardId(), req.getTagIds());
        return add.getId();
    }

    /**
     * 异步审核版本的发布:
     *  - 仅允许 APPROVED 状态 -> PUBLISHED (即"审核通过后用户手动点发布")
     *  - 若想直接发布草稿, 请改用 submitForAudit (内部默认自动发布)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishArticle(Long articleId, Long userId) {
        Article article = selectArticleByArticleId(articleId);
        if (!Objects.equals(article.getUserId(), userId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
        if (ArticleStatus.isPublished(article.getStatus())) {
            return;
        }
        if (article.getStatus() == null || article.getStatus() != ArticleStatus.APPROVED.getCode()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PUBLISH_NEED_APPROVED));
        }
        String ipRegion = ipRegionService.resolveRegion(RequestIpUtils.resolveClientIp());
        LambdaUpdateWrapper<Article> publishUpdate = new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .eq(Article::getStatus, ArticleStatus.APPROVED.getCode())
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN)
                .set(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .set(Article::getAuditFinishedAt, new Date());
        if (StringUtils.hasText(ipRegion)) {
            publishUpdate.set(Article::getIpRegion, ipRegion);
        }
        int result = articleMapper.update(null, publishUpdate);
        if (result <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UPDATE_ARTICLE));
        }
        articlePublishSideEffectService.promotePublishedExposure(articleId, userId, article.getBoardId());
        articleHotRankingService.addToHotRanking(articleId);
        articleSearchIndexService.syncPublishedArticle(articleId);
    }

    // ============================================================
    // 详情
    // ============================================================
    @Override
    public ArticleDetailResponse queryArticleDetailByArticleId(Long articleId, Long loginUserId) {
        if (articleId == null || articleId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        Article articleInfo = selectArticleByArticleId(articleId);
        if (!ArticleStatus.isPublished(articleInfo.getStatus()) && !Objects.equals(loginUserId, articleInfo.getUserId())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        if (articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId).setSql("visit_count = visit_count + 1")) > 0) {
            articleInfo.setVisitCount(articleInfo.getVisitCount() + 1);
            if (ArticleStatus.isPublished(articleInfo.getStatus())) {
                stringRedisTemplate.opsForZSet().incrementScore(Constant.REDIS_KEY_HOT_ARTICLES,
                        String.valueOf(articleId), Constant.HOT_SCORE_WEIGHT_VISIT);
            }
        }
        User userInfo = userService.getUserInfoById(articleInfo.getUserId());
        if (userInfo == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        Board boardInfo = boardService.requireBoardEntity(articleInfo.getBoardId());
        if (boardInfo == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        boolean isOwner = Objects.equals(loginUserId, articleInfo.getUserId());
        boolean isLiked = false;
        boolean isFavorited = false;
        if (loginUserId != null && loginUserId > 0) {
            ArticleLike like = articleLikeMapper.selectOne(new LambdaQueryWrapper<ArticleLike>()
                    .eq(ArticleLike::getArticleId, articleId).eq(ArticleLike::getUserId, loginUserId));
            isLiked = (like != null);
            isFavorited = favoriteArticleService.isFavorited(articleId, loginUserId);
        }
        ArticleDetailResponse resp = new ArticleDetailResponse(
                new UserBriefVO(userInfo), articleInfo, boardInfo, isOwner, isLiked, isFavorited);
        resp.setImageUrls(articleMediaService.queryArticleImageUrls(articleId));
        resp.setTags(articleTagService.listByArticleId(articleId));
        return resp;
    }

    @Override
    public Article selectArticleByArticleId(Long articleId) {
        Article info = articleMapper.selectOne(new LambdaQueryWrapper<Article>()
                .eq(Article::getId, articleId)
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN));
        if (info == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        return info;
    }

    // ============================================================
    // 修改
    // ============================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateArticle(UpdateArticleRequest req, Long loginUserId) {
        Long articleId = req.getArticleId();
        Article article = selectArticleByArticleId(articleId);
        if (!Objects.equals(article.getUserId(), loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UPDATE_ARTICLE));
        }
        User editor = userService.queryUserByUserId(loginUserId);
        UserMuteGuard.assertCanPost(editor);
        if (ArticleStatus.isEditingLocked(article.getStatus())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AUDIT_EDIT_LOCKED));
        }
        Long galleryCount = articleImageMapper.selectCount(new LambdaQueryWrapper<ArticleImage>()
                .eq(ArticleImage::getArticleId, articleId).ne(ArticleImage::getDeleteState, 1));
        if (galleryCount != null && galleryCount > 0) {
            String raw = req.getContent() == null ? "" : req.getContent();
            String plain = raw.replaceAll("<[^>]+>", "").trim();
            if (plain.length() < Constant.ARTICLE_GALLERY_MIN_CONTENT_LEN) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_ARTICLE_GALLERY_NEEDS_CONTENT));
            }
        }
        boolean wasPublished = ArticleStatus.isPublished(article.getStatus());
        var updateUw = new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN)
                .ne(Article::getStatus, ArticleStatus.PENDING_AUDIT.getCode())
                .set(Article::getTitle, req.getTitle())
                .set(Article::getContent, req.getContent())
                .set(Article::getStatus, ArticleStatus.DRAFT.getCode());
        if (wasPublished) {
            updateUw.set(Article::getAuditRetryCount, 0)
                    .set(Article::getAuditTaskId, null)
                    .set(Article::getAuditResultMessage, null)
                    .set(Article::getAuditSubmittedAt, null)
                    .set(Article::getAuditFinishedAt, null);
        }
        int result = articleMapper.update(null, updateUw);
        if (result <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
        stringRedisTemplate.delete(Constant.REDIS_KEY_ARTICLE_SUMMARY + articleId);
        if (wasPublished) {
            stringRedisTemplate.opsForZSet().remove(Constant.REDIS_KEY_HOT_ARTICLES, String.valueOf(articleId));
            boardService.deleteOneById(article.getBoardId());
            userService.deleteOneById(loginUserId);
            articleSearchIndexService.removeArticle(articleId);
        }
        if (req.getTagIds() != null) {
            articleTagService.bindArticleTags(articleId, article.getBoardId(), req.getTagIds());
        }
    }

    // ============================================================
    // 删除
    // ============================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteArticle(Long articleId, Long loginUserId) {
        Article article = selectArticleByArticleId(articleId);
        if (!Objects.equals(article.getUserId(), loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_DELETE_ARTICLE));
        }
        int result = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN)
                .set(Article::getDeleteState, DELETE_TRUE));
        if (result <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
        }
        if (ArticleStatus.isPublished(article.getStatus())) {
            userService.deleteOneById(loginUserId);
            boardService.deleteOneById(article.getBoardId());
        }
        stringRedisTemplate.opsForZSet().remove(Constant.REDIS_KEY_HOT_ARTICLES, String.valueOf(articleId));
        articleSearchIndexService.removeArticle(articleId);
        log.info("帖子 {} 已逻辑删除并从热帖榜单移除", articleId);
    }

    // ============================================================
    // 回复数维护
    // ============================================================
    @Override
    public void addReply(Long articleId) {
        selectArticleByArticleId(articleId);
        int result = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .setSql("reply_count = reply_count + 1"));
        if (result <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
        }
        stringRedisTemplate.opsForZSet().incrementScore(Constant.REDIS_KEY_HOT_ARTICLES,
                String.valueOf(articleId), Constant.HOT_SCORE_WEIGHT_REPLY);
    }

    @Override
    public void deleteReply(Long articleId) {
        selectArticleByArticleId(articleId);
        int result = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .setSql("reply_count = GREATEST(reply_count - 1, 0)"));
        if (result <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
        }
        stringRedisTemplate.opsForZSet().incrementScore(Constant.REDIS_KEY_HOT_ARTICLES,
                String.valueOf(articleId), -Constant.HOT_SCORE_WEIGHT_REPLY);
    }

    @Override
    public void addSubReply(Long articleId) {
        selectArticleByArticleId(articleId);
        int result = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .setSql("sub_reply_count = sub_reply_count + 1"));
        if (result <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
        }
        stringRedisTemplate.opsForZSet().incrementScore(Constant.REDIS_KEY_HOT_ARTICLES,
                String.valueOf(articleId), Constant.HOT_SCORE_WEIGHT_REPLY);
    }

    @Override
    public void deleteSubReply(Long articleId) {
        selectArticleByArticleId(articleId);
        int result = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .setSql("sub_reply_count = GREATEST(sub_reply_count - 1, 0)"));
        if (result <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
        }
        stringRedisTemplate.opsForZSet().incrementScore(Constant.REDIS_KEY_HOT_ARTICLES,
                String.valueOf(articleId), -Constant.HOT_SCORE_WEIGHT_REPLY);
    }

    // ============================================================
    // 用户主页帖子列表
    // ============================================================
    @Override
    public PageResult<ArticleBriefVO> queryArticleListByUserIdWithPage(Long userId, Long loginUserId, Integer pageNum, Integer pageSize) {
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<Article> page = buildUserArticlePage(userId, validPageNum, validPageSize);
        boolean isOwner = Objects.equals(userId, loginUserId);
        Page<Article> result = articleMapper.selectPage(page, buildUserArticleWrapper(userId, isOwner));
        return ArticleConverter.toBriefPage(new PageResult<>(result.getRecords(), result.getTotal(), validPageNum, validPageSize, result.getPages(), result.hasNext()));
    }

    @Override
    public ArticleListByUserIdPageResponse queryArticleListByUserIdWithPageAndUserInfo(Long userId, Long loginUserId, Integer pageNum, Integer pageSize) {
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<Article> page = buildUserArticlePage(userId, validPageNum, validPageSize);
        boolean isOwner = Objects.equals(userId, loginUserId);
        Page<Article> result = articleMapper.selectPage(page, buildUserArticleWrapper(userId, isOwner));
        User user = userService.getUserInfoById(userId);
        PageResult<ArticleBriefVO> pageResult = ArticleConverter.toBriefPage(new PageResult<>(result.getRecords(), result.getTotal(),
                validPageNum, validPageSize, result.getPages(), result.hasNext()));
        return new ArticleListByUserIdPageResponse(pageResult, new UserBriefVO(user), isOwner);
    }

    private Page<Article> buildUserArticlePage(Long userId, int pageNum, int pageSize) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        return PageUtils.getPage(pageNum, pageSize);
    }

    private LambdaQueryWrapper<Article> buildUserArticleWrapper(Long userId, boolean isOwner) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Article::getUserId, userId).ne(Article::getDeleteState, DELETE_TRUE).ne(Article::getState, STATE_FORBIDDEN);
        if (!isOwner) {
            wrapper.eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode());
        }
        wrapper.orderByDesc(Article::getUpdateTime);
        return wrapper;
    }

    @Override
    public PageResult<ArticleBriefVO> queryDeletedArticleListWithPage(Long loginUserId, Integer pageNum, Integer pageSize) {
        if (loginUserId == null || loginUserId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<Article> page = PageUtils.getPage(validPageNum, validPageSize);
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Article::getUserId, loginUserId)
                .eq(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN)
                .orderByDesc(Article::getUpdateTime);
        Page<Article> result = articleMapper.selectPage(page, wrapper);
        return ArticleConverter.toBriefPage(new PageResult<>(result.getRecords(), result.getTotal(), validPageNum, validPageSize, result.getPages(), result.hasNext()));
    }

    // ============================================================
    // 热帖榜
    // ============================================================
    @Override
    public List<Long> getHotArticleList(Integer topN) {
        return articleHotRankingService.getHotArticleList(topN);
    }

    @Override
    public void rebuildHotArticleRanking() {
        articleHotRankingService.rebuildHotArticleRanking();
    }

    // ============================================================
    // AI 摘要 + 内容审核
    // ============================================================
    @Override
    public String getArticleSummary(Long articleId) {
        String cacheKey = Constant.REDIS_KEY_ARTICLE_SUMMARY + articleId;
        Article article = selectArticleByArticleId(articleId);
        if (article == null) {
            return Constant.SUMMARY_ARTICLE_NOT_FOUND;
        }
        String content = article.getContent();
        String plainText = (content == null) ? "" : content.replaceAll("<[^>]+>", "").trim();
        if (plainText.length() < 50) {
            stringRedisTemplate.delete(cacheKey);
            return String.format(Constant.SUMMARY_ARTICLE_TOO_SHORT, plainText.length());
        }
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isBlank()) {
            if (!isSummaryTooSimilarToBody(cached, plainText)) {
                return cached;
            }
            stringRedisTemplate.delete(cacheKey);
        }
        String summary;
        try {
            summary = AiAuditUtils.getSummary(content);
        } catch (ApplicationException ex) {
            log.warn("帖子 {} AI 摘要调用失败: {}", articleId, ex.getMessage());
            return Constant.SUMMARY_AI_SERVICE_UNAVAILABLE;
        }
        if (summary != null && !summary.trim().isEmpty()) {
            if (isSummaryTooSimilarToBody(summary, plainText)) {
                log.warn("帖子 {} AI 摘要与正文高度重合，不写入缓存", articleId);
                return "AI 返回内容与正文过于相似，请充实正文后再尝试智能导读。";
            }
            stringRedisTemplate.opsForValue().set(cacheKey, summary, Constant.REDIS_TTL_ARTICLE_SUMMARY, TimeUnit.SECONDS);
            return summary;
        }
        log.warn("帖子 {} AI 摘要生成失败或返回为空", articleId);
        return "AI 未能生成有效摘要，请稍后重试或充实正文后再试。";
    }

    private static boolean isSummaryTooSimilarToBody(String summary, String plainText) {
        if (summary == null || plainText == null) {
            return false;
        }
        String sNorm = summary.replaceAll("\\s+", "");
        String pNorm = plainText.replaceAll("\\s+", "");
        if (sNorm.isEmpty() || pNorm.isEmpty()) {
            return false;
        }
        if (sNorm.equals(pNorm)) {
            return true;
        }
        int minLen = Math.min(sNorm.length(), pNorm.length());
        if (minLen < 20) {
            return false;
        }
        return pNorm.length() > 40 && (sNorm.contains(pNorm) || pNorm.contains(sNorm));
    }

    @Override
    public String validateContent(String content) {
        return AiAuditUtils.isTextAllowed(content);
    }

    @Override
    public ArticleValidateTextVO validateContentResult(String content) {
        String violation = validateContent(content);
        return ArticleConverter.toValidateTextVO(violation == null, violation);
    }

    @Override
    public ArticleValidateTextVO validateContentResult(ValidateTextRequest request) {
        String content = request != null && request.getContent() != null ? request.getContent()
                : request == null ? null : request.getText();
        return validateContentResult(content);
    }

    // ============================================================
    // 封面 / 相册 / 视频（委托 ArticleMediaService）
    // ============================================================
    @Override
    public void updateArticleCoverByUrl(Long articleId, String coverUrl, Long loginUserId) {
        articleMediaService.updateArticleCoverByUrl(articleId, coverUrl, loginUserId);
    }

    @Override
    public void replaceArticleImages(Long articleId, Long loginUserId, List<String> imageUrls) {
        articleMediaService.replaceArticleImages(articleId, loginUserId, imageUrls);
    }

    @Override
    public void setArticleVideo(Long articleId, Long loginUserId, String videoUrl) {
        articleMediaService.setArticleVideo(articleId, loginUserId, videoUrl);
    }

    @Override
    public void clearArticleVideo(Long articleId, Long loginUserId) {
        articleMediaService.clearArticleVideo(articleId, loginUserId);
    }

    @Override
    public List<String> queryArticleImageUrls(Long articleId) {
        return articleMediaService.queryArticleImageUrls(articleId);
    }

    // ============================================================
    // 异步审核（委托 ArticleAuditService）
    // ============================================================
    @Override
    public String submitForAudit(Long articleId, Long loginUserId, Boolean notifyEmail) {
        return articleAuditService.submitForAudit(articleId, loginUserId, notifyEmail);
    }

    @Override
    public void applyAuditResult(ArticleAuditResultMqVO result) {
        articleAuditService.applyAuditResult(result);
    }

    @Override
    public AuditStatusResponse getAuditStatus(Long articleId, Long loginUserId) {
        return articleAuditService.getAuditStatus(articleId, loginUserId);
    }

    @Override
    public int sweepStuckAuditTasks() {
        return articleAuditService.sweepStuckAuditTasks();
    }
}
