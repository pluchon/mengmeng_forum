package org.example.forumdemo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.points.PointsDailyVO;
import org.example.forumdemo.entity.vo.points.PointsLogVO;
import org.example.forumdemo.entity.vo.points.PointsWalletVO;
import org.example.forumdemo.service.interfaces.points.PointsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "积分钱包", description = "余额查询 / 流水分页 / 按日聚合(ECharts)")
@RestController
@RequestMapping("/points")
public class PointsController {

    @Autowired
    private PointsService pointsService;

    @Operation(summary = "钱包概览", description = "返回当前余额 + 累计签到入账 + 累计消费, 用于「我的积分」页头部")
    @GetMapping("/wallet")
    public Result<PointsWalletVO> getWallet(HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(pointsService.getWallet(loginUser.getId()));
    }

    @Operation(summary = "积分流水分页", description = "倒序按时间返回, 用于「我的积分 -> 流水」明细页")
    @GetMapping("/log")
    public Result<PageResult<PointsLogVO>> getLogWithPage(@RequestParam(defaultValue = "1") Integer pageNum,
                                                          @RequestParam(defaultValue = "10") Integer pageSize,
                                                          @RequestParam(required = false) Byte sourceType,
                                                          HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(pointsService.getLogWithPage(loginUser.getId(), pageNum, pageSize, sourceType));
    }

    @Operation(summary = "积分按日聚合", description = "最近 N 天的入账 / 消费 / 净变动, 给前端 ECharts 直接喂数据. days 默认 30, 上限 365")
    @GetMapping("/daily")
    public Result<List<PointsDailyVO>> getDaily(@RequestParam(required = false) Integer days,
                                                HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(pointsService.getDailyAggregation(loginUser.getId(), days));
    }
}
