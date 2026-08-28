package org.pluchon.forum.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "看板娘对话请求")
public class MascotChatRequest {

    @NotBlank(message = "message 不能为空")
    @Size(max = 2000, message = "message 过长")
    @Schema(description = "用户本轮输入")
    private String message;

    @Schema(description = "前端会话 id, 用于串联多轮")
    private String sessionId;

    @Schema(description = "当前看板娘模型 code（forum_mascot_model.code），供 Python 人设等扩展使用")
    private String mascotModelCode;

    @Schema(description = "已废弃：原 standard/keyboard/gamepad，由 mascotModelCode 替代")
    private String appearance;

    @Schema(description = "前几轮对话, 由前端维护")
    private List<MascotHistoryTurn> history;

    @Schema(description = "功能：chat（对话与帮助，服务端自动路由）| writing | help | drawing")
    private String skill;

    @Schema(description = "为 true 时不写入陪伴助手会话表，仅依赖 history 维持上下文")
    private Boolean ephemeral;

    @Schema(description = "浏览器本地时间 ISO-8601，供看板娘 MCP 感知当前日期时段")
    private String clientDatetime;

    @Schema(description = "会员配额将用尽时主动改用萌萌币扣费（需前端确认后传 true）")
    private Boolean usePointsBilling;

    @Schema(description = "客户端幂等键；流式/重试时传相同值防重复扣费")
    private String clientRequestId;
}
