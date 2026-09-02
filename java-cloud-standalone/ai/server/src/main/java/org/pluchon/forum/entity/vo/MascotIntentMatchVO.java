package org.pluchon.forum.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Schema(description = "一次牵线邀约（对方身份在双方都点头前不可见）")
@Data
public class MascotIntentMatchVO {

    private Long id;

    @Schema(description = "交集描述；不含任何身份信息")
    private String reason;

    @Schema(description = "我这一侧的态度 PENDING|ACCEPTED|DECLINED")
    private String myState;

    @Schema(description = "PENDING|CONNECTED|CLOSED")
    private String state;

    @Schema(description = "只有 CONNECTED 之后才有值")
    private Long peerUserId;

    @Schema(description = "只有 CONNECTED 之后才有值")
    private String peerNickname;

    private Date createTime;
}
