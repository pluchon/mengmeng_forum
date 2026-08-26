package org.pluchon.forum.entity.vo;

import lombok.Data;

@Data
public class CompanionContextWindowVO {

    private long usedTokens;
    private long maxTokens;
    private boolean canCompress;
}
