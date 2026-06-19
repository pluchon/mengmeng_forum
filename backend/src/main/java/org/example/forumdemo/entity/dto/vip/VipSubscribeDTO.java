package org.example.forumdemo.entity.dto.vip;

import lombok.Data;

@Data
public class VipSubscribeDTO {
    /** 1=PRO 2=MAX */
    private Byte tier;
}
