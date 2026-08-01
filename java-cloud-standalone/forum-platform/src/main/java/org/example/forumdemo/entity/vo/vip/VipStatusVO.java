package org.example.forumdemo.entity.vo.vip;

import lombok.Data;

import java.util.Date;

@Data
public class VipStatusVO {
    private Byte vipTier;
    private Date vipExpireAt;
    private Integer points;
}
