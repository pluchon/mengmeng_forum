package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.entity.db.CheckinLog;
import org.pluchon.forum.entity.vo.checkin.CheckinResultResponse;
import org.pluchon.forum.entity.vo.checkin.CheckinRuleMonthResponse;
import org.pluchon.forum.entity.vo.checkin.CheckinStatusResponse;
import org.pluchon.forum.entity.vo.common.CursorPageResult;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.service.interfaces.checkin.CheckinService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "签到模块", description = "用户签到 / 状态查询 / 签到流水 / 签到规则")
@RestController
@RequestMapping("/checkin")
public class CheckinController {

    @Autowired
    private CheckinService checkinService;

    @Operation(summary = "执行签到", description = "同一用户当天重复签到会返回已签到状态码. 一次返回本次得分 + 签后最新状态")
    @PostMapping("/doCheckin")
    public Result<CheckinResultResponse> doCheckin(HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(checkinService.doCheckin(sessionUser.getId()));
    }

    @Operation(summary = "查询签到状态", description = "用于进入签到页时展示日历高亮 / 是否已签 / 下一档奖励. 不会触发签到")
    @GetMapping("/info")
    public Result<CheckinStatusResponse> getStatus(HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(checkinService.getStatus(sessionUser.getId()));
    }

    @Operation(summary = "查询签到流水(分页)", description = "倒序按签到日期返回")
    @GetMapping("/log")
    public Result<PageResult<CheckinLog>> getLogWithPage(@RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize, HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(checkinService.getLogWithPage(sessionUser.getId(), pageNum, pageSize));
    }

    @Operation(summary = "查询月度签到规则", description = "可传 month=1~12 查询其它月份, 找不到则回退 month=0 兜底")
    @GetMapping("/rule")
    public Result<CheckinRuleMonthResponse> getRule(@RequestParam(required = false) Integer month) {
        return Result.success(checkinService.getRule(month));
    }

    @Operation(summary = "签到流水游标分页", description = "深分页推荐；cursor 取自上一页 nextCursor")
    @GetMapping("/log/cursor")
    public Result<CursorPageResult<CheckinLog>> getLogWithCursor(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(checkinService.getLogWithCursor(sessionUser.getId(), cursor, pageSize));
    }
}
