package org.pluchon.forum.entity.vo.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "当前账号安全评估")
public class UserSecurityAssessmentVO {

    @Schema(description = "安全评级")
    private String level;

    @Schema(description = "安全说明")
    private String description;

    @Schema(description = "是否已设置登录密码")
    private Boolean passwordConfigured;

    @Schema(description = "是否已绑定邮箱")
    private Boolean emailBound;

    @Schema(description = "是否已绑定手机号")
    private Boolean phoneBound;

    @Schema(description = "是否检测到近期登录风险")
    private Boolean loginRiskDetected;

    @Schema(description = "登录风险说明")
    private String loginRiskHint;
}
