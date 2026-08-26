package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.dto.CompanionSessionRenameRequest;
import org.pluchon.forum.entity.dto.MascotChatRequest;
import org.pluchon.forum.entity.dto.MascotMemoryEditRequest;
import org.pluchon.forum.entity.dto.MascotRelatedRecommendationRequest;
import org.pluchon.forum.entity.vo.CompanionMessageVO;
import org.pluchon.forum.entity.vo.CompanionContextWindowVO;
import org.pluchon.forum.entity.vo.CompanionSessionVO;
import org.pluchon.forum.entity.vo.MascotChatResponseVO;
import org.pluchon.forum.entity.vo.MascotMemoryVO;
import org.pluchon.forum.entity.vo.MascotModelPublicVO;
import org.pluchon.forum.entity.vo.mascot.MascotQuotaHintVO;
import org.pluchon.forum.entity.vo.MascotRelatedRecommendationVO;
import org.pluchon.forum.service.interfaces.mascot.CompanionMemoryService;
import org.pluchon.forum.service.interfaces.mascot.MascotService;
import org.pluchon.forum.service.security.AiUserContext;
import org.pluchon.forum.service.security.AiUserLookupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.Executor;

@Slf4j
@Tag(name = "看板娘", description = "Live2D 看板娘对话")
@Validated
@RestController
@RequestMapping("/mascot")
public class MascotController {

    @Autowired
    private MascotService mascotService;

    @Autowired
    private CompanionMemoryService companionMemoryService;

    @Autowired
    private AiUserLookupService aiUserLookupService;

    @Autowired
    @Qualifier("sseExecutor")
    private Executor sseExecutor;

    @Operation(summary = "当前模型配额使用率", description = "会员 PRO/MAX 用于使用萌币积分")
    @GetMapping("/quota-hint")
    public Result<MascotQuotaHintVO> quotaHint(
            @RequestParam(required = false) String llmProvider,
            HttpServletRequest httpServletRequest) {
        AiUserContext user = currentUser(httpServletRequest);
        if (user == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        return Result.success(mascotService.quotaHintForLlmRoute(user.getId(), llmProvider));
    }

    @Operation(summary = "上架中的看板娘模型列表", description = "无需登录；前端用于 Live2D 选择与展示")
    @GetMapping("/public/models")
    public Result<List<MascotModelPublicVO>> publicModels() {
        return Result.success(mascotService.listPublicModels());
    }

    @Operation(summary = "看板娘对话", description = "单次 JSON 返回; 普通用户有每日次数上限")
    @PostMapping("/chat")
    public Result<MascotChatResponseVO> chat(
            @Valid @RequestBody MascotChatRequest request,
            HttpServletRequest httpServletRequest) {
        AiUserContext user = currentUser(httpServletRequest);
        if (user == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        return Result.success(mascotService.chat(user, request, resolveClientIp(httpServletRequest)));
    }

    @Operation(summary = "看板娘流式对话", description = "SSE 流式返回；data 含 text / meta / error")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @Valid @RequestBody MascotChatRequest request,
            HttpServletRequest httpServletRequest) {
        AiUserContext user = currentUser(httpServletRequest);
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
        String clientIp = resolveClientIp(httpServletRequest);
        sseExecutor.execute(() -> {
            try {
                mascotService.streamChat(user, request, clientIp, emitter);
            } catch (Exception ex) {
                log.warn("看板娘 SSE 线程未捕获异常: {}", ex.getMessage());
                try {
                    emitter.send(SseEmitter.event().data("{\"error\":\"对话失败，请稍后重试\"}"));
                    emitter.complete();
                } catch (Exception ignored) {
                    emitter.completeWithError(ex);
                }
            }
        });
        return emitter;
    }

    /** 用户确认后检索相关帖子 */
    @Operation(summary = "检索看板娘相关帖子", description = "用户确认后执行 RAG 检索，并保存实际展示的帖子结果")
    @PostMapping("/related-recommendations")
    public Result<MascotRelatedRecommendationVO> relatedRecommendations(
            @Valid @RequestBody MascotRelatedRecommendationRequest request,
            HttpServletRequest httpServletRequest) {
        AiUserContext user = currentUser(httpServletRequest);
        if (user == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        return Result.success(mascotService.recommendRelatedArticles(user, request));
    }

    /** 读取当前会话已保存的相关帖子结果 */
    @Operation(summary = "读取看板娘相关帖子", description = "返回当前用户指定会话中已保存的相关帖子检索结果")
    @GetMapping("/related-recommendations")
    public Result<List<MascotRelatedRecommendationVO>> relatedRecommendationHistory(
            @RequestParam @Positive Long sessionId,
            HttpServletRequest httpServletRequest) {
        AiUserContext user = currentUser(httpServletRequest);
        if (user == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        return Result.success(mascotService.listRelatedRecommendations(user, sessionId));
    }

    @Operation(summary = "陪伴助手会话列表", description = "按功能 skill 分页返回当前用户会话")
    @GetMapping("/companion/sessions")
    public Result<List<CompanionSessionVO>> companionSessions(
            @RequestParam String skill,
            @RequestParam(defaultValue = "30") int limit,
            HttpServletRequest httpServletRequest) {
        AiUserContext user = currentUser(httpServletRequest);
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
        AiUserContext user = currentUser(httpServletRequest);
        if (user == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        return Result.success(companionMemoryService.listMessages(user.getId(), sessionId));
    }

    /** 读取当前会话的上下文估算占用 */
    @GetMapping("/companion/sessions/{sessionId}/context-window")
    public Result<CompanionContextWindowVO> contextWindow(
            @PathVariable @Positive Long sessionId,
            HttpServletRequest httpServletRequest) {
        AiUserContext user = currentUser(httpServletRequest);
        if (user == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        return Result.success(mascotService.getContextWindow(user, sessionId));
    }

    /** 压缩当前会话的历史上下文 */
    @PostMapping("/companion/sessions/{sessionId}/compress-context")
    public Result<CompanionContextWindowVO> compressContext(
            @PathVariable @Positive Long sessionId,
            HttpServletRequest httpServletRequest) {
        AiUserContext user = currentUser(httpServletRequest);
        if (user == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        return Result.success(mascotService.compressContext(user, sessionId));
    }

    @GetMapping("/companion/memory")
    public Result<MascotMemoryVO> mascotMemory(HttpServletRequest httpServletRequest) {
        AiUserContext user = currentUser(httpServletRequest);
        if (user == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        return Result.success(mascotService.getMascotMemory(user));
    }

    @PostMapping("/companion/memory")
    public Result<MascotMemoryVO> editMascotMemory(
            @Valid @RequestBody MascotMemoryEditRequest request,
            HttpServletRequest httpServletRequest) {
        AiUserContext user = currentUser(httpServletRequest);
        if (user == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        return Result.success(mascotService.editMascotMemory(user, request));
    }

    /** 修改陪伴助手会话名称 */
    @PutMapping("/companion/sessions/{sessionId}")
    public Result<Void> renameCompanionSession(
            @PathVariable @Positive Long sessionId,
            @Valid @RequestBody CompanionSessionRenameRequest request,
            HttpServletRequest httpServletRequest) {
        AiUserContext user = currentUser(httpServletRequest);
        if (user == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        companionMemoryService.renameSession(user.getId(), sessionId, request.getTitle());
        return Result.success();
    }

    /** 删除陪伴助手会话 */
    @DeleteMapping("/companion/sessions/{sessionId}")
    public Result<Void> deleteCompanionSession(
            @PathVariable Long sessionId,
            HttpServletRequest httpServletRequest) {
        AiUserContext user = currentUser(httpServletRequest);
        if (user == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        companionMemoryService.deleteSession(user.getId(), sessionId);
        return Result.success();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (remote != null && !remote.isBlank() && !remote.startsWith("127.") && !"::1".equals(remote)) {
            return remote.trim();
        }
        String forwarded = request.getHeader("X-Real-IP");
        return forwarded == null ? "" : forwarded.trim().split(",")[0].trim();
    }

    private AiUserContext currentUser(HttpServletRequest request) {
        Object session = request.getAttribute(Constant.USER_SESSION);
        if (!(session instanceof org.pluchon.forum.common.security.AuthenticatedUser user)) {
            return null;
        }
        return aiUserLookupService.getById(user.getId());
    }
}
