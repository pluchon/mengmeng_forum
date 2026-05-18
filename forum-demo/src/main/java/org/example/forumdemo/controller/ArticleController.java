package org.example.forumdemo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.article.PublishArticleRequest;
import org.example.forumdemo.entity.dto.article.ReplaceArticleImagesRequest;
import org.example.forumdemo.entity.dto.article.SubmitForAuditRequest;
import org.example.forumdemo.entity.dto.article.UpdateArticleRequest;
import org.example.forumdemo.entity.vo.article.ArticleDetailResponse;
import org.example.forumdemo.entity.vo.article.ArticleListByUserIdPageResponse;
import org.example.forumdemo.entity.vo.article.AuditStatusResponse;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.service.interfaces.article.ArticleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "帖子模块", description = "帖子的增删改查接口")
@RestController
@RequestMapping("/article")
public class ArticleController {
    @Autowired
    private ArticleService articleService;

    @Operation(summary = "创建帖子草稿", description = "文章内容先入库为草稿，返回帖子ID；封面上传和发布动作走独立接口")
    @PostMapping("/createDraft")
    public Result<Long> createDraft(@Valid @RequestBody PublishArticleRequest publishArticleRequest, HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
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
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null){
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        articleService.publishArticle(articleId, loginUser.getId());
        return Result.success("发布成功");
    }

    @Operation(summary = "提交帖子进入异步审核流程",
            description = "前置: 内容/封面/相册图均已落库. 后端会扭转状态到 PENDING_AUDIT, 投递 MQ 给 LangGraph. " +
                    "前端展示\"审核中\"页面. notifyEmail=true 时审核结果会额外推送邮件(站内信无论如何都发).")
    @PostMapping("/submitForAudit")
    public Result<String> submitForAudit(@Valid @RequestBody SubmitForAuditRequest req,
                                         HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        String taskId = articleService.submitForAudit(req.getArticleId(), loginUser.getId(), req.getNotifyEmail());
        return Result.success(taskId);
    }

    @Operation(summary = "查询帖子审核状态(轮询兜底)",
            description = "前端\"审核中\"页面在 WebSocket 未到达的兜底场景下调用此接口轮询. " +
                    "返回当前状态码 + 文本 + 重试次数 + 最近一次审核结论文本.")
    @GetMapping("/getAuditStatus")
    public Result<AuditStatusResponse> getAuditStatus(@RequestParam Long articleId,
                                                      HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        return Result.success(articleService.getAuditStatus(articleId, loginUser.getId()));
    }

    @Operation(summary = "根据帖子Id展示帖子详细内容", description = "传入帖子ID")
    @GetMapping("/selectArticleDetailByArticleId")
    public Result<ArticleDetailResponse> selectArticleDetailByArticleId(Long articleId, HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        Long userId = (loginUser == null) ? -1L : loginUser.getId();
        return Result.success(articleService.queryArticleDetailByArticleId(articleId, userId));
    }

    @Operation(summary = "编辑帖子内容，只有作者本人才可以", description = "传入帖子ID，标题，以及正文")
    @PutMapping("/updateArticleByArticleId")
    public Result<String> updateArticleByArticleId(@RequestBody UpdateArticleRequest updateArticleRequest, HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null){
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        articleService.updateArticle(updateArticleRequest, loginUser.getId());
        return Result.success("更新帖子成功");
    }

    @Operation(summary = "删除对应帖子", description = "传入帖子ID，注意只有作者本人才可以")
    @DeleteMapping("/deleteArticle")
    public Result<String> deleteArticle(Long articleId, HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null){
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        articleService.deleteArticle(articleId, loginUser.getId());
        return Result.success("删除帖子成功");
    }

    // 根据用户ID查询该用户的帖子列表->带分页
    @Operation(summary = "查询用户帖子列表->分页", description = "传入用户ID和分页参数")
    @GetMapping("/getArticleListByUserIdWithPage")
    public Result<PageResult<Article>> getArticleListByUserIdWithPage(Long userId, @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize, HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        Long loginUserId = (loginUser != null) ? loginUser.getId() : -1L;
        return Result.success(articleService.queryArticleListByUserIdWithPage(userId, loginUserId, pageNum, pageSize));
    }

    // 根据用户ID查询该用户的帖子列表->带分页，包含用户信息
    @Operation(summary = "查询用户帖子列表->分页，包含用户信息", description = "传入用户ID和分页参数，返回包含用户信息和owner标志")
    @GetMapping("/getArticleListByUserIdWithPageAndUserInfo")
    public Result<ArticleListByUserIdPageResponse> getArticleListByUserIdWithPageAndUserInfo(Long userId, @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize, HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        Long loginUserId = (loginUser != null) ? loginUser.getId() : -1L;
        return Result.success(articleService.queryArticleListByUserIdWithPageAndUserInfo(userId, loginUserId, pageNum, pageSize));
    }

    @Operation(summary = "查询热帖榜单TopN", description = "从RedisZSet中取点赞数最高的N篇帖子ID，文章在发布时就以score=0入库，因此若ZSe为空会抛异常")
    @GetMapping("/getHotArticleList")
    public Result<List<Long>> getHotArticleList(@RequestParam(defaultValue = "10") Integer topN) {
        return Result.success(articleService.getHotArticleList(topN));
    }

    @Operation(summary = "回收站：查看自己已删除的帖子（分页）",
            description = "依据 delete_state=1 过滤出当前登录用户已删除的帖子，按更新时间倒序返回，仅本人可见")
    @GetMapping("/getDeletedArticleListWithPage")
    public Result<PageResult<Article>> getDeletedArticleListWithPage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        return Result.success(articleService.queryDeletedArticleListWithPage(loginUser.getId(), pageNum, pageSize));
    }

    @Operation(summary = "文章内容安全审核")
    @PostMapping("/validateText")
    public Result<Map<String, Object>> validateText(@RequestBody Map<String, String> body) {
        String content = body.getOrDefault("content", body.get("text"));
        return Result.success(articleService.validateContentResult(content));
    }

    @Operation(summary = "获取帖子 AI 摘要")
    @GetMapping("/getSummary")
    public Result<String> getSummary(Long articleId) {
        return Result.successData(articleService.getArticleSummary(articleId));
    }

    @Operation(summary = "通过URL直接更新帖子封面", description = "传入帖子ID和已上传的图片URL，直接写入数据库")
    @PostMapping("/updateCoverUrl")
    public Result<String> updateCoverUrl(@RequestParam Long articleId, @RequestParam String coverUrl, HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
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
    public Result<String> replaceArticleImages(@RequestBody ReplaceArticleImagesRequest req,
                                               HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        articleService.replaceArticleImages(req.getArticleId(), loginUser.getId(), req.getImageUrls());
        return Result.success("相册已更新");
    }
}
