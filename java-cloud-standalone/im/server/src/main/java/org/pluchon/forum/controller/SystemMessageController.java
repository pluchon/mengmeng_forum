package org.pluchon.forum.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.SystemMessage;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.service.interfaces.message.SystemMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统消息接口(平台->用户的单向通知, 当前承载帖子审核结果).
 * 与私信模块(/message/**)物理隔离, 前端在站内信页面可同时拉两组接口聚合渲染.
 */
@Tag(name = "系统消息", description = "审核结果/公告等系统通知")
@RestController
@RequestMapping("/system-message")
public class SystemMessageController {

    @Autowired
    private SystemMessageService systemMessageService;

    @Operation(summary = "分页查询当前用户的系统消息",
            description = "按创建时间倒序; 包含审核通过/未通过/异常等所有类型. 仅本人可见.")
    @GetMapping("/list")
    public Result<PageResult<SystemMessage>> list(@RequestParam(required = false) Integer pageNum,
                                                  @RequestParam(required = false) Integer pageSize,
                                                  HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        Page<SystemMessage> page = systemMessageService.queryByUser(loginUser.getId(), pageNum, pageSize);
        PageResult<SystemMessage> resp = new PageResult<>(
                page.getRecords(), page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize(),
                page.getPages(), page.hasNext()
        );
        return Result.success(resp);
    }

    @Operation(summary = "查询未读系统消息数量(用于红点)",
            description = "返回当前登录用户未读条数; 未登录返回 0.")
    @GetMapping("/unreadCount")
    public Result<Long> unreadCount(HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.success(0L);
        }
        return Result.success(systemMessageService.countUnread(loginUser.getId()));
    }

    @Operation(summary = "标记单条系统消息为已读",
            description = "仅消息接收方本人可调用; 已读再次调用幂等(直接返回成功).")
    @PutMapping("/markOneRead")
    public Result<String> markOneRead(@RequestParam Long messageId, HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        systemMessageService.markOneRead(messageId, loginUser.getId());
        return Result.success("OK");
    }

    @Operation(summary = "标记全部系统消息为已读",
            description = "把当前登录用户所有未读系统消息批量置为已读.")
    @PutMapping("/markAllRead")
    public Result<Integer> markAllRead(HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        return Result.success(systemMessageService.markAllRead(loginUser.getId()));
    }

    @Operation(summary = "软删一条系统消息",
            description = "delete_state 置 1; 仅接收方本人可调用.")
    @DeleteMapping("/delete")
    public Result<String> delete(@RequestParam Long messageId, HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        systemMessageService.deleteOne(messageId, loginUser.getId());
        return Result.success("OK");
    }

    /** 内部：跨服务创建系统消息（如内容审核结果） */
    @PostMapping("/internal/create")
    public Long internalCreate(
            @RequestParam("receiveUserId") Long receiveUserId,
            @RequestParam("type") Byte type,
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam(value = "relatedId", required = false) Long relatedId,
            @RequestParam(value = "payload", required = false) String payload) {
        return systemMessageService.createMessage(receiveUserId, type, title, content, relatedId, payload);
    }
}
