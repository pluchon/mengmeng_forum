package org.pluchon.forum.entity.dto.vip;

import lombok.Data;

@Data
public class VipCreateOrderDTO {

    // 目标档位 1 PRO 2 MAX
    private Byte tier;

    // 支付渠道，不传按 mock
    private String payChannel;
}
