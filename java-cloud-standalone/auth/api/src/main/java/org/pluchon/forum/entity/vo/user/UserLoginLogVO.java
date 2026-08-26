package org.pluchon.forum.entity.vo.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户登录日志条目")
public class UserLoginLogVO {

    @Schema(description = "登录时间")
    private String loginTime;

    @Schema(description = "登录方式: 密码/邮箱/短信")
    private String loginTypeLabel;

    @Schema(description = "IP 地址")
    private String ipAddress;

    @Schema(description = "IP 归属地省份")
    private String ipRegion;

    @Schema(description = "设备/浏览器摘要")
    private String deviceSummary;
}
