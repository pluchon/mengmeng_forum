package org.pluchon.forum.entity.dto.vip;

import lombok.Data;

@Data
public class VipSubscribeDTO {
    /** 1=PRO 2=MAX */
    private Byte tier;

    /** 客户端幂等键，重试时必须复用同一值 */
    private String requestId;
}
