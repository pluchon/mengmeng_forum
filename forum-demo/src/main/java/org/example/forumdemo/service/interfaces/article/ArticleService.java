package org.example.forumdemo.service.interfaces.article;

import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.entity.dto.article.PublishArticleRequest;
import org.example.forumdemo.entity.dto.article.UpdateArticleRequest;
import org.example.forumdemo.entity.vo.article.ArticleDetailResponse;
import org.example.forumdemo.entity.vo.article.ArticleListByUserIdPageResponse;
import org.example.forumdemo.entity.vo.article.AuditStatusResponse;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.mq.ArticleAuditResultMqVO;

import java.util.List;
import java.util.Map;

/**
 * 帖子核心业务
 * 不再持有 MultipartFile 参数：图片上传走 FileService，业务侧只接收 URL
 */
public interface ArticleService {

    // 创建草稿，返回新帖子的 ID
    Long createDraft(PublishArticleRequest publishArticleRequest, Long userId);

    // 发布已存在帖子；草稿变已发布，已发布帖子重新发布时只做权限与内容校验
    void publishArticle(Long articleId, Long userId);

    // 帖子详情，包含作者信息、板块信息、当前用户的点赞 / Owner 标志
    ArticleDetailResponse queryArticleDetailByArticleId(Long articleId, Long loginUserId);

    // 修改帖子内容（仅作者）
    void updateArticle(UpdateArticleRequest updateArticleRequest, Long loginUserId);

    // 删除帖子（事务，仅作者；同步维护用户、板块计数与热帖榜）
    void deleteArticle(Long articleId, Long loginUserId);

    // 用户主页帖子列表（分页）
    PageResult<Article> queryArticleListByUserIdWithPage(Long userId, Long loginUserId, Integer pageNum, Integer pageSize);

    // 用户主页帖子列表（分页，附带用户信息与 owner 标志）
    ArticleListByUserIdPageResponse queryArticleListByUserIdWithPageAndUserInfo(Long userId, Long loginUserId,
                                                                               Integer pageNum, Integer pageSize);

    // 回收站：根据 delete_state=1 查询当前登录用户已删除的帖子（分页，仅本人可看）
    PageResult<Article> queryDeletedArticleListWithPage(Long loginUserId, Integer pageNum, Integer pageSize);

    // 热帖榜单 TopN（Redis ZSet，冷启动时从 DB 回源）
    List<Long> getHotArticleList(Integer topN);

    // 帖子摘要（带 Redis 缓存）
    String getArticleSummary(Long articleId);

    // 内容安全审核：返回 null 表示通过；非 null 为违规原因
    String validateContent(String content);

    // 内容安全审核：返回 { isAllowed: true/false, reason: ... }
    Map<String, Object> validateContentResult(String content);

    // FileController 上传完成后，由业务侧把 URL 落库为帖子封面
    void updateArticleCoverByUrl(Long articleId, String coverUrl, Long loginUserId);

    /**
     * 全量替换帖子相册图. 校验:
     *   1) 帖子存在 + 登录用户 = 作者
     *   2) imageUrls 数量 ≤ 15
     *   3) 每个 URL 必须落在 forum_article_picture/ 子目录且不含路径穿越字符
     *   4) 当 imageUrls 非空时 article.content 长度 ≥ 10 字符
     * 事务内: 软删旧 article_image + 按入参顺序插入新行(sort 从 0 自增).
     */
    void replaceArticleImages(Long articleId, Long loginUserId, java.util.List<String> imageUrls);

    /** 按 sort 升序返回某帖子未删除的相册图URL列表; 无图返回空数组. */
    java.util.List<String> queryArticleImageUrls(Long articleId);

    /**
     * 全量重算热帖榜 ZSet (按 like / visit / favorite / reply+sub_reply 加权).
     * 由 HotArticleRankingTask 每天凌晨与启动后调用; 也允许人工兜底.
     */
    void rebuildHotArticleRanking();

    // ============ 异步审核 ============

    /**
     * 提交帖子进入异步审核流程.
     *  - 仅 DRAFT / REJECTED / AUDIT_ERROR / PUBLISHED 状态允许
     *  - 累计提交次数达到 ARTICLE_AUDIT_MAX_RETRY (3) 后拒绝
     *  - 成功后状态扭转为 PENDING_AUDIT 并投递 q-audit-article
     *
     * @param articleId   要审核的帖子 ID
     * @param loginUserId 当前登录用户 ID (必须为帖子作者)
     * @param notifyEmail 审核结果是否额外推邮件; 站内信无论如何都发
     * @return 本次审核任务 ID
     */
    String submitForAudit(Long articleId, Long loginUserId, Boolean notifyEmail);

    /**
     * 应用 Python 端回执的审核结果. 由 ForumConsumer 调用, 不允许外部 Controller 调.
     *  - 幂等: 同 taskId 重复调用直接返回
     *  - CAS: 仅当 article.status=PENDING 且 article.audit_task_id=入参 taskId 才扭转
     *  - 通过 -> PUBLISHED + 系统消息 + 可选邮件 + 入热帖榜 + 摘要写缓存
     *  - 不通过 -> REJECTED + 系统消息 (附拒绝理由)
     *  - 异常 -> AUDIT_ERROR + 系统消息 (提示重试)
     */
    void applyAuditResult(ArticleAuditResultMqVO result);

    /**
     * 查询某帖子审核状态; 用作前端"审核中"页面的轮询兜底.
     * 仅作者本人可查.
     */
    AuditStatusResponse getAuditStatus(Long articleId, Long loginUserId);

    /**
     * 兜底扫描: 把状态为 PENDING 且超时未收到结果的帖子转为 AUDIT_ERROR.
     * 由 ArticleAuditTimeoutTask 定时调度.
     */
    int sweepStuckAuditTasks();

    // ============ 内部共用 ============

    Article selectArticleByArticleId(Long articleId);

    // 帖子楼层数 +1 / -1（一级回复, 被 ArticleReplyService 调用）
    void addReply(Long articleId);

    void deleteReply(Long articleId);

    // 楼中楼计数 +1 / -1（独立于 reply_count, 由 ArticleSubReplyService 调用）
    void addSubReply(Long articleId);

    void deleteSubReply(Long articleId);
}
