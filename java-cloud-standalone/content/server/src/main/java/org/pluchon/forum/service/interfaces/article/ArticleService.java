package org.pluchon.forum.service.interfaces.article;

import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.dto.article.PublishArticleRequest;
import org.pluchon.forum.entity.dto.article.UpdateArticleRequest;
import org.pluchon.forum.entity.vo.article.ArticleBriefVO;
import org.pluchon.forum.entity.vo.article.ArticleDetailResponse;
import org.pluchon.forum.entity.vo.article.ArticleListByUserIdPageResponse;
import org.pluchon.forum.entity.vo.article.AuditStatusResponse;
import org.pluchon.forum.entity.vo.article.HotArticleListItemVO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.mq.ArticleAuditResultMqVO;

import java.util.List;

// 帖子核心业务 不再持有 MultipartFile 参数：图片上传走 FileService，业务侧只接收 URL
public interface ArticleService {

    // 创建草稿，返回新帖子的 ID
    Long createDraft(PublishArticleRequest publishArticleRequest, Long userId);

    // 帖子详情，包含作者信息、板块信息、当前用户的点赞 / Owner 标志
    // clientIp 只用于浏览量去重，取不到时按不去重处理
    ArticleDetailResponse queryArticleDetailByArticleId(Long articleId, Long loginUserId, String clientIp);

    // 修改帖子内容 仅作者
    void updateArticle(UpdateArticleRequest updateArticleRequest, Long loginUserId);

    // 删除帖子 事务，仅作者；同步维护用户、板块计数与热帖榜
    void deleteArticle(Long articleId, Long loginUserId);

    // 用户主页帖子列表 分页
    PageResult<ArticleBriefVO> queryArticleListByUserIdWithPage(Long userId, Long loginUserId, Integer pageNum,
                                                                 Integer pageSize, Integer status, String keyword);

    // 用户主页帖子列表 分页，附带用户信息与 owner 标志
    ArticleListByUserIdPageResponse queryArticleListByUserIdWithPageAndUserInfo(Long userId, Long loginUserId,
                                                                               Integer pageNum, Integer pageSize);

    // 热帖榜分页，后端最多提供前 50 条，每页最多 10 条
    PageResult<HotArticleListItemVO> queryHotArticleListWithPage(Integer pageNum, Integer pageSize, Long loginUserId);

    // 内容安全审核：返回 null 表示通过；非 null 为违规原因
    String validateContent(String content);

    // 更新帖子封面
    void updateArticleCoverByUrl(Long articleId, String coverUrl, Long loginUserId);

    // 全量替换帖子相册图
    void replaceArticleImages(Long articleId, Long loginUserId, List<String> imageUrls);

    // 设置帖子为视频帖并绑定视频链接
    void setArticleVideo(Long articleId, Long loginUserId, String videoUrl);

    // 清空视频并切回图文帖
    void clearArticleVideo(Long articleId, Long loginUserId);

    // 绑定帖子配乐
    void setArticleMusic(Long articleId, Long loginUserId,
                         String musicKey, String musicTitle,
                         String musicCoverUrl, String musicAudioUrl, String musicLrcUrl);

    // 清空帖子配乐
    void clearArticleMusic(Long articleId, Long loginUserId);

    // 按 sort 升序返回某帖子未删除的相册图URL列表; 无图返回空数组.
    List<String> queryArticleImageUrls(Long articleId);

    // 全量重算热帖榜 ZSet 按 like / visit / favorite / reply+sub_reply 加权 . 由 HotArticleRankingTask 每天凌晨与启动后调用; 也允许人工兜底.
    void rebuildHotArticleRanking();

    

    // 提交帖子进入异步审核流程. 仅 DRAFT / REJECTED / AUDIT_ERROR / PUBLISHED 状态允许 累计提交次数达到 ARTICLE_AUDIT_MAX_RETRY 3 后拒绝 成功后状态扭转为 PENDING_AUDIT 并投递 q audit article
    String submitForAudit(Long articleId, Long loginUserId);

    // 应用 Python 端回执的审核结果. 由 ForumConsumer 调用, 不允许外部 Controller 调. 幂等: 同 taskId 重复调用直接返回 CAS: 仅当 article.status PENDING 且 article.audit_task_id 入参 taskId 才扭转 通过 > PUBLISHED + 系统消息 + 入热帖榜 + 摘要写缓存 不通过 > REJECTED + 系统消息 附拒绝理由 异常 > AUDIT_ERROR + 系统消息 提示重试
    void applyAuditResult(ArticleAuditResultMqVO result);

    // 查询某帖子审核状态; 用作前端 审核中 页面的轮询兜底. 仅作者本人可查.
    AuditStatusResponse getAuditStatus(Long articleId, Long loginUserId);

    // 兜底扫描: 把状态为 PENDING 且超时未收到结果的帖子转为 AUDIT_ERROR. 由 ArticleAuditTimeoutTask 定时调度.
    int sweepStuckAuditTasks();

    

    Article selectArticleByArticleId(Long articleId);

    // 帖子楼层数 +1 / 1 一级回复, 被 ArticleReplyService 调用
    void addReply(Long articleId);

    void deleteReply(Long articleId);

    // 楼中楼计数 +1 / 1 独立于 reply_count, 由 ArticleSubReplyService 调用
    void addSubReply(Long articleId);

    void deleteSubReply(Long articleId);
}
