package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

// VIP 体验卡等活动发放的独立配额礼包
@Data
@TableName("vip_quota_bonus_grant")
public class VipQuotaBonusGrant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String sourceType;
    private String sourceIdempotencyKey;
    private Long qwenGrantedMicros;
    private Long qwenUsedMicros;
    private Long qwenReservedMicros;
    private BigDecimal wanGrantedCredits;
    private BigDecimal wanUsedCredits;
    private BigDecimal wanReservedCredits;
    private Date expireTime;
    private Date createTime;
    private Date updateTime;

    @TableLogic
    private Integer deleteState;
}
