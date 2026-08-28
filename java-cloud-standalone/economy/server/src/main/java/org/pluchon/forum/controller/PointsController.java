package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.dto.points.PointsLogQueryDTO;
import org.pluchon.forum.entity.vo.points.PointsCenterOverviewVO;
import org.pluchon.forum.entity.vo.points.PointsCenterChartVO;
import org.pluchon.forum.entity.vo.points.PointsCenterTrendVO;
import org.pluchon.forum.entity.vo.points.PointsLogVO;
import org.pluchon.forum.entity.vo.points.PointsWalletVO;
import org.pluchon.forum.service.interfaces.points.PointsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "积分钱包", description = "余额查询 / 萌币中心流水与趋势")
@RestController
@RequestMapping("/points")
public class PointsController {

    @Autowired
    private PointsService pointsService;

    @Operation(summary = "钱包概览", description = "返回当前余额 + 累计签到入账 + 累计消费, 用于「我的积分」页头部")
    @GetMapping("/wallet")
    public Result<PointsWalletVO> getWallet(HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(pointsService.getWallet(loginUser.getId()));
    }

    /** 萌币中心概览：自然周趋势、里程碑与收支来源 */
    @GetMapping("/center/overview")
    public Result<PointsCenterOverviewVO> getCenterOverview(@RequestParam(defaultValue = "0") Integer weekOffset,
                                                            HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(pointsService.getCenterOverview(loginUser.getId(), weekOffset));
    }

    /** 萌币中心周趋势：切换周时不重复刷新里程碑 */
    @GetMapping("/center/trend")
    public Result<PointsCenterTrendVO> getCenterTrend(@RequestParam(defaultValue = "0") Integer weekOffset,
                                                       HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(pointsService.getCenterTrend(loginUser.getId(), weekOffset));
    }

    /** 萌币流水图表：按与流水相同的筛选条件聚合 */
    @GetMapping("/center/chart")
    public Result<PointsCenterChartVO> getCenterChart(PointsLogQueryDTO query,
                                                       HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(pointsService.getCenterChart(loginUser.getId(), query));
    }

    /** 萌币中心流水：后端负责收支、来源、时间筛选与分页 */
    @GetMapping("/center/log")
    public Result<PageResult<PointsLogVO>> getCenterLog(PointsLogQueryDTO query,
                                                         HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(pointsService.getCenterLogWithPage(loginUser.getId(), query));
    }

    /** 领取已达成的萌币里程碑 */
    @PostMapping("/center/milestones/{milestoneCode}/claim")
    public Result<Integer> claimMilestone(@PathVariable String milestoneCode,
                                          HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(pointsService.claimMilestone(loginUser.getId(), milestoneCode));
    }

    /** 内部服务调用：查询余额 */
    @GetMapping("/internal/{userId}/balance")
    public Integer internalBalance(@PathVariable("userId") Long userId) {
        PointsWalletVO wallet = pointsService.getWallet(userId);
        return wallet == null || wallet.getBalance() == null ? 0 : wallet.getBalance();
    }

    /** 内部服务调用：是否已有幂等成功流水 */
    @GetMapping("/internal/{userId}/idempotency")
    public Boolean internalHasIdempotency(
            @PathVariable("userId") Long userId,
            @RequestParam("idempotencyKey") String idempotencyKey) {
        return pointsService.hasIdempotencyRecord(userId, idempotencyKey);
    }

    /** 内部服务调用：加积分，返回变动后余额 */
    @PostMapping("/internal/{userId}/add")
    public Integer internalAdd(
            @PathVariable("userId") Long userId,
            @RequestParam("amount") int amount,
            @RequestParam("sourceType") byte sourceType,
            @RequestParam(value = "relatedId", required = false) Long relatedId,
            @RequestParam(value = "remark", required = false) String remark,
            @RequestParam(value = "idempotencyKey", required = false) String idempotencyKey) {
        return pointsService.addPoints(userId, amount, sourceType, relatedId, remark, idempotencyKey);
    }

    /** 内部服务调用：扣积分，返回变动后余额 */
    @PostMapping("/internal/{userId}/deduct")
    public Integer internalDeduct(
            @PathVariable("userId") Long userId,
            @RequestParam("amount") int amount,
            @RequestParam("sourceType") byte sourceType,
            @RequestParam(value = "relatedId", required = false) Long relatedId,
            @RequestParam(value = "remark", required = false) String remark,
            @RequestParam(value = "idempotencyKey", required = false) String idempotencyKey) {
        return pointsService.deductPoints(userId, amount, sourceType, relatedId, remark, idempotencyKey);
    }
}
