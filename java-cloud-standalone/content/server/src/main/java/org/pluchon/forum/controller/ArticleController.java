package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.utils.HttpRequestUtils;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.vo.article.ArticleBriefVO;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.entity.dto.article.PublishArticleRequest;
import org.pluchon.forum.entity.dto.article.ReplaceArticleImagesRequest;
import org.pluchon.forum.entity.dto.article.SubmitForAuditRequest;
import org.pluchon.forum.entity.dto.article.UpdateArticleRequest;
import org.pluchon.forum.entity.dto.article.SetArticleMusicRequest;
import org.pluchon.forum.entity.vo.article.ArticleDetailResponse;
import org.pluchon.forum.entity.vo.article.ArticleListByUserIdPageResponse;
import org.pluchon.forum.entity.vo.article.AuditStatusResponse;
import org.pluchon.forum.entity.vo.article.HotArticleListItemVO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.creator.CreatorDashboardVO;
import org.pluchon.forum.entity.vo.creator.CreatorInsightVO;
import org.pluchon.forum.entity.vo.creator.CreatorInsightDataVO;
import org.pluchon.forum.service.interfaces.creator.CreatorDashboardService;
import org.pluchon.forum.service.interfaces.creator.CreatorInsightService;
import org.pluchon.forum.service.interfaces.article.ArticleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.pluchon.forum.entity.dto.article.ArticleSummaryRegenerateRequest;
import org.pluchon.forum.entity.vo.article.ArticleSummaryVO;
import org.pluchon.forum.entity.dto.article.ContentReportRequest;
import org.pluchon.forum.entity.vo.article.ContentReportVO;
import org.pluchon.forum.service.interfaces.moderation.ContentReportService;
import org.pluchon.forum.service.interfaces.article.ArticleSummaryService;


@Tag(name = "帖子模块", description = "帖子的增删改查接口")
@RestController
@RequestMapping("/article")
public class ArticleController {

    @Autowired
    private ArticleSummaryService articleSummaryService;

    @Autowired
    private ContentReportService contentReportService;
    @Autowired
    private ArticleService articleService;

    @Autowired
    private CreatorDashboardService creatorDashboardService;

    @Autowired
    private CreatorInsightService creatorInsightService;

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
        return Result.success(articleService.queryArticleDetailByArticleId(articleId, userId,
                HttpRequestUtils.resolveClientIp(httpServletRequest)));
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

    // 根据用户ID查询该用户的帖子列表 >带分页
    @Operation(summary = "查询用户帖子列表->分页", description = "传入用户ID和分页参数")
    @GetMapping("/getArticleListByUserIdWithPage")
    public Result<PageResult<ArticleBriefVO>> getArticleListByUserIdWithPage(Long userId, @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize, Integer status, String keyword,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        Long loginUserId = (loginUser != null) ? loginUser.getId() : -1L;
        return Result.success(articleService.queryArticleListByUserIdWithPage(userId, loginUserId, pageNum, pageSize,
                status, keyword));
    }

    /** 获取当前登录用户的创作中心统计 */
    @Operation(summary = "创作中心统计", description = "按自然周返回阅读与点赞趋势，并返回本月新增数据")
    @GetMapping("/creator/dashboard")
    public Result<CreatorDashboardVO> creatorDashboard(
            @RequestParam(defaultValue = "0") Integer weekOffset,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        return Result.success(creatorDashboardService.getDashboard(loginUser.getId(), weekOffset));
    }

    /** 生成当前登录用户的创作数据小结 */
    @Operation(summary = "创作数据 AI 小结", description = "支持近一周、近半个月、近一个月和近半年，统计数据不变时复用缓存")
    @PostMapping("/creator/insight")
    public Result<CreatorInsightVO> creatorInsight(
            @RequestParam(defaultValue = "WEEK") String period,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        return Result.success(creatorInsightService.generate(loginUser.getId(), period));
    }

    /** 查询创作趋势和已缓存小结 */
    @GetMapping("/creator/insight-data")
    public Result<CreatorInsightDataVO> creatorInsightData(
            @RequestParam(value = "period", required = false) String period,
            HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        return Result.success(creatorInsightService.loadData(loginUser.getId(), period));
    }

    // 根据用户ID查询该用户的帖子列表 >带分页，包含用户信息
    @Operation(summary = "查询用户帖子列表->分页，包含用户信息", description = "传入用户ID和分页参数，返回包含用户信息和owner标志")
    @GetMapping("/getArticleListByUserIdWithPageAndUserInfo")
    public Result<ArticleListByUserIdPageResponse> getArticleListByUserIdWithPageAndUserInfo(Long userId, @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize, HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        Long loginUserId = (loginUser != null) ? loginUser.getId() : -1L;
        return Result.success(articleService.queryArticleListByUserIdWithPageAndUserInfo(userId, loginUserId, pageNum, pageSize));
    }

    /** 分页查询热帖榜，最多返回排名前 28 条 */
    @Operation(summary = "分页查询热帖榜", description = "后端按热度排名分页，每页最多14条，总榜最多28条")
    @GetMapping("/getHotArticleListWithPage")
    public Result<PageResult<HotArticleListItemVO>> getHotArticleListWithPage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        Long loginUserId = loginUser == null ? null : loginUser.getId();
        return Result.success(articleService.queryHotArticleListWithPage(pageNum, pageSize, loginUserId));
    }

    /** 查询帖子AI总结状态与内容 */
    @Operation(summary = "查询帖子AI总结状态与内容")
    @GetMapping("/summary")
    public Result<ArticleSummaryVO> getSummaryState(@RequestParam Long articleId) {
        return Result.success(articleSummaryService.getSummary(articleId));
    }

    /** 重新生成帖子AI总结 */
    @Operation(summary = "重新生成帖子AI总结")
    @PostMapping("/summary/regenerate")
    public Result<ArticleSummaryVO> regenerateSummary(
            @Valid @RequestBody ArticleSummaryRegenerateRequest request,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        return Result.success(articleSummaryService.regenerate(request.getArticleId(), loginUser.getId()));
    }

    /** 举报帖子或评论 */
    @PostMapping("/report")
    public Result<ContentReportVO> reportContent(
            @Valid @RequestBody ContentReportRequest request,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        return Result.success(contentReportService.report(loginUser.getId(), request));
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

    @Operation(summary = "绑定帖子配乐", description = "与相册/视频正交；URL 须落在 music/music_* 目录")
    @PostMapping("/setArticleMusic")
    public Result<String> setArticleMusic(@Valid @RequestBody SetArticleMusicRequest req, HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        articleService.setArticleMusic(
                req.getArticleId(),
                loginUser.getId(),
                req.getMusicKey(),
                req.getMusicTitle(),
                req.getMusicCoverUrl(),
                req.getMusicAudioUrl(),
                req.getMusicLrcUrl());
        return Result.success("配乐已绑定");
    }

    @Operation(summary = "清空帖子配乐")
    @PostMapping("/clearArticleMusic")
    public Result<String> clearArticleMusic(@RequestParam Long articleId, HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        articleService.clearArticleMusic(articleId, loginUser.getId());
        return Result.success("配乐已清空");
    }
}
