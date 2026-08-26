package org.pluchon.forum.entity.vo.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "登录日志轻量风险提示")
public class UserLoginRiskHintVO {

    @Schema(description = "是否检测到近期登录风险")
    private Boolean riskDetected;

    @Schema(description = "风险说明，无风险时为空")
    private String hint;
}
