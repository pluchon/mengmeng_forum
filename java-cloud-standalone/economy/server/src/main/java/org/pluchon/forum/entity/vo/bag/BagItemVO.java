package org.pluchon.forum.entity.vo.bag;

import lombok.Data;

import java.util.Date;

@Data
public class BagItemVO {

    private Long id;

    private String source;

    private String itemName;

    private String rewardType;

    private Integer rewardValue;

    private Byte vipTier;

    /** 0 未使用 1 已使用 2 待发放 */
    private Integer useStatus;

    private Date useTime;

    private String grantSummary;

    private Date createTime;
}
