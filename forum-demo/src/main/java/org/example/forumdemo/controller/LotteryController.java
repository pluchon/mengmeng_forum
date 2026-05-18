package org.example.forumdemo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.lottery.LotteryDrawDTO;
import org.example.forumdemo.entity.vo.lottery.LotteryActivityInfoVO;
import org.example.forumdemo.entity.vo.lottery.LotteryActivityListItemVO;
import org.example.forumdemo.entity.vo.lottery.LotteryDrawResultVO;
import org.example.forumdemo.entity.vo.lottery.LotterySurpriseClaimVO;
import org.example.forumdemo.service.interfaces.lottery.LotteryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "积分抽奖", description = "活动信息 / 单抽与十连")
@RestController
@RequestMapping("/lottery")
public class LotteryController {

    @Autowired
    private LotteryService lotteryService;

    @Operation(summary = "可选活动列表", description = "需登录; 返回对用户开放且进行中的活动，按 id 倒序")
    @GetMapping("/activities")
    public Result<List<LotteryActivityListItemVO>> activities() {
        return Result.success(lotteryService.listSelectableActivities());
    }

    @Operation(summary = "当前活动与奖池", description = "需登录; activityId 可选，不传则取最新进行中活动")
    @GetMapping("/info")
    public Result<LotteryActivityInfoVO> info(
            @RequestParam(required = false) Long activityId,
            HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(lotteryService.getActivityInfo(loginUser.getId(), activityId));
    }

    @Operation(summary = "抽奖", description = "times=1 单抽 times=10 十连; 先扣积分再开奖")
    @PostMapping("/draw")
    public Result<LotteryDrawResultVO> draw(@RequestBody LotteryDrawDTO dto, HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(lotteryService.draw(loginUser.getId(), dto));
    }

    @Operation(summary = "抽奖页彩蛋积分", description = "「点我看看」一次性积分；已领取则返回幂等结果")
    @PostMapping("/surprise-bonus")
    public Result<LotterySurpriseClaimVO> surpriseBonus(HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(lotteryService.claimPageSurpriseBonus(loginUser.getId()));
    }
}
