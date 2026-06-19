package org.example.forumdemo.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.config.OssConfig;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ArticleStatus;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.mq.ForumProducer;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.utils.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.entity.db.ArticleImage;
import org.example.forumdemo.entity.db.ArticleLike;
import org.example.forumdemo.entity.db.Board;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.article.PublishArticleRequest;
import org.example.forumdemo.entity.dto.article.UpdateArticleRequest;
import org.example.forumdemo.entity.vo.article.ArticleDetailResponse;
import org.example.forumdemo.entity.vo.article.ArticleListByUserIdPageResponse;
import org.example.forumdemo.entity.vo.article.AuditStatusResponse;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.mq.ArticleAuditResultMqVO;
import org.example.forumdemo.entity.vo.mq.ArticleAuditTaskMqVO;
import org.example.forumdemo.entity.vo.user.UserBriefVO;
import org.example.forumdemo.mapper.ArticleImageMapper;
import org.example.forumdemo.mapper.ArticleLikeMapper;
import org.example.forumdemo.mapper.ArticleMapper;
import org.example.forumdemo.service.impl.websocket.WebSocketPushService;
import org.example.forumdemo.service.interfaces.article.ArticleService;
import org.example.forumdemo.service.interfaces.board.BoardService;
import org.example.forumdemo.service.interfaces.favorite.FavoriteArticleService;
import org.example.forumdemo.service.interfaces.message.SystemMessageService;
import org.example.forumdemo.service.interfaces.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    @Autowired
    private OssConfig ossConfig;

    /**
     * 用 @Lazy 是为了打破 ArticleService ↔ FavoriteArticleService 之间的潜在循环依赖
     * (后者依赖 ArticleService.selectArticleByArticleId 校验帖子合法性)
     */
    @Autowired
    @org.springframework.context.annotation.Lazy
    private FavoriteArticleService favoriteArticleService;

    @Autowired
    private ForumProducer forumProducer;

    @Autowired
    private SystemMessageService systemMessageService;

    @Autowired
    private MailUtil mailUtil;

    @Autowired
    private WebSocketPushService webSocketPushService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 注入 Spring 代理实例的"自引用", 用于在内部方法间调用时仍能触发 @Transactional/AOP.
     * sweepStuckAuditTasks 直接 this.applyAuditResult(...) 会绕过代理 -> 事务不生效;
     * 改用 self.applyAuditResult(...) 才能保证每条兜底任务在独立事务里 commit/rollback.
     */
    @Autowired
    @org.springframework.context.annotation.Lazy
    private ArticleService self;

    @Autowired
    private org.example.forumdemo.service.interfaces.ai.AiHubService aiHubService;

    @Autowired
    private org.example.forumdemo.service.interfaces.article.ArticleTagService articleTagService;

    /** 状态 / 删除标记：1 表示禁用 / 已删除 */
    private static final byte STATE_FORBIDDEN = 1;
    private static final byte DELETE_TRUE = 1;
    private static final byte AUDIT_NOTIFY_EMAIL_ON = 1;
    private static final byte AUDIT_NOTIFY_EMAIL_OFF = 0;

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
        // 查看用户是否被禁言了
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
     *
     * 留出此接口主要给"自动发布模式临时切换为手动"的未来场景, 当前默认不会被直接调用,
     * 因为 applyAuditResult 通过 APPROVED 时会直接扭转到 PUBLISHED.
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
        // 仅 APPROVED 允许走手动发布; 其他状态必须先经过 submitForAudit
        if (article.getStatus() == null || article.getStatus() != ArticleStatus.APPROVED.getCode()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PUBLISH_NEED_APPROVED));
        }
        int result = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .eq(Article::getStatus, ArticleStatus.APPROVED.getCode())
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN)
                // 设置发布状态
                .set(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .set(Article::getAuditFinishedAt, new Date()));
        if (result <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UPDATE_ARTICLE));
        }
        userService.addOneById(userId);
        boardService.addOneById(article.getBoardId());
        // 新文章按当前各字段加权入榜
        stringRedisTemplate.opsForZSet().add(Constant.REDIS_KEY_HOT_ARTICLES,
                String.valueOf(articleId), computeHotScore(article));
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
        // 草稿仅作者本人可见
        if (!ArticleStatus.isPublished(articleInfo.getStatus()) && !Objects.equals(loginUserId, articleInfo.getUserId())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        // 先 +1 再返回，让前端拿到的是包含本次访问的最新值
        if (articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId).setSql("visit_count = visit_count + 1")) > 0) {
            articleInfo.setVisitCount(articleInfo.getVisitCount() + 1);
            // 仅已发布帖子写入热榜分数；否则 Redis ZINCRBY 会凭空创建成员，把草稿/审核中等 id 带进热帖榜
            if (ArticleStatus.isPublished(articleInfo.getStatus())) {
                stringRedisTemplate.opsForZSet().incrementScore(Constant.REDIS_KEY_HOT_ARTICLES,
                        String.valueOf(articleId), Constant.HOT_SCORE_WEIGHT_VISIT);
            }
        }
        User userInfo = userService.getUserInfoById(articleInfo.getUserId());
        if (userInfo == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        Board boardInfo = boardService.queryBoardByBoardId(articleInfo.getBoardId());
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
        resp.setImageUrls(queryArticleImageUrls(articleId));
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
    /**
     * 修改帖子. 异步审核版本:
     *  - PENDING_AUDIT 状态禁止编辑(必须等待结果)
     *  - 修改后状态强制重置为 DRAFT, 用户需要再次 submitForAudit 重新过审
     *  - 已发布(PUBLISHED) 帖子修改后也会回 DRAFT, 重新走审核 - 由 Redis 语义缓存兜底, 命中可秒过
     *  - 修改不再走同步 AI 审核, 内容审核统一推迟到 submitForAudit
     */
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
        // 联动规则: 若该帖子已有相册图, 新正文(纯文本)必须 ≥ 10 字; 否则要求用户先清空相册或写够正文
        // 这样保证"有图必须配足量正文"不变量在编辑阶段也成立, 防止经由 updateArticle 绕过校验
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
        // 内容变更, AI 摘要缓存失效
        stringRedisTemplate.delete(Constant.REDIS_KEY_ARTICLE_SUMMARY + articleId);
        // 帖子之前是已发布的, 现在退回草稿等回审 -> 从热帖榜移除, 不让"未审核"内容继续上榜
        if (wasPublished) {
            stringRedisTemplate.opsForZSet().remove(Constant.REDIS_KEY_HOT_ARTICLES, String.valueOf(articleId));
            boardService.deleteOneById(article.getBoardId());
            userService.deleteOneById(loginUserId);
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
        // 同步从热帖 ZSet 中移除，防止已删除帖子继续出现在榜单
        stringRedisTemplate.opsForZSet().remove(Constant.REDIS_KEY_HOT_ARTICLES, String.valueOf(articleId));
        log.info("帖子 {} 已逻辑删除并从热帖榜单移除", articleId);
    }

    // ============================================================
    // 回复数维护（被 ArticleReplyService / ArticleSubReplyService 调用）
    // reply_count = 一级回复(楼层)数; sub_reply_count = 楼中楼数
    // 热帖榜按 (reply_count + sub_reply_count) * WEIGHT_REPLY 计分
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
    public PageResult<Article> queryArticleListByUserIdWithPage(Long userId, Long loginUserId, Integer pageNum, Integer pageSize) {
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<Article> page = buildUserArticlePage(userId, validPageNum, validPageSize);
        boolean isOwner = Objects.equals(userId, loginUserId);
        Page<Article> result = articleMapper.selectPage(page, buildUserArticleWrapper(userId, isOwner));
        return new PageResult<>(result.getRecords(), result.getTotal(), validPageNum, validPageSize, result.getPages(), result.hasNext());
    }

    @Override
    public ArticleListByUserIdPageResponse queryArticleListByUserIdWithPageAndUserInfo(Long userId, Long loginUserId, Integer pageNum, Integer pageSize) {
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<Article> page = buildUserArticlePage(userId, validPageNum, validPageSize);
        boolean isOwner = Objects.equals(userId, loginUserId);
        Page<Article> result = articleMapper.selectPage(page, buildUserArticleWrapper(userId, isOwner));
        User user = userService.getUserInfoById(userId);
        PageResult<Article> pageResult = new PageResult<>(result.getRecords(), result.getTotal(),
                validPageNum, validPageSize, result.getPages(), result.hasNext());
        return new ArticleListByUserIdPageResponse(pageResult, new UserBriefVO(user), isOwner);
    }

    private Page<Article> buildUserArticlePage(Long userId, int pageNum, int pageSize) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        return PageUtils.getPage(pageNum, pageSize);
    }

    /**
     * 用户帖子列表过滤器
     *  - 本人查自己 (isOwner=true): 返回未删除 / 未禁用的全部帖子, 含草稿(创作中心要看到草稿)
     *  - 他人查作者 (isOwner=false): 仅返回已发布
     * 排序: 按 update_time DESC, 新发/新改的帖子排在前面
     */
    private LambdaQueryWrapper<Article> buildUserArticleWrapper(Long userId, boolean isOwner) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Article::getUserId, userId).ne(Article::getDeleteState, DELETE_TRUE).ne(Article::getState, STATE_FORBIDDEN);
        if (!isOwner) {
            wrapper.eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode());
        }
        wrapper.orderByDesc(Article::getUpdateTime);
        return wrapper;
    }

    // ============================================================
    // 回收站：用户查看自己已删除的帖子
    // ============================================================
    @Override
    public PageResult<Article> queryDeletedArticleListWithPage(Long loginUserId, Integer pageNum, Integer pageSize) {
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
        return new PageResult<>(result.getRecords(), result.getTotal(), validPageNum, validPageSize, result.getPages(), result.hasNext());
    }

    // ============================================================
    // 热帖榜
    // score = like_count * W_LIKE + visit_count * W_VISIT + favorite_count * W_FAV
    //       + (reply_count + sub_reply_count) * W_REPLY
    // ZSet 按 score 倒序; getHotArticleList(N) 取 Top N 帖子ID
    // ============================================================
    @Override
    public List<Long> getHotArticleList(Integer topN) {
        int n = (topN == null || topN < 1) ? 10 : topN;
        int overFetch = Math.min(Math.max(n * 4, n + 24), 200);
        Set<String> set = stringRedisTemplate.opsForZSet().reverseRange(Constant.REDIS_KEY_HOT_ARTICLES, 0, overFetch - 1);
        if (set != null && !set.isEmpty()) {
            return filterPublishedHotIdsOrderPreserving(set, n);
        }
        // 查询之前要进行热帖榜单的重算
        rebuildHotArticleRanking();
        Set<String> reload = stringRedisTemplate.opsForZSet().reverseRange(Constant.REDIS_KEY_HOT_ARTICLES, 0, overFetch - 1);
        if (reload == null || reload.isEmpty()) {
            return Collections.emptyList();
        }
        return filterPublishedHotIdsOrderPreserving(reload, n);
    }

    /**
     * ZSet 可能含脏成员（历史误 increment 等）；按 Redis 分数顺序只保留当前仍「已发布且未删未封禁」的 id。
     */
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
        List<Article> articles = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode()));
        // 先删旧 ZSet, 再批量写入新分数; 即便中间失败, 启动期 cold-read 也会触发重算
        stringRedisTemplate.delete(Constant.REDIS_KEY_HOT_ARTICLES);
        if (articles.isEmpty()) {
            log.info("热帖榜重算: 无可用帖子");
            return;
        }
        for (Article a : articles) {
            double score = computeHotScore(a);
            stringRedisTemplate.opsForZSet().add(Constant.REDIS_KEY_HOT_ARTICLES,
                    String.valueOf(a.getId()), score);
        }
        log.info("热帖榜重算完成: 共写入 {} 篇", articles.size());
    }

    /** 综合分公式; 任何字段为 null 都按 0 兜底, 避免拉胯到 NaN. */
    private double computeHotScore(Article a) {
        int like = a.getLikeCount() == null ? 0 : a.getLikeCount();
        int visit = a.getVisitCount() == null ? 0 : a.getVisitCount();
        int favorite = a.getFavoriteCount() == null ? 0 : a.getFavoriteCount();
        int reply = a.getReplyCount() == null ? 0 : a.getReplyCount();
        int sub = a.getSubReplyCount() == null ? 0 : a.getSubReplyCount();
        return like     * Constant.HOT_SCORE_WEIGHT_LIKE
             + visit    * Constant.HOT_SCORE_WEIGHT_VISIT
             + favorite * Constant.HOT_SCORE_WEIGHT_FAVORITE
             + (reply + sub) * Constant.HOT_SCORE_WEIGHT_REPLY;
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
        // 清洗 HTML 标签后判断纯文字长度，避免对几乎空的内容浪费 AI 调用
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
    public Map<String, Object> validateContentResult(String content) {
        String violation = validateContent(content);
        Map<String, Object> result = new HashMap<>();
        result.put("isAllowed", violation == null);
        if (violation != null) {
            result.put("reason", violation);
        }
        return result;
    }

    // ============================================================
    // 封面 URL 落库（FileController 上传后调用）
    // ============================================================
    /**
     * 设置帖子封面 URL.
     *  - PENDING_AUDIT 期间禁止
     *  - PUBLISHED 状态修改封面后状态退回 DRAFT 并下榜/减计数, 强制走回审 (与 updateArticle 一致)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateArticleCoverByUrl(Long articleId, String coverUrl, Long loginUserId) {
        Article article = selectArticleByArticleId(articleId);
        if (!article.getUserId().equals(loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
        if (ArticleStatus.isEditingLocked(article.getStatus())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AUDIT_EDIT_LOCKED));
        }
        boolean wasPublished = ArticleStatus.isPublished(article.getStatus());
        LambdaUpdateWrapper<Article> uw = new LambdaUpdateWrapper<>();
        uw.eq(Article::getId, articleId).set(Article::getCoverImg, coverUrl);
        if (wasPublished) {
            // 已发布帖子改封面 -> 状态退回 DRAFT, 等待用户重新 submitForAudit
            uw.set(Article::getStatus, ArticleStatus.DRAFT.getCode());
        }
        if (articleMapper.update(null, uw) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
        if (wasPublished) {
            stringRedisTemplate.opsForZSet().remove(Constant.REDIS_KEY_HOT_ARTICLES, String.valueOf(articleId));
            boardService.deleteOneById(article.getBoardId());
            userService.deleteOneById(loginUserId);
            stringRedisTemplate.delete(Constant.REDIS_KEY_ARTICLE_SUMMARY + articleId);
        }
    }

    // ============================================================
    // 帖子相册 (article_image)
    // ============================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceArticleImages(Long articleId, Long loginUserId, List<String> imageUrls) {
        if (articleId == null || articleId <= 0 || loginUserId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        // 串行化并发写: SELECT ... FOR UPDATE 锁住 article 主键行, 避免两次并发 replace 合并两套图
        Article article = articleMapper.selectByIdForUpdate(articleId);
        if (article == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_ARTICLE));
        }
        if (!article.getUserId().equals(loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
        if (ArticleStatus.isEditingLocked(article.getStatus())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AUDIT_EDIT_LOCKED));
        }
        // 视频帖清空相册：setArticleVideo 已处理；空列表 replace 仅用于相册帖
        if (article.getMediaType() != null && article.getMediaType() == 1) {
            if (imageUrls == null || imageUrls.isEmpty()) {
                return;
            }
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "视频帖不支持相册图"));
        }
        boolean wasPublished = ArticleStatus.isPublished(article.getStatus());
        // 入参规整: null 视作空数组, 保留入参顺序但允许重复
        List<String> urls = imageUrls == null ? Collections.emptyList() : new ArrayList<>(imageUrls);
        if (urls.size() > Constant.ARTICLE_GALLERY_MAX) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_ARTICLE_GALLERY_LIMIT));
        }
        // 业务规则: 有图必须配 ≥ 10 字"纯文本"正文; 去掉 HTML 标签后再算长度, 避免 <p>1</p> 这种水帖混过去
        if (!urls.isEmpty()) {
            String raw = article.getContent() == null ? "" : article.getContent();
            String plain = raw.replaceAll("<[^>]+>", "").trim();
            if (plain.length() < Constant.ARTICLE_GALLERY_MIN_CONTENT_LEN) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_ARTICLE_GALLERY_NEEDS_CONTENT));
            }
        }
        for (String url : urls) {
            validateArticleImageUrl(url);
        }
        // 切换为图片帖：清空视频字段（幂等）
        articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .ne(Article::getDeleteState, DELETE_TRUE)
                .set(Article::getMediaType, (byte) 0)
                .set(Article::getVideoUrl, null));
        // 软删旧记录, 再插入新行; 写在同一事务里, 任一步失败回滚
        articleImageMapper.update(null, new LambdaUpdateWrapper<ArticleImage>()
                .eq(ArticleImage::getArticleId, articleId)
                .ne(ArticleImage::getDeleteState, 1)
                .set(ArticleImage::getDeleteState, DELETE_TRUE));
        int sort = 0;
        for (String url : urls) {
            ArticleImage row = new ArticleImage();
            row.setArticleId(articleId);
            row.setImageUrl(url.trim());
            row.setSort(sort++);
            articleImageMapper.insert(row);
        }
        // 已发布帖子换了相册 -> 状态退回 DRAFT 并下榜减计数, 与 updateArticle/updateArticleCoverByUrl 行为一致;
        // 用户随后必须再次 submitForAudit 才能上线 (语义缓存命中可秒过)
        if (wasPublished) {
            int reset = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                    .eq(Article::getId, articleId)
                    .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                    .ne(Article::getDeleteState, DELETE_TRUE)
                    .set(Article::getStatus, ArticleStatus.DRAFT.getCode()));
            if (reset > 0) {
                stringRedisTemplate.opsForZSet().remove(Constant.REDIS_KEY_HOT_ARTICLES, String.valueOf(articleId));
                boardService.deleteOneById(article.getBoardId());
                userService.deleteOneById(loginUserId);
                stringRedisTemplate.delete(Constant.REDIS_KEY_ARTICLE_SUMMARY + articleId);
            }
        }
        log.info("帖子相册替换完成: articleId={}, count={}, userId={}", articleId, urls.size(), loginUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setArticleVideo(Long articleId, Long loginUserId, String videoUrl) {
        if (articleId == null || articleId <= 0 || loginUserId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (videoUrl == null || videoUrl.isBlank()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "视频地址为空"));
        }
        Article article = articleMapper.selectByIdForUpdate(articleId);
        if (article == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_ARTICLE));
        }
        if (!article.getUserId().equals(loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
        if (ArticleStatus.isEditingLocked(article.getStatus())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AUDIT_EDIT_LOCKED));
        }
        boolean wasPublished = ArticleStatus.isPublished(article.getStatus());
        // 清空相册图（软删）
        articleImageMapper.update(null, new LambdaUpdateWrapper<ArticleImage>()
                .eq(ArticleImage::getArticleId, articleId)
                .ne(ArticleImage::getDeleteState, 1)
                .set(ArticleImage::getDeleteState, DELETE_TRUE));
        // 设置为视频帖
        LambdaUpdateWrapper<Article> uw = new LambdaUpdateWrapper<>();
        uw.eq(Article::getId, articleId)
                .ne(Article::getDeleteState, DELETE_TRUE)
                .set(Article::getMediaType, (byte) 1)
                .set(Article::getVideoUrl, videoUrl.trim());
        if (wasPublished) {
            uw.set(Article::getStatus, ArticleStatus.DRAFT.getCode());
        }
        if (articleMapper.update(null, uw) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
        if (wasPublished) {
            stringRedisTemplate.opsForZSet().remove(Constant.REDIS_KEY_HOT_ARTICLES, String.valueOf(articleId));
            boardService.deleteOneById(article.getBoardId());
            userService.deleteOneById(loginUserId);
            stringRedisTemplate.delete(Constant.REDIS_KEY_ARTICLE_SUMMARY + articleId);
        }
        log.info("帖子视频已绑定: articleId={}, userId={}", articleId, loginUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearArticleVideo(Long articleId, Long loginUserId) {
        if (articleId == null || articleId <= 0 || loginUserId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        Article article = articleMapper.selectByIdForUpdate(articleId);
        if (article == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_ARTICLE));
        }
        if (!article.getUserId().equals(loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
        if (ArticleStatus.isEditingLocked(article.getStatus())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AUDIT_EDIT_LOCKED));
        }
        boolean wasPublished = ArticleStatus.isPublished(article.getStatus());
        LambdaUpdateWrapper<Article> uw = new LambdaUpdateWrapper<>();
        uw.eq(Article::getId, articleId)
                .ne(Article::getDeleteState, DELETE_TRUE)
                .set(Article::getMediaType, (byte) 0)
                .set(Article::getVideoUrl, null);
        if (wasPublished) {
            uw.set(Article::getStatus, ArticleStatus.DRAFT.getCode());
        }
        if (articleMapper.update(null, uw) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
        if (wasPublished) {
            stringRedisTemplate.opsForZSet().remove(Constant.REDIS_KEY_HOT_ARTICLES, String.valueOf(articleId));
            boardService.deleteOneById(article.getBoardId());
            userService.deleteOneById(loginUserId);
            stringRedisTemplate.delete(Constant.REDIS_KEY_ARTICLE_SUMMARY + articleId);
        }
    }

    @Override
    public List<String> queryArticleImageUrls(Long articleId) {
        if (articleId == null || articleId <= 0) {
            return Collections.emptyList();
        }
        List<ArticleImage> rows = articleImageMapper.selectList(new LambdaQueryWrapper<ArticleImage>()
                .eq(ArticleImage::getArticleId, articleId)
                .ne(ArticleImage::getDeleteState, 1)
                .orderByAsc(ArticleImage::getSort)
                .orderByAsc(ArticleImage::getId));
        if (rows.isEmpty()) return Collections.emptyList();
        List<String> urls = new ArrayList<>(rows.size());
        for (ArticleImage r : rows){
            urls.add(r.getImageUrl());
        }
        return urls;
    }

    /**
     * 校验相册图 URL 必须落在 OSS_PATH_ARTICLE_IMAGE 子目录, 且不含可能绕过前缀语义的路径段.
     * 与商城 / 聊天图的校验策略一致.
     */
    private void validateArticleImageUrl(String url) {
        if (!ossConfig.matchesPublicObjectUrl(url, Constant.OSS_PATH_ARTICLE_IMAGE)) {
            log.warn("帖子相册 URL 非法: {}", url);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_INVALID_OSS_URL));
        }
    }

    private boolean containsControlChar(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c == 0x7F) return true;
        }
        return false;
    }

    // ============================================================
    // 异步审核: submitForAudit / applyAuditResult / getAuditStatus / sweep
    // ============================================================

    /**
     * 提交审核. 详细流程:
     *  1) 校验帖子存在 + 作者本人
     *  2) 校验当前状态在 DRAFT / REJECTED / AUDIT_ERROR / PUBLISHED 之内
     *  3) 校验 retry_count < 上限 (3)
     *  4) 用 CAS UPDATE 把状态从旧状态扭转到 PENDING_AUDIT,
     *     同时 retry_count + 1, 写入新 task_id / submittedAt / notifyEmail / 清空之前的 finishedAt
     *  5) 投递 MQ; 投递失败要立刻回滚 (事务管理)
     *  6) 收集图片 URL (cover + gallery), 一并送 Python 审核
     *
     * 注意: "已发布回审" 时会先把状态退回, 因此热帖榜应当在 updateArticle 时就移除,
     *      此处不再重复处理.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String submitForAudit(Long articleId, Long loginUserId, Boolean notifyEmail) {
        if (articleId == null || loginUserId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        User author = userService.queryUserByUserId(loginUserId);
        UserMuteGuard.assertCanPost(author);
        Article article = articleMapper.selectByIdForUpdate(articleId);
        if (article == null || (article.getState() != null && article.getState() == STATE_FORBIDDEN)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        if (!Objects.equals(article.getUserId(), loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AUDIT_NOT_AUTHOR));
        }
        if (!ArticleStatus.canSubmitForAudit(article.getStatus())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AUDIT_STATUS_INVALID));
        }
        int curRetry = article.getAuditRetryCount() == null ? 0 : article.getAuditRetryCount();
        if (curRetry >= Constant.ARTICLE_AUDIT_MAX_RETRY) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AUDIT_RETRY_LIMIT));
        }
        String taskId = UUID.randomUUID().toString();
        Byte oldStatus = article.getStatus();
        boolean wasPublished = ArticleStatus.isPublished(oldStatus);
        int updated = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .eq(Article::getStatus, oldStatus)
                .ne(Article::getDeleteState, DELETE_TRUE)
                .set(Article::getStatus, ArticleStatus.PENDING_AUDIT.getCode())
                .set(Article::getAuditTaskId, taskId)
                .set(Article::getAuditNotifyEmail, Boolean.TRUE.equals(notifyEmail) ? AUDIT_NOTIFY_EMAIL_ON : AUDIT_NOTIFY_EMAIL_OFF)
                .set(Article::getAuditRetryCount, curRetry + 1)
                .set(Article::getAuditSubmittedAt, new Date())
                .set(Article::getAuditFinishedAt, null)
                .set(Article::getAuditResultMessage, null));
        if (updated <= 0) {
            // 并发下另一个请求已经把状态改了, 拒绝本次
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AUDIT_STATUS_INVALID));
        }
        // 收集图片: cover + gallery; cover 单独传, gallery 列表传 vl 模型逐张审
        List<String> imageUrls = queryArticleImageUrls(articleId);
        String videoUrl = null;
        if (article.getMediaType() != null && article.getMediaType() == 1
                && StringUtils.hasText(article.getVideoUrl())) {
            videoUrl = article.getVideoUrl().trim();
        }
        ArticleAuditTaskMqVO task = new ArticleAuditTaskMqVO(
                taskId,
                articleId,
                loginUserId,
                article.getTitle(),
                article.getContent(),
                StringUtils.hasText(article.getCoverImg()) ? article.getCoverImg() : null,
                imageUrls,
                videoUrl,
                System.currentTimeMillis()
        );
        // 先发 MQ: 失败抛异常会回滚 DB CAS, 同时本方法尚未做"下榜+减计数"副作用, 数据保持一致.
        // 副作用必须放在 MQ 之后, 否则 MQ 失败回滚 DB 时, Redis/计数已经被错误地减掉.
        forumProducer.sendArticleAuditTask(task);
        // 已发布回审: 在 MQ 已确认投递成功后再把帖子从热帖榜 / 用户帖数 / 板块帖数中下线,
        // 避免审核未通过 / 挂掉时它继续"在线".
        if (wasPublished) {
            stringRedisTemplate.opsForZSet().remove(Constant.REDIS_KEY_HOT_ARTICLES, String.valueOf(articleId));
            boardService.deleteOneById(article.getBoardId());
            userService.deleteOneById(loginUserId);
        }
        log.info("提交审核成功: articleId={}, userId={}, taskId={}, retry={}/{}",
                articleId, loginUserId, taskId, curRetry + 1, Constant.ARTICLE_AUDIT_MAX_RETRY);
        return taskId;
    }

    /**
     * 应用审核结果. 调用方仅限 ForumConsumer (q-audit-result 消费) 与超时兜底任务.
     *
     * 幂等设计 (审过 Reviewer 建议后改造):
     *  - 主防线: DB CAS WHERE status=PENDING_AUDIT AND audit_task_id=入参 taskId.
     *    只要 status 已从 PENDING_AUDIT 翻面, 任何后续投递都不会再生效. DB 是 source of truth.
     *  - 辅助加速: Redis dedup key 只在 DB CAS 成功翻面后才写入, 用于"不必要的进入查询",
     *    避免 publisher 抖动重发时空跑一遍 SELECT/SWITCH. 失败也无所谓, DB 兜底.
     *  - finishedAt 写入: 进一步辅助观测.
     *
     * 注意: SETNX 不再放在最前面, 防止"SETNX 已写但 DB 还没改 -> 重投永远被挡, 卡 PENDING".
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyAuditResult(ArticleAuditResultMqVO result) {
        if (result == null || result.getTaskId() == null || result.getArticleId() == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        String taskId = result.getTaskId();
        Long articleId = result.getArticleId();

        // 加速短路: 之前已完整处理过 -> 直接跳过, 不查 DB
        String dedupKey = Constant.REDIS_KEY_AUDIT_RESULT_DEDUP + taskId;
        try {
            String marker = stringRedisTemplate.opsForValue().get(dedupKey);
            if ("done".equals(marker)) {
                log.info("审核结果重复回调(命中 Redis 标记), 已忽略: taskId={}", taskId);
                return;
            }
        } catch (Exception ignored) {
            // Redis 不可用时, 仍走 DB CAS 兜底, 不影响主流程
        }

        Article article = articleMapper.selectOne(new LambdaQueryWrapper<Article>()
                .eq(Article::getId, articleId)
                .ne(Article::getDeleteState, DELETE_TRUE));
        if (article == null) {
            log.warn("审核结果对应帖子不存在: articleId={}, taskId={}", articleId, taskId);
            return;
        }
        // 主防线: DB CAS - 仅当 PENDING_AUDIT 且 task_id 一致时才能继续
        if (article.getStatus() == null || article.getStatus() != ArticleStatus.PENDING_AUDIT.getCode()
                || !Objects.equals(article.getAuditTaskId(), taskId)) {
            log.info("审核结果对应任务已失效, 忽略: articleId={}, taskId={}, currentStatus={}",
                    articleId, taskId, article.getStatus());
            return;
        }

        String finalStatus = result.getFinalStatus() == null ? "AUDIT_ERROR" : result.getFinalStatus().toUpperCase();
        Date now = new Date();
        boolean applied;
        switch (finalStatus) {
            case "APPROVED":
                applied = applyAuditApproved(article, result, now);
                break;
            case "REJECTED":
                applied = applyAuditRejected(article, result, now);
                break;
            default:
                applied = applyAuditError(article, result, now);
                break;
        }
        // 只有 DB 真正翻面后才写 dedup, 失败不影响正确性 (下次进来 DB CAS 仍然挡掉)
        if (applied) {
            try {
                stringRedisTemplate.opsForValue().set(dedupKey, "done",
                        Constant.REDIS_TTL_AUDIT_RESULT_DEDUP, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    /** APPROVED -> auto publish: 直接扭转到 PUBLISHED + 入榜 + 系统消息 + (可选)邮件 + 写摘要缓存; 返回是否真正扭转 */
    private boolean applyAuditApproved(Article article, ArticleAuditResultMqVO result, Date now) {
        Long articleId = article.getId();
        Long userId = article.getUserId();
        int updated = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .eq(Article::getStatus, ArticleStatus.PENDING_AUDIT.getCode())
                .eq(Article::getAuditTaskId, result.getTaskId())
                .set(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .set(Article::getAuditFinishedAt, now)
                .set(Article::getAuditResultMessage,
                        StringUtils.hasText(result.getFinalReason()) ? result.getFinalReason() : "审核通过"));
        if (updated <= 0) {
            log.warn("APPROVED 扭转失败 (并发): articleId={}, taskId={}", articleId, result.getTaskId());
            return false;
        }
        userService.addOneById(userId);
        boardService.addOneById(article.getBoardId());
        // 取最新统计后入榜, 避免使用过期 article 对象算分
        Article latest = articleMapper.selectById(articleId);
        if (latest != null) {
            stringRedisTemplate.opsForZSet().add(Constant.REDIS_KEY_HOT_ARTICLES,
                    String.valueOf(articleId), computeHotScore(latest));
        }
        // Python 顺手给了摘要, 直接写缓存, 用户首次进详情就有
        if (StringUtils.hasText(result.getSummary())) {
            stringRedisTemplate.opsForValue().set(
                    Constant.REDIS_KEY_ARTICLE_SUMMARY + articleId,
                    result.getSummary(),
                    Constant.REDIS_TTL_ARTICLE_SUMMARY,
                    TimeUnit.SECONDS);
        }
        Article published = latest != null ? latest : articleMapper.selectById(articleId);
        if (published != null) {
            User author = userService.getUserInfoById(userId);
            Map<String, Object> ragPayload = new HashMap<>();
            ragPayload.put("articleId", articleId);
            ragPayload.put("title", published.getTitle());
            ragPayload.put("content", published.getContent());
            ragPayload.put("mediaType", published.getMediaType() != null ? published.getMediaType().intValue() : 0);
            ragPayload.put("videoUrl", published.getVideoUrl());
            ragPayload.put("coverUrl", published.getCoverImg());
            ragPayload.put("summary", result.getSummary());
            ragPayload.put("authorNickname", author != null ? author.getNickname() : "");
            ragPayload.put("tagNames", articleTagService.tagNamesByArticleId(articleId));
            aiHubService.indexArticleRag(ragPayload);
        }
        notifyAuditResult(article, result,
                Constant.SYSTEM_MSG_TYPE_AUDIT_PASS,
                Constant.SYSTEM_MSG_TITLE_AUDIT_PASS,
                String.format("你的帖子《%s》已通过审核, 自动发布到论坛.", safeTitle(article.getTitle())));
        log.info("APPROVED 处理完成: articleId={}, taskId={}", articleId, result.getTaskId());
        return true;
    }

    /** REJECTED -> 退回到 REJECTED 状态 + 系统消息(附拒绝理由); 返回是否真正扭转 */
    private boolean applyAuditRejected(Article article, ArticleAuditResultMqVO result, Date now) {
        Long articleId = article.getId();
        String reason = StringUtils.hasText(result.getFinalReason()) ? result.getFinalReason() : "内容违规";
        int updated = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .eq(Article::getStatus, ArticleStatus.PENDING_AUDIT.getCode())
                .eq(Article::getAuditTaskId, result.getTaskId())
                .set(Article::getStatus, ArticleStatus.REJECTED.getCode())
                .set(Article::getAuditFinishedAt, now)
                .set(Article::getAuditResultMessage, truncate(reason, 500)));
        if (updated <= 0) {
            log.warn("REJECTED 扭转失败 (并发): articleId={}, taskId={}", articleId, result.getTaskId());
            return false;
        }
        notifyAuditResult(article, result,
                Constant.SYSTEM_MSG_TYPE_AUDIT_FAIL,
                Constant.SYSTEM_MSG_TITLE_AUDIT_FAIL,
                String.format("你的帖子《%s》未通过审核, 原因: %s. 请修改后重新提交.",
                        safeTitle(article.getTitle()), reason));
        log.info("REJECTED 处理完成: articleId={}, taskId={}, reason={}", articleId, result.getTaskId(), reason);
        return true;
    }

    /** AUDIT_ERROR -> 状态 AUDIT_ERROR + 系统消息(提示重试); 返回是否真正扭转 */
    private boolean applyAuditError(Article article, ArticleAuditResultMqVO result, Date now) {
        Long articleId = article.getId();
        String reason = StringUtils.hasText(result.getFinalReason()) ? result.getFinalReason() : "审核服务异常";
        int updated = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .eq(Article::getStatus, ArticleStatus.PENDING_AUDIT.getCode())
                .eq(Article::getAuditTaskId, result.getTaskId())
                .set(Article::getStatus, ArticleStatus.AUDIT_ERROR.getCode())
                .set(Article::getAuditFinishedAt, now)
                .set(Article::getAuditResultMessage, truncate(reason, 500)));
        if (updated <= 0) {
            log.warn("AUDIT_ERROR 扭转失败 (并发): articleId={}, taskId={}", articleId, result.getTaskId());
            return false;
        }
        notifyAuditResult(article, result,
                Constant.SYSTEM_MSG_TYPE_AUDIT_ERROR,
                Constant.SYSTEM_MSG_TITLE_AUDIT_ERROR,
                String.format("你的帖子《%s》审核异常: %s. 请稍后重新提交.",
                        safeTitle(article.getTitle()), reason));
        log.warn("AUDIT_ERROR 处理完成: articleId={}, taskId={}", articleId, result.getTaskId());
        return true;
    }

    /** 站内信 + 可选邮件 + WebSocket 实时通知; 任何异常不阻塞审核结果落库. */
    private void notifyAuditResult(Article article, ArticleAuditResultMqVO result,
                                   Byte sysMsgType, String title, String content) {
        Long userId = article.getUserId();
        Long articleId = article.getId();
        Long systemMsgId = null;
        // 系统消息: 站内信红点必发
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("articleId", articleId);
            payload.put("taskId", result.getTaskId());
            payload.put("finalStatus", result.getFinalStatus());
            String payloadJson;
            try {
                payloadJson = objectMapper.writeValueAsString(payload);
            } catch (Exception e) {
                payloadJson = null;
            }
            systemMsgId = systemMessageService.createMessage(userId, sysMsgType, title, truncate(content, 500),
                    articleId, payloadJson);
        } catch (Exception e) {
            log.error("写入系统消息失败: userId={}, articleId={}", userId, articleId, e);
        }
        pushAuditRealtimeNotify(userId, articleId, result, title, content, systemMsgId);
        // 邮件: 仅当用户提交审核时勾选了 notifyEmail
        if (article.getAuditNotifyEmail() != null && article.getAuditNotifyEmail() == AUDIT_NOTIFY_EMAIL_ON) {
            sendAuditEmail(userId, title, content);
        }
    }

    /** 审核结果 WebSocket：驱动审核页即时跳转 + 站内信红点刷新 */
    private void pushAuditRealtimeNotify(Long userId, Long articleId, ArticleAuditResultMqVO result,
                                         String title, String content, Long systemMsgId) {
        if (userId == null || result == null) {
            return;
        }
        try {
            int statusAfter = resolveStatusAfterAudit(result.getFinalStatus());
            Map<String, Object> auditWs = new LinkedHashMap<>();
            auditWs.put("type", "audit_result");
            auditWs.put("articleId", articleId);
            auditWs.put("taskId", result.getTaskId());
            auditWs.put("finalStatus", result.getFinalStatus());
            auditWs.put("status", statusAfter);
            auditWs.put("resultMessage", truncate(content, 500));
            auditWs.put("title", title);
            webSocketPushService.push(userId, objectMapper.writeValueAsString(auditWs));
            Map<String, Object> sysWs = new LinkedHashMap<>();
            sysWs.put("type", "system_message");
            sysWs.put("messageId", systemMsgId);
            sysWs.put("articleId", articleId);
            sysWs.put("title", title);
            sysWs.put("content", truncate(content, 500));
            sysWs.put("finalStatus", result.getFinalStatus());
            webSocketPushService.push(userId, objectMapper.writeValueAsString(sysWs));
        } catch (Exception e) {
            log.warn("审核结果 WebSocket 推送失败 userId={}, articleId={}", userId, articleId, e);
        }
    }

    private int resolveStatusAfterAudit(String finalStatus) {
        if (finalStatus == null) {
            return ArticleStatus.AUDIT_ERROR.getCode();
        }
        return switch (finalStatus.toUpperCase()) {
            case "APPROVED" -> ArticleStatus.PUBLISHED.getCode();
            case "REJECTED" -> ArticleStatus.REJECTED.getCode();
            default -> ArticleStatus.AUDIT_ERROR.getCode();
        };
    }

    /** 解密用户邮箱 -> JavaMailSender 投递; 失败仅日志, 不影响主流程 */
    private void sendAuditEmail(Long userId, String title, String content) {
        try {
            User user = userService.queryUserByUserId(userId);
            if (user == null || !StringUtils.hasText(user.getEmail())) {
                log.info("跳过邮件通知: 用户未绑定邮箱 userId={}", userId);
                return;
            }
            String email = PiiUtils.decrypt(user.getEmail());
            if (!StringUtils.hasText(email)) {
                log.info("跳过邮件通知: 邮箱解密为空 userId={}", userId);
                return;
            }
            mailUtil.sendSampleMail(email, title, content);
            log.info("审核邮件通知已发送: userId={}", userId);
        } catch (Exception e) {
            log.error("发送审核邮件失败: userId={}", userId, e);
        }
    }

    private static String safeTitle(String title) {
        if (title == null) return "(无标题)";
        return title.length() > 30 ? title.substring(0, 30) + "..." : title;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    @Override
    public AuditStatusResponse getAuditStatus(Long articleId, Long loginUserId) {
        if (articleId == null || loginUserId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        Article article = selectArticleByArticleId(articleId);
        if (!Objects.equals(article.getUserId(), loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AUDIT_NOT_AUTHOR));
        }
        AuditStatusResponse resp = new AuditStatusResponse();
        resp.setArticleId(articleId);
        resp.setStatus(article.getStatus());
        resp.setStatusText(statusText(article.getStatus()));
        resp.setTaskId(article.getAuditTaskId());
        resp.setResultMessage(article.getAuditResultMessage());
        int retry = article.getAuditRetryCount() == null ? 0 : article.getAuditRetryCount();
        resp.setRetryCount(retry);
        resp.setRetryLimit(Constant.ARTICLE_AUDIT_MAX_RETRY);
        resp.setRetryLimitReached(retry >= Constant.ARTICLE_AUDIT_MAX_RETRY);
        resp.setSubmittedAt(article.getAuditSubmittedAt());
        resp.setFinishedAt(article.getAuditFinishedAt());
        return resp;
    }

    private static String statusText(Byte status) {
        if (status == null) return "未知";
        if (status == ArticleStatus.DRAFT.getCode())          return ArticleStatus.DRAFT.getMessage();
        if (status == ArticleStatus.PENDING_AUDIT.getCode())  return ArticleStatus.PENDING_AUDIT.getMessage();
        if (status == ArticleStatus.APPROVED.getCode())       return ArticleStatus.APPROVED.getMessage();
        if (status == ArticleStatus.REJECTED.getCode())       return ArticleStatus.REJECTED.getMessage();
        if (status == ArticleStatus.AUDIT_ERROR.getCode())    return ArticleStatus.AUDIT_ERROR.getMessage();
        if (status == ArticleStatus.PUBLISHED.getCode())      return ArticleStatus.PUBLISHED.getMessage();
        return "未知";
    }

    /**
     * 兜底: 把 status=PENDING_AUDIT 且 submitted_at + TIMEOUT < now 的帖子统一转为 AUDIT_ERROR.
     * 由 ArticleAuditTimeoutTask 定时调用 (每 5min 一次).
     * 用户后续可继续 submitForAudit 重试 (受 retry 上限约束).
     */
    @Override
    public int sweepStuckAuditTasks() {
        long timeoutMs = Constant.ARTICLE_AUDIT_TIMEOUT_SECONDS * 1000;
        Date cutoff = new Date(System.currentTimeMillis() - timeoutMs);
        List<Article> stuck = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, ArticleStatus.PENDING_AUDIT.getCode())
                .ne(Article::getDeleteState, DELETE_TRUE)
                .lt(Article::getAuditSubmittedAt, cutoff));
        if (stuck.isEmpty()) return 0;
        int handled = 0;
        for (Article a : stuck) {
            try {
                ArticleAuditResultMqVO mock = new ArticleAuditResultMqVO();
                mock.setTaskId(a.getAuditTaskId());
                mock.setArticleId(a.getId());
                mock.setUserId(a.getUserId());
                mock.setTitle(a.getTitle());
                mock.setFinalStatus("AUDIT_ERROR");
                mock.setFinalReason("审核服务长时间未响应, 已自动归类为异常, 请重新提交");
                mock.setFinishedAt(System.currentTimeMillis());
                // 通过 self 调用让 @Transactional 生效 (this.xxx 是自调用, 不会走 Spring 代理)
                self.applyAuditResult(mock);
                handled++;
            } catch (Exception e) {
                log.error("审核任务兜底处理失败: articleId={}", a.getId(), e);
            }
        }
        if (handled > 0) {
            log.warn("兜底任务处理完毕: {} 条 PENDING 审核被强制转为 AUDIT_ERROR", handled);
        }
        return handled;
    }
}
