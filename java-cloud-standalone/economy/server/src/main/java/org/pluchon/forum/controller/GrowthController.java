package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.entity.dto.growth.GrowthChallengeSubmitRequest;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.growth.GrowthChallengeDetailVO;
import org.pluchon.forum.entity.vo.growth.GrowthChallengeVO;
import org.pluchon.forum.entity.vo.growth.GrowthExperienceRecordVO;
import org.pluchon.forum.entity.vo.growth.GrowthOverviewVO;
import org.pluchon.forum.entity.vo.growth.GrowthSubmitResultVO;
import org.pluchon.forum.service.interfaces.growth.GrowthService;
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

    /** 分页查询成长经验记录。 */
    @Operation(summary = "分页查询成长经验记录")
    @GetMapping("/records")
    public Result<PageResult<GrowthExperienceRecordVO>> records(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest request) {
        return Result.success(growthService.experienceRecordPage(userId(request), pageNum, pageSize));
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

    /** 内部：校验正式用户（跨服务发帖等） */
    @PostMapping("/internal/{userId}/require-formal")
    public void internalRequireFormal(@PathVariable("userId") Long userId) {
        growthService.requireFormalUser(userId);
    }

    /** 内部：新用户成长档案 */
    @PostMapping("/internal/{userId}/create-profile")
    public void internalCreateProfile(@PathVariable("userId") Long userId) {
        growthService.createNewUserProfile(userId);
    }

    private Long userId(HttpServletRequest request) {
        User user = (User) request.getAttribute(Constant.USER_SESSION);
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.USER_UNLOGIN));
        }
        return user.getId();
    }
}
