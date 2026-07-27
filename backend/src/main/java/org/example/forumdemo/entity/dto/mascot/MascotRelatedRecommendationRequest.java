package org.example.forumdemo.entity.dto.mascot;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

// 看板娘确认检索请求
@Data
@Schema(description = "看板娘确认检索相关帖子请求")
public class MascotRelatedRecommendationRequest {

    @NotNull(message = "sessionId 不能为空")
    @Positive(message = "sessionId 非法")
    @Schema(description = "当前陪伴会话 ID")
    private Long sessionId;

    @NotBlank(message = "query 不能为空")
    @Size(max = 500, message = "query 过长")
    @Schema(description = "由看板娘结合上下文生成的检索语句")
    private String query;
}
