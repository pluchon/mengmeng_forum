package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.entity.vo.checkin.CheckinMonthResponse;
import org.pluchon.forum.entity.vo.checkin.CheckinResultResponse;
import org.pluchon.forum.entity.vo.checkin.CheckinRuleMonthResponse;
import org.pluchon.forum.entity.vo.checkin.CheckinStatusResponse;
import org.pluchon.forum.entity.vo.checkin.CheckinLogVO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.service.interfaces.checkin.CheckinService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "签到模块", description = "用户签到 / 补签 / 状态查询 / 签到流水 / 签到规则")
@RestController
@RequestMapping("/checkin")
public class CheckinController {

    @Autowired
    private CheckinService checkinService;

    /** 执行签到，同一用户当天重复签到会返回已签到状态码 */
    @PostMapping("/doCheckin")
    public Result<CheckinResultResponse> doCheckin(HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(checkinService.doCheckin(sessionUser.getId()));
    }

    /** 消耗补签卡，自动补签离今天最近的漏签日 */
    @PostMapping("/makeup")
    public Result<CheckinResultResponse> makeupCheckin(HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(checkinService.makeupCheckin(sessionUser.getId()));
    }

    /** 查询签到状态 */
    @GetMapping("/info")
    public Result<CheckinStatusResponse> getStatus(HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(checkinService.getStatus(sessionUser.getId()));
    }

    /** 查询月度签到摘要 日历 + 周统计 */
    @GetMapping("/month")
    public Result<CheckinMonthResponse> getMonth(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(checkinService.getMonth(sessionUser.getId(), year, month));
    }

    /** 查询签到流水 分页 */
    @GetMapping("/log")
    public Result<PageResult<CheckinLogVO>> getLogWithPage(@RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize, HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(checkinService.getLogWithPage(sessionUser.getId(), pageNum, pageSize));
    }

    /** 查询月度签到规则 */
    @GetMapping("/rule")
    public Result<CheckinRuleMonthResponse> getRule(@RequestParam(required = false) Integer month) {
        return Result.success(checkinService.getRule(month));
    }
}
