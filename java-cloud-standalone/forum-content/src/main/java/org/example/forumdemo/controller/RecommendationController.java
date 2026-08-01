package org.example.forumdemo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.recommendation.NotInterestedArticleRequest;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.recommendation.RecommendArticleVO;
import org.example.forumdemo.service.interfaces.recommendation.RecommendationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 推荐模块
@Tag(name = "推荐模块", description = "为你推荐与帖子级反馈")
@RestController
@RequestMapping("/recommend")
public class RecommendationController {

    @Autowired
    private RecommendationService recommendationService;

    /** 获取为你推荐分页，游客自动使用公开兜底流。 */
    @Operation(summary = "获取为你推荐分页")
    @GetMapping("/feed")
    public Result<PageResult<RecommendArticleVO>> getFeed(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        Long loginUserId = loginUser == null ? null : loginUser.getId();
        return Result.success(recommendationService.getFeed(loginUserId, pageNum, pageSize));
    }

    /** 将当前帖子标记为不感兴趣。 */
    @Operation(summary = "标记推荐帖子不感兴趣")
    @PostMapping("/feedback/not-interested")
    public Result<String> markNotInterested(@Valid @RequestBody NotInterestedArticleRequest request,
            HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        recommendationService.markNotInterested(loginUser.getId(), request);
        return Result.success("已减少这篇帖子的推荐");
    }

    /** 分页查询当前用户设为不感兴趣的帖子。 */
    @Operation(summary = "分页查询不感兴趣帖子")
    @GetMapping("/feedback/not-interested")
    public Result<PageResult<RecommendArticleVO>> getNotInterestedArticles(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "12") Integer pageSize,
            HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(recommendationService.getNotInterestedArticles(loginUser.getId(), pageNum, pageSize));
    }

    /** 恢复当前用户对指定帖子的兴趣。 */
    @Operation(summary = "恢复推荐帖子兴趣")
    @DeleteMapping("/feedback/not-interested/{articleId}")
    public Result<String> restoreInterested(@PathVariable Long articleId, HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        recommendationService.restoreInterested(loginUser.getId(), articleId);
        return Result.success("已恢复兴趣");
    }
}
