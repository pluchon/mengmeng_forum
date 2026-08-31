package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.entity.dto.lottery.LotteryCollectClaimDTO;
import org.pluchon.forum.entity.dto.lottery.LotteryDrawDTO;
import org.pluchon.forum.entity.dto.lottery.LotteryTaskClaimDTO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.lottery.LotteryActivityInfoVO;
import org.pluchon.forum.entity.vo.lottery.LotteryActivityListItemVO;
import org.pluchon.forum.entity.vo.lottery.LotteryCollectVO;
import org.pluchon.forum.entity.vo.lottery.LotteryDrawHistoryVO;
import org.pluchon.forum.entity.vo.lottery.LotteryDrawResultVO;
import org.pluchon.forum.entity.vo.lottery.LotteryPoolTaskVO;
import org.pluchon.forum.entity.vo.lottery.LotteryPublicRecentDrawVO;
import org.pluchon.forum.service.interfaces.lottery.LotteryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "积分抽奖", description = "活动信息 / 单抽与十连 / 公开中奖 / 本池任务")
@RestController
@RequestMapping("/lottery")
public class LotteryController {

    @Autowired
    private LotteryService lotteryService;

    @Operation(summary = "可选活动列表", description = "需登录; 分页返回对用户开放且进行中的活动，按 id 倒序")
    @GetMapping("/activities")
    public Result<PageResult<LotteryActivityListItemVO>> activities(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "4") Integer pageSize) {
        return Result.success(lotteryService.pageSelectableActivities(pageNum, pageSize));
    }

    @Operation(summary = "当前活动与奖池", description = "需登录; activityId 可选，不传则取最新进行中活动")
    @GetMapping("/info")
    public Result<LotteryActivityInfoVO> info(
            @RequestParam(required = false) Long activityId,
            HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(lotteryService.getActivityInfo(loginUser.getId(), activityId));
    }

    @Operation(summary = "全站公开近期中奖", description = "需登录; 每页默认 5 条，最多展示 5 页窗口")
    @GetMapping("/recent-public")
    public Result<PageResult<LotteryPublicRecentDrawVO>> recentPublic(
            @RequestParam(required = false) Long activityId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "5") Integer pageSize) {
        return Result.success(lotteryService.pagePublicRecentDraws(activityId, pageNum, pageSize));
    }

    @Operation(summary = "我的抽奖记录", description = "需登录; 按单次开奖记录分页（十连拆成多条），activityId 可选")
    @GetMapping("/records")
    public Result<PageResult<LotteryDrawHistoryVO>> records(
            @RequestParam(required = false) Long activityId,
            @RequestParam(required = false) Boolean rareOnly,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(lotteryService.queryDrawRecords(
                loginUser.getId(), activityId, rareOnly, pageNum, pageSize));
    }

    @Operation(summary = "抽奖", description = "times=1 单抽 times=10 十连; 可自动使用抵扣券")
    @PostMapping("/draw")
    public Result<LotteryDrawResultVO> draw(@RequestBody LotteryDrawDTO dto, HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(lotteryService.draw(loginUser.getId(), dto));
    }

    @Operation(summary = "领取本池任务奖励", description = "需登录; 校验进度后发放抵扣券")
    @PostMapping("/tasks/claim")
    public Result<LotteryPoolTaskVO> claimTask(@RequestBody LotteryTaskClaimDTO dto, HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(lotteryService.claimPoolTask(loginUser.getId(), dto));
    }

    @Operation(summary = "领取幸运收集册里程奖励", description = "需登录; 校验收集数后发放券/积分/VIP")
    @PostMapping("/collect/claim")
    public Result<LotteryCollectVO> claimCollect(@RequestBody LotteryCollectClaimDTO dto, HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(lotteryService.claimCollectMilestone(loginUser.getId(), dto));
    }
}
