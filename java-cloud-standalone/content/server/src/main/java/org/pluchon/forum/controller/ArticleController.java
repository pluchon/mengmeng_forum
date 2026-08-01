package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.vo.article.ArticleBriefVO;
import org.pluchon.forum.entity.vo.article.ArticleValidateTextVO;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.entity.dto.article.PublishArticleRequest;
import org.pluchon.forum.entity.dto.article.ReplaceArticleImagesRequest;
import org.pluchon.forum.entity.dto.article.SubmitForAuditRequest;
import org.pluchon.forum.entity.dto.article.UpdateArticleRequest;
import org.pluchon.forum.entity.dto.article.ValidateTextRequest;
import org.pluchon.forum.entity.vo.article.ArticleDetailResponse;
import org.pluchon.forum.entity.vo.article.ArticleListByUserIdPageResponse;
import org.pluchon.forum.entity.vo.article.AuditStatusResponse;
import org.pluchon.forum.entity.vo.article.HotArticleListItemVO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.service.interfaces.article.ArticleGuideStreamService;
import org.pluchon.forum.service.interfaces.article.ArticleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.Executor;

@Tag(name = "帖子模块", description = "帖子的增删改查接口")
@RestController
@RequestMapping("/article")
public class ArticleController {
    @Autowired
    private ArticleService articleService;

    @Autowired
    private ArticleGuideStreamService articleGuideStreamService;

    @Autowired
    @Qualifier("sseExecutor")
    private Executor sseExecutor;

    @Operation(summary = "创建帖子草稿", description = "文章内容先入库为草稿，返回帖子ID；封面上传和发布动作走独立接口")
    @PostMapping("/createDraft")
    public Result<Long> createDraft(@Valid @RequestBody PublishArticleRequest publishArticleRequest, HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null){
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        Long id = articleService.createDraft(publishArticleRequest, loginUser.getId());
        return Result.success(id);
    }

    @Operation(summary = "发布帖子(仅审核通过状态)",
            description = "异步审核版本: 当前仅允许 APPROVED 状态 -> PUBLISHED. " +
                    "草稿 / 拒绝 / 异常状态请改用 /article/submitForAudit. " +
                    "通常用户无需手动调本接口, 因为审核通过会自动发布; 此接口给未来切手动模式预留.")
    @PutMapping("/publishArticle")
    public Result<String> publishArticle(@RequestParam Long articleId, HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null){
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        articleService.publishArticle(articleId, loginUser.getId());
        return Result.success("发布成功");
    }

    @Operation(summary = "提交帖子进入异步审核流程",
            description = "前置: 内容/封面/相册图均已落库. 后端会扭转状态到 PENDING_AUDIT, 投递 MQ 给 LangGraph. " +
                    "前端展示\"审核中\"页面，审核结果统一通过站内信推送.")
    @PostMapping("/submitForAudit")
    public Result<String> submitForAudit(@Valid @RequestBody SubmitForAuditRequest req,
                                         HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        String taskId = articleService.submitForAudit(req.getArticleId(), loginUser.getId());
        return Result.success(taskId);
    }

    @Operation(summary = "查询帖子审核状态(轮询兜底)",
            description = "前端\"审核中\"页面在 WebSocket 未到达的兜底场景下调用此接口轮询. " +
                    "返回当前状态码 + 文本 + 重试次数 + 最近一次审核结论文本.")
    @GetMapping("/getAuditStatus")
    public Result<AuditStatusResponse> getAuditStatus(@RequestParam Long articleId,
                                                      HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        return Result.success(articleService.getAuditStatus(articleId, loginUser.getId()));
    }

    @Operation(summary = "根据帖子Id展示帖子详细内容", description = "传入帖子ID")
    @GetMapping("/selectArticleDetailByArticleId")
    public Result<ArticleDetailResponse> selectArticleDetailByArticleId(Long articleId, HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        Long userId = (loginUser == null) ? -1L : loginUser.getId();
        return Result.success(articleService.queryArticleDetailByArticleId(articleId, userId));
    }

    @Operation(summary = "编辑帖子内容，只有作者本人才可以", description = "传入帖子ID，标题，以及正文")
    @PutMapping("/updateArticleByArticleId")
    public Result<String> updateArticleByArticleId(@RequestBody UpdateArticleRequest updateArticleRequest, HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null){
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        articleService.updateArticle(updateArticleRequest, loginUser.getId());
        return Result.success("更新帖子成功");
    }

    @Operation(summary = "删除对应帖子", description = "传入帖子ID，注意只有作者本人才可以")
    @DeleteMapping("/deleteArticle")
    public Result<String> deleteArticle(Long articleId, HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null){
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        articleService.deleteArticle(articleId, loginUser.getId());
        return Result.success("删除帖子成功");
    }

    // 根据用户ID查询该用户的帖子列表->带分页
    @Operation(summary = "查询用户帖子列表->分页", description = "传入用户ID和分页参数")
    @GetMapping("/getArticleListByUserIdWithPage")
    public Result<PageResult<ArticleBriefVO>> getArticleListByUserIdWithPage(Long userId, @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize, HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        Long loginUserId = (loginUser != null) ? loginUser.getId() : -1L;
        return Result.success(articleService.queryArticleListByUserIdWithPage(userId, loginUserId, pageNum, pageSize));
    }

    // 根据用户ID查询该用户的帖子列表->带分页，包含用户信息
    @Operation(summary = "查询用户帖子列表->分页，包含用户信息", description = "传入用户ID和分页参数，返回包含用户信息和owner标志")
    @GetMapping("/getArticleListByUserIdWithPageAndUserInfo")
    public Result<ArticleListByUserIdPageResponse> getArticleListByUserIdWithPageAndUserInfo(Long userId, @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize, HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        Long loginUserId = (loginUser != null) ? loginUser.getId() : -1L;
        return Result.success(articleService.queryArticleListByUserIdWithPageAndUserInfo(userId, loginUserId, pageNum, pageSize));
    }

    @Operation(summary = "查询热帖榜单TopN", description = "从RedisZSet中取点赞数最高的N篇帖子ID，文章在发布时就以score=0入库，因此若ZSe为空会抛异常")
    @GetMapping("/getHotArticleList")
    public Result<List<Long>> getHotArticleList(@RequestParam(defaultValue = "10") Integer topN) {
        return Result.success(articleService.getHotArticleList(topN));
    }

    /** 分页查询热帖榜，最多返回排名前 50 条。 */
    @Operation(summary = "分页查询热帖榜", description = "后端按热度排名分页，每页最多10条，总榜最多50条")
    @GetMapping("/getHotArticleListWithPage")
    public Result<PageResult<HotArticleListItemVO>> getHotArticleListWithPage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        Long loginUserId = loginUser == null ? null : loginUser.getId();
        return Result.success(articleService.queryHotArticleListWithPage(pageNum, pageSize, loginUserId));
    }

    @Operation(summary = "回收站：查看自己已删除的帖子（分页）",
            description = "依据 delete_state=1 过滤出当前登录用户已删除的帖子，按更新时间倒序返回，仅本人可见")
    @GetMapping("/getDeletedArticleListWithPage")
    public Result<PageResult<ArticleBriefVO>> getDeletedArticleListWithPage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        return Result.success(articleService.queryDeletedArticleListWithPage(loginUser.getId(), pageNum, pageSize));
    }

    @Operation(summary = "文章内容安全审核")
    @PostMapping("/validateText")
    public Result<ArticleValidateTextVO> validateText(@RequestBody ValidateTextRequest body) {
        return Result.success(articleService.validateContentResult(body));
    }

    @Operation(summary = "获取帖子 AI 摘要")
    @GetMapping("/getSummary")
    public Result<String> getSummary(Long articleId) {
        return Result.successData(articleService.getArticleSummary(articleId));
    }

    @Operation(summary = "流式生成帖子 AI 智能导读")
    @GetMapping(value = "/streamGuide", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamGuide(@RequestParam Long articleId) {
        SseEmitter emitter = new SseEmitter(180_000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError((e) -> emitter.complete());
        sseExecutor.execute(() -> articleGuideStreamService.streamArticleGuide(articleId, emitter));
        return emitter;
    }

    @Operation(summary = "通过URL直接更新帖子封面", description = "传入帖子ID和已上传的图片URL，直接写入数据库")
    @PostMapping("/updateCoverUrl")
    public Result<String> updateCoverUrl(@RequestParam Long articleId, @RequestParam String coverUrl, HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null){
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        articleService.updateArticleCoverByUrl(articleId, coverUrl, loginUser.getId());
        return Result.success("封面更新成功");
    }

    @Operation(summary = "全量替换帖子相册图",
            description = "传入 articleId + imageUrls(最多15张, 顺序即展示顺序; 每个URL须落在 forum_article_picture/ 子目录). " +
                          "传空数组等同清空相册. 有图时正文必须 ≥ 10 字符, 否则返回 1146.")
    @PostMapping("/replaceArticleImages")
    public Result<String> replaceArticleImages(@RequestBody ReplaceArticleImagesRequest req, HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        articleService.replaceArticleImages(req.getArticleId(), loginUser.getId(), req.getImageUrls());
        return Result.success("相册已更新");
    }

    @Operation(summary = "设置帖子视频", description = "把帖子切换为视频帖并绑定视频URL；会自动清空相册图；封面仍需走图片封面接口")
    @PostMapping("/setArticleVideo")
    public Result<String> setArticleVideo(@RequestParam Long articleId, @RequestParam String videoUrl, HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        articleService.setArticleVideo(articleId, loginUser.getId(), videoUrl);
        return Result.success("视频已绑定");
    }

    @Operation(summary = "清空帖子视频", description = "把帖子切回图片帖并清空视频URL（不影响封面）；相册由 replaceArticleImages 再维护")
    @PostMapping("/clearArticleVideo")
    public Result<String> clearArticleVideo(@RequestParam Long articleId, HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        articleService.clearArticleVideo(articleId, loginUser.getId());
        return Result.success("视频已清空");
    }
}
