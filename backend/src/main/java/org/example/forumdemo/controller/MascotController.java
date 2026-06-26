package org.example.forumdemo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.mascot.MascotChatRequest;
import org.example.forumdemo.entity.vo.mascot.CompanionMessageVO;
import org.example.forumdemo.entity.vo.mascot.CompanionSessionVO;
import org.example.forumdemo.entity.vo.mascot.MascotChatResponseVO;
import org.example.forumdemo.entity.vo.mascot.MascotModelPublicVO;
import org.example.forumdemo.entity.vo.mascot.MascotQuotaHintVO;
import org.example.forumdemo.service.interfaces.mascot.CompanionMemoryService;
import org.example.forumdemo.service.interfaces.mascot.MascotService;
import org.example.forumdemo.service.interfaces.vip.VipCenterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.Executor;

@Tag(name = "看板娘", description = "Live2D 看板娘对话 (Java 转发 Python)")
@Validated
@RestController
@RequestMapping("/mascot")
public class MascotController {

    @Autowired
    private MascotService mascotService;

    @Autowired
    private CompanionMemoryService companionMemoryService;

    @Autowired
    private VipCenterService vipCenterService;

    @Autowired
    @Qualifier("sseExecutor")
    private Executor sseExecutor;

    @Operation(summary = "当前模型配额使用率", description = "会员 PRO/MAX：用于「使用萌币积分」按钮（≥95% 可开启）")
    @GetMapping("/quota-hint")
    public Result<MascotQuotaHintVO> quotaHint(
            @RequestParam(required = false) String llmProvider,
            HttpServletRequest httpServletRequest) {
        User user = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (user == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        return Result.success(vipCenterService.quotaHintForLlmRoute(user.getId(), llmProvider));
    }

    @Operation(summary = "上架中的看板娘模型列表", description = "无需登录；前端用于 Live2D 选择与展示")
    @GetMapping("/public/models")
    public Result<List<MascotModelPublicVO>> publicModels() {
        return Result.success(mascotService.listPublicModels());
    }

    @Operation(summary = "看板娘对话", description = "单次 JSON 返回; 普通用户有每日次数上限 (见 application.yml)")
    @PostMapping("/chat")
    public Result<MascotChatResponseVO> chat(
            @Valid @RequestBody MascotChatRequest request,
            HttpServletRequest httpServletRequest) {
        User user = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (user == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        return Result.success(mascotService.chat(user, request));
    }

    @Operation(summary = "看板娘流式对话", description = "SSE 流式返回；data 含 text / meta / error")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @Valid @RequestBody MascotChatRequest request,
            HttpServletRequest httpServletRequest) {
        User user = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        SseEmitter emitter = new SseEmitter(180_000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError((e) -> emitter.complete());
        if (user == null) {
            try {
                emitter.send(SseEmitter.event().data("{\"error\":\"未登录\"}"));
                emitter.complete();
            } catch (Exception ignored) {
                emitter.completeWithError(new IllegalStateException("unlogin"));
            }
            return emitter;
        }
        sseExecutor.execute(() -> mascotService.streamChat(user, request, emitter));
        return emitter;
    }

    @Operation(summary = "陪伴助手会话列表", description = "按功能 skill 分页返回当前用户会话")
    @GetMapping("/companion/sessions")
    public Result<List<CompanionSessionVO>> companionSessions(
            @RequestParam String skill,
            @RequestParam(defaultValue = "30") int limit,
            HttpServletRequest httpServletRequest) {
        User user = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (user == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        return Result.success(companionMemoryService.listSessions(user.getId(), skill, limit));
    }

    @Operation(summary = "陪伴助手会话消息", description = "加载指定会话的全部消息")
    @GetMapping("/companion/sessions/{sessionId}/messages")
    public Result<List<CompanionMessageVO>> companionMessages(
            @PathVariable Long sessionId,
            HttpServletRequest httpServletRequest) {
        User user = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (user == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        return Result.success(companionMemoryService.listMessages(user.getId(), sessionId));
    }
}
