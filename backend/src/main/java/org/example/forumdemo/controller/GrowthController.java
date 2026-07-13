package org.example.forumdemo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.growth.GrowthChallengeSubmitRequest;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.growth.GrowthChallengeDetailVO;
import org.example.forumdemo.entity.vo.growth.GrowthChallengeVO;
import org.example.forumdemo.entity.vo.growth.GrowthOverviewVO;
import org.example.forumdemo.entity.vo.growth.GrowthSubmitResultVO;
import org.example.forumdemo.service.interfaces.growth.GrowthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 成长中心接口
@Tag(name = "成长中心")
@RestController
@RequestMapping("/growth")
public class GrowthController {
    @Autowired
    private GrowthService growthService;

    /** 查询成长中心总览。 */
    @Operation(summary = "查询成长中心总览")
    @GetMapping("/overview")
    public Result<GrowthOverviewVO> overview(HttpServletRequest request) {
        return Result.success(growthService.overview(userId(request)));
    }

    /** 分页查询成长挑战。 */
    @Operation(summary = "分页查询成长挑战")
    @GetMapping("/challenges")
    public Result<PageResult<GrowthChallengeVO>> challenges(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "4") Integer pageSize,
            HttpServletRequest request) {
        return Result.success(growthService.challengePage(userId(request), pageNum, pageSize));
    }

    /** 开始指定挑战。 */
    @Operation(summary = "开始成长挑战")
    @PostMapping("/challenges/{challengeCode}/start")
    public Result<GrowthChallengeDetailVO> start(
            @PathVariable String challengeCode,
            HttpServletRequest request) {
        return Result.success(growthService.start(userId(request), challengeCode));
    }

    /** 提交挑战答案并结算奖励。 */
    @Operation(summary = "提交成长挑战")
    @PostMapping("/challenges/{challengeCode}/submit")
    public Result<GrowthSubmitResultVO> submit(
            @PathVariable String challengeCode,
            @Valid @RequestBody GrowthChallengeSubmitRequest body,
            HttpServletRequest request) {
        return Result.success(growthService.submit(userId(request), challengeCode, body));
    }

    private Long userId(HttpServletRequest request) {
        User user = (User) request.getAttribute(Constant.USER_SESSION);
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.USER_UNLOGIN));
        }
        return user.getId();
    }
}
