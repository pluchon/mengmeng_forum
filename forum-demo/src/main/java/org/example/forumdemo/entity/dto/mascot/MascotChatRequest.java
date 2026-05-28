package org.example.forumdemo.entity.dto.mascot;

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

    @Schema(description = "功能：writing | help | reading")
    private String skill;

    @Schema(description = "文本对话后端路由：qwen-flash | qwen-deep | deepseek-flash | deepseek-deep（深度档服务端会对非 VIP 降级；旧 gemini-* 会映射为通义）")
    private String llmProvider;
}
