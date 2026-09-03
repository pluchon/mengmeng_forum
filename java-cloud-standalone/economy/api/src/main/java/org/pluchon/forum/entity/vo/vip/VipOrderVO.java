package org.pluchon.forum.entity.vo.vip;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

// 会员订单响应：下单与轮询查单共用
@Data
public class VipOrderVO {

    private String orderNo;
    private Byte vipTier;
    private String tierLabel;

    // new 新购 / renew 续费 / upgrade 升级
    private String orderKind;

    private String orderKindLabel;
    private BigDecimal amount;
    private String payChannel;

    // 二维码内容或收银台地址
    private String payPayload;

    private Byte paymentState;
    private String paymentStateLabel;

    // 订单超时关闭时间，前端据此收掉二维码
    private Date orderExpireAt;

    // 发货后的会员到期时间，仅支付成功时有值
    private Date vipExpireAt;

    private Date createTime;
}
