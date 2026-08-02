package org.pluchon.forum.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ArticleStatus;
import org.pluchon.forum.common.enums.ArticleType;
import org.pluchon.forum.common.enums.HotArticleTrendDirection;
import org.pluchon.forum.common.enums.QuestionStatus;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.common.utils.RequestIpUtils;
import org.pluchon.forum.common.utils.TransactionHooks;
import org.pluchon.forum.converter.ArticleConverter;
import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.db.ArticleImage;
import org.pluchon.forum.entity.db.ArticleLike;
import org.pluchon.forum.entity.db.Board;
import org.pluchon.forum.api.auth.UserInternalVO;
import org.pluchon.forum.entity.db.UserRecommendFeedback;
import org.pluchon.forum.entity.dto.article.PublishArticleRequest;
import org.pluchon.forum.entity.dto.article.UpdateArticleRequest;
import org.pluchon.forum.entity.dto.article.ValidateTextRequest;
import org.pluchon.forum.entity.vo.article.ArticleBriefVO;
import org.pluchon.forum.entity.vo.article.ArticleDetailResponse;
import org.pluchon.forum.entity.vo.article.ArticleListByUserIdPageResponse;
import org.pluchon.forum.entity.vo.article.ArticleValidateTextVO;
import org.pluchon.forum.entity.vo.article.AuditStatusResponse;
import org.pluchon.forum.entity.vo.article.HotArticleListItemVO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.mq.ArticleAuditResultMqVO;
import org.pluchon.forum.entity.vo.user.UserBriefVO;
import org.pluchon.forum.mapper.ArticleImageMapper;
import org.pluchon.forum.mapper.ArticleLikeMapper;
import org.pluchon.forum.mapper.ArticleMapper;
import org.pluchon.forum.mapper.UserRecommendFeedbackMapper;
import org.pluchon.forum.service.impl.remote.ContentUserLookupService;
import org.pluchon.forum.service.impl.remote.ContentUserMuteGuard;
import org.pluchon.forum.service.remote.ContentAiGatewayService;
import org.pluchon.forum.service.interfaces.article.ArticleAuditService;
import org.pluchon.forum.service.interfaces.article.ArticleHotRankingService;
import org.pluchon.forum.service.interfaces.article.ArticleMediaService;
import org.pluchon.forum.service.interfaces.article.ArticlePublishSideEffectService;
import org.pluchon.forum.service.interfaces.article.ArticleService;
import org.pluchon.forum.service.interfaces.article.ArticleTagService;
import org.pluchon.forum.service.interfaces.board.BoardService;
import org.pluchon.forum.service.interfaces.common.IpRegionService;
import org.pluchon.forum.service.interfaces.favorite.FavoriteArticleService;
import org.pluchon.forum.cloud.feign.ContentGrowthInternalFeignClient;
import org.pluchon.forum.service.interfaces.search.ArticleSearchIndexService;
import org.pluchon.forum.service.impl.remote.ContentFollowLookupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ArticleServiceImpl implements ArticleService {

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private ContentUserLookupService userInternalLookupService;

    @Autowired
    private UserRecommendFeedbackMapper userRecommendFeedbackMapper;

    @Autowired
    private ContentFollowLookupService userFollowService;

    @Autowired
    private ContentGrowthInternalFeignClient contentGrowthInternalFeignClient;

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

    @Autowired
    private ContentAiGatewayService contentAiGatewayService;

    /** 状态 / 删除标记：1 表示禁用 / 已删除 */
    private static final byte STATE_FORBIDDEN = 1;
    private static final byte DELETE_TRUE = 1;
    private static final byte DELETE_FALSE = 0;
    private static final int SEMANTIC_SEARCH_CANDIDATE_LIMIT = 120;

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
        contentGrowthInternalFeignClient.requireFormalUser(userId);
        ArticleType articleType = ArticleType.fromCode(req.getArticleType());
        if (articleType == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_ARTICLE_TYPE_INVALID));
        }
        UserInternalVO user = userInternalLookupService.queryUserByUserId(userId);
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        ContentUserMuteGuard.assertCanPost(user);
        Article add = new Article();
        add.setUserId(userId);
        add.setTitle(req.getTitle());
        add.setContent(req.getContent());
        add.setBoardId(req.getBoardId());
        add.setContentType(req.getContentType());
        add.setArticleType(articleType.getCode());
        if (articleType == ArticleType.QUESTION) {
            add.setQuestionStatus(QuestionStatus.WAITING.getCode());
        }
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
        contentGrowthInternalFeignClient.requireFormalUser(userId);
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
                articleHotRankingService.incrementScore(articleId, Constant.HOT_SCORE_WEIGHT_VISIT);
            }
        }
        UserInternalVO userInfo = userInternalLookupService.getUserInfoById(articleInfo.getUserId());
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
                org.pluchon.forum.converter.ContentUserBriefConverter.toBrief(userInfo), articleInfo, boardInfo, isOwner, isLiked, isFavorited);
        resp.setIsNotInterested(loginUserId != null && loginUserId > 0
                && userRecommendFeedbackMapper.selectCount(new LambdaQueryWrapper<UserRecommendFeedback>()
                        .eq(UserRecommendFeedback::getUserId, loginUserId)
                        .eq(UserRecommendFeedback::getArticleId, articleId)
                        .eq(UserRecommendFeedback::getDeleteState, DELETE_FALSE)) > 0);
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
        UserInternalVO editor = userInternalLookupService.queryUserByUserId(loginUserId);
        ContentUserMuteGuard.assertCanPost(editor);
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
            final Long boardId = article.getBoardId();
            final Long authorId = loginUserId;
            TransactionHooks.afterCommit(() ->
                    articlePublishSideEffectService.rollbackPublishedExposure(articleId, boardId, authorId));
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
            Article current = articleMapper.selectById(articleId);
            if (current != null && Objects.equals(current.getDeleteState(), DELETE_TRUE)
                    && Objects.equals(current.getUserId(), loginUserId)) {
                return;
            }
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
        }
        final boolean wasPublished = ArticleStatus.isPublished(article.getStatus());
        final Long boardId = article.getBoardId();
        TransactionHooks.afterCommit(() -> {
            if (wasPublished) {
                articlePublishSideEffectService.rollbackPublishedExposure(articleId, boardId, loginUserId);
            } else {
                articleHotRankingService.removeFromRanking(articleId);
                articleSearchIndexService.removeArticle(articleId);
            }
        });
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
        articleHotRankingService.incrementScore(articleId, Constant.HOT_SCORE_WEIGHT_REPLY);
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
        articleHotRankingService.incrementScore(articleId, -Constant.HOT_SCORE_WEIGHT_REPLY);
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
        articleHotRankingService.incrementScore(articleId, Constant.HOT_SCORE_WEIGHT_REPLY);
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
        articleHotRankingService.incrementScore(articleId, -Constant.HOT_SCORE_WEIGHT_REPLY);
    }

    // ============================================================
    // 用户主页帖子列表
    // ============================================================
    @Override
    public PageResult<ArticleBriefVO> queryArticleListByUserIdWithPage(Long userId, Long loginUserId, Integer pageNum,
                                                                        Integer pageSize, Integer status, String keyword) {
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<Article> page = buildUserArticlePage(userId, validPageNum, validPageSize);
        boolean isOwner = Objects.equals(userId, loginUserId);
        Page<Article> result = articleMapper.selectPage(page, buildUserArticleWrapper(userId, isOwner, status, keyword));
        if (isOwner && StringUtils.hasText(keyword)
                && (result.getRecords() == null || result.getRecords().isEmpty())) {
            return querySemanticUserArticles(userId, status, keyword.trim(), validPageNum, validPageSize);
        }
        return ArticleConverter.toBriefPage(new PageResult<>(result.getRecords(), result.getTotal(), validPageNum, validPageSize, result.getPages(), result.hasNext()));
    }

    // 创作中心仅在标题/正文模糊匹配为空时请求 AI 对本人的候选帖子排序，状态与归属仍由本域复查。
    private PageResult<ArticleBriefVO> querySemanticUserArticles(Long userId, Integer status, String keyword,
                                                                  int pageNum, int pageSize) {
        List<Article> candidates = articleMapper.selectPage(
                new Page<>(1, SEMANTIC_SEARCH_CANDIDATE_LIMIT, false),
                buildUserArticleWrapper(userId, true, status, null)).getRecords();
        if (candidates == null || candidates.isEmpty()) {
            return ArticleConverter.toBriefPage(new PageResult<>(Collections.emptyList(), 0L, pageNum, pageSize, 0L, false));
        }
        List<Map<String, Object>> payload = new ArrayList<>(candidates.size());
        for (Article candidate : candidates) {
            Map<String, Object> item = new HashMap<>(3);
            item.put("candidateId", candidate.getId());
            item.put("articleId", candidate.getId());
            item.put("text", (candidate.getTitle() == null ? "" : candidate.getTitle())
                    + "\n" + (candidate.getContent() == null ? "" : candidate.getContent()));
            payload.add(item);
        }
        List<Long> rankedIds;
        try {
            rankedIds = contentAiGatewayService.ragVectorSearchArticles(keyword, payload);
            if (rankedIds == null || rankedIds.isEmpty()) {
                rankedIds = contentAiGatewayService.rankSemanticCandidates(keyword, payload);
            }
        } catch (RuntimeException exception) {
            log.warn("创作中心 AI 语义检索失败: {}", exception.getMessage());
            rankedIds = Collections.emptyList();
        }
        if (rankedIds == null || rankedIds.isEmpty()) {
            return ArticleConverter.toBriefPage(new PageResult<>(Collections.emptyList(), 0L, pageNum, pageSize, 0L, false));
        }
        List<Article> verified = articleMapper.selectList(buildUserArticleWrapper(userId, true, status, null)
                .in(Article::getId, rankedIds));
        Map<Long, Article> verifiedById = new HashMap<>();
        for (Article article : verified) {
            verifiedById.put(article.getId(), article);
        }
        List<Article> ranked = new ArrayList<>();
        for (Long articleId : rankedIds) {
            Article article = verifiedById.get(articleId);
            if (article != null) {
                ranked.add(article);
            }
        }
        long total = ranked.size();
        int fromIndex = Math.min((pageNum - 1) * pageSize, ranked.size());
        int toIndex = Math.min(fromIndex + pageSize, ranked.size());
        long pages = total == 0 ? 0L : (total + pageSize - 1) / pageSize;
        return ArticleConverter.toBriefPage(new PageResult<>(ranked.subList(fromIndex, toIndex), total,
                pageNum, pageSize, pages, toIndex < ranked.size()));
    }

    @Override
    public ArticleListByUserIdPageResponse queryArticleListByUserIdWithPageAndUserInfo(Long userId, Long loginUserId, Integer pageNum, Integer pageSize) {
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<Article> page = buildUserArticlePage(userId, validPageNum, validPageSize);
        boolean isOwner = Objects.equals(userId, loginUserId);
        Page<Article> result = articleMapper.selectPage(page, buildUserArticleWrapper(userId, isOwner, null, null));
        UserInternalVO user = userInternalLookupService.getUserInfoById(userId);
        PageResult<ArticleBriefVO> pageResult = ArticleConverter.toBriefPage(new PageResult<>(result.getRecords(), result.getTotal(),
                validPageNum, validPageSize, result.getPages(), result.hasNext()));
        UserBriefVO profileUser = org.pluchon.forum.converter.ContentUserBriefConverter.toBrief(user);
        if (!isOwner) {
            profileUser.setVipExpireAt(null);
            profileUser.setIpRegion(null);
        }
        return new ArticleListByUserIdPageResponse(pageResult, profileUser, isOwner);
    }

    private Page<Article> buildUserArticlePage(Long userId, int pageNum, int pageSize) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        return PageUtils.getPage(pageNum, pageSize);
    }

    private LambdaQueryWrapper<Article> buildUserArticleWrapper(Long userId, boolean isOwner, Integer status, String keyword) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Article::getUserId, userId).ne(Article::getDeleteState, DELETE_TRUE).ne(Article::getState, STATE_FORBIDDEN);
        if (!isOwner) {
            wrapper.eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode());
        } else if (status != null) {
            wrapper.eq(Article::getStatus, status);
        }
        if (isOwner && StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            wrapper.and(item -> item.like(Article::getTitle, normalizedKeyword)
                    .or()
                    .like(Article::getContent, normalizedKeyword));
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
    public PageResult<HotArticleListItemVO> queryHotArticleListWithPage(Integer pageNum, Integer pageSize,
            Long loginUserId) {
        PageResult<Long> idPage = articleHotRankingService.getHotArticlePage(pageNum, pageSize);
        if (idPage.getRecords() == null || idPage.getRecords().isEmpty()) {
            return new PageResult<>(List.of(), idPage.getTotal(), idPage.getPageNum(), idPage.getPageSize(),
                    idPage.getPages(), idPage.getHasNextPage());
        }
        List<Long> rankedIds = idPage.getRecords();
        Map<Long, HotArticleTrendDirection> trendDirections = articleHotRankingService.getTrendDirections(rankedIds);
        Map<Long, Article> articleMap = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                        .in(Article::getId, rankedIds)
                        .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                        .ne(Article::getDeleteState, DELETE_TRUE)
                        .ne(Article::getState, STATE_FORBIDDEN))
                .stream()
                .collect(Collectors.toMap(Article::getId, article -> article));
        Set<Long> authorIds = articleMap.values().stream()
                .map(Article::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserInternalVO> userMap = authorIds.isEmpty()
                ? Map.of()
                : userInternalLookupService.loadActiveUsers(authorIds);
        Set<Long> followingIds = loginUserId != null && loginUserId > 0
                ? userFollowService.listFollowingIds(loginUserId)
                : Set.of();
        long rankBase = (long) (idPage.getPageNum() - 1) * idPage.getPageSize();
        List<HotArticleListItemVO> records = new ArrayList<>(rankedIds.size());
        for (int index = 0; index < rankedIds.size(); index++) {
            Article article = articleMap.get(rankedIds.get(index));
            if (article == null) {
                continue;
            }
            UserInternalVO author = userMap.get(article.getUserId());
            if (author == null) {
                continue;
            }
            HotArticleListItemVO item = new HotArticleListItemVO();
            item.setRank(rankBase + index + 1);
            item.setArticle(ArticleConverter.toBriefVO(article));
            item.setUser(org.pluchon.forum.converter.ContentUserBriefConverter.toBrief(author));
            item.setFromFollowing(followingIds.contains(article.getUserId()));
            item.setTrendDirection(trendDirections.getOrDefault(article.getId(), HotArticleTrendDirection.STABLE));
            records.add(item);
        }
        return new PageResult<>(records, idPage.getTotal(), idPage.getPageNum(), idPage.getPageSize(),
                idPage.getPages(), idPage.getHasNextPage());
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
            summary = contentAiGatewayService.summarize(content);
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
        return contentAiGatewayService.validateText(content);
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
    public String submitForAudit(Long articleId, Long loginUserId) {
        return articleAuditService.submitForAudit(articleId, loginUserId);
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
